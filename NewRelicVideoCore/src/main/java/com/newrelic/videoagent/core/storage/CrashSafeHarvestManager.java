package com.newrelic.videoagent.core.storage;

import android.content.Context;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.harvest.HarvestComponentFactory;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;
import com.newrelic.videoagent.core.harvest.SchedulerInterface;
import com.newrelic.videoagent.core.harvest.MultiTaskHarvestScheduler;
import com.newrelic.videoagent.core.harvest.OptimizedHttpClient;
import com.newrelic.videoagent.core.harvest.PriorityEventBuffer;

import java.util.List;
import java.util.Map;

/**
 * Crash-safe harvest manager with integrated storage
 * Handles all crash scenarios and app lifecycle events automatically
 */
public class CrashSafeHarvestManager {

    private final CrashSafeEventBuffer eventBuffer;
    private final IntegratedDeadLetterHandler deadLetterHandler;
    private final HttpClientInterface httpClient;
    private final SchedulerInterface scheduler;
    private final NRVideoConfiguration configuration;

    private volatile boolean schedulerStarted = false;
    private final Object schedulerLock = new Object();

    public CrashSafeHarvestManager(Context context, NRVideoConfiguration configuration) {
        this.configuration = configuration;

        // Create crash-safe components
        this.eventBuffer = new CrashSafeEventBuffer(context);
        this.httpClient = new OptimizedHttpClient(configuration, context);

        // Create dead letter handler with crash-safe integration
        EventBufferInterface deadLetterQueue = new PriorityEventBuffer();
        this.deadLetterHandler = new IntegratedDeadLetterHandler(
            deadLetterQueue, eventBuffer, httpClient, configuration);

        // Create scheduler with lifecycle awareness
        this.scheduler = new CrashAwareScheduler(
            context, this::harvestRegular, this::harvestLive,
            configuration.getHarvestCycleSeconds(),
            configuration.getLiveHarvestCycleSeconds());

        // Set up overflow callback
        eventBuffer.setOverflowCallback(this::harvestNow);

        // Register crash handler
        registerCrashHandler();
    }

    /**
     * Record custom event with lazy scheduler initialization
     */
    public void recordCustomEvent(String eventType, Map<String, Object> attributes) {
        if (eventType != null && !eventType.trim().isEmpty()) {
            Map<String, Object> event = new java.util.HashMap<>(attributes != null ? attributes : new java.util.HashMap<>());
            event.put("eventType", eventType);
            event.put("timestamp", System.currentTimeMillis());

            eventBuffer.addEvent(event);
            ensureSchedulerStarted();
        }
    }

    /**
     * Force harvest all pending events (app backgrounding)
     */
    public void forceHarvestAll() {
        try {
            if (schedulerStarted) {
                harvestRegular();
                harvestLive();
                deadLetterHandler.retryFailedEvents();
            }
        } catch (Exception e) {
            System.err.println("[CrashSafeHarvest] Force harvest failed: " + e.getMessage());
        }
    }

    /**
     * CRITICAL: Emergency backup for app kill/crash
     */
    public void emergencyShutdown() {
        try {
            // Emergency backup all pending events
            eventBuffer.emergencyBackup();
            deadLetterHandler.emergencyBackup();

            // Cleanup
            eventBuffer.cleanup();
        } catch (Exception e) {
            System.err.println("[CrashSafeHarvest] Emergency shutdown failed: " + e.getMessage());
        }
    }

    /**
     * Start the harvest manager
     */
    public void start() {
        ensureSchedulerStarted();
    }

    /**
     * Stop the harvest manager
     */
    public void stop() {
        scheduler.shutdown();
        schedulerStarted = false;
    }

    // Private harvest methods
    private void harvestRegular() {
        harvest(8192, "ondemand", "regular", true);
    }

    private void harvestLive() {
        harvest(4096, "live", "live", false);
    }

    private void harvestNow(String bufferType) {
        try {
            if ("live".equals(bufferType)) {
                harvestLive();
            } else {
                harvestRegular();
            }
        } catch (Exception e) {
            System.err.println("[CrashSafeHarvest] Emergency harvest failed: " + e.getMessage());
        }
    }

    private void harvest(int batchSizeBytes, String priority, String harvestType, boolean retryFailures) {
        try {
            List<Map<String, Object>> events = eventBuffer.pollBatchByPriority(
                batchSizeBytes, new com.newrelic.videoagent.core.harvest.DefaultSizeEstimator(), priority);

            if (!events.isEmpty()) {
                boolean success = httpClient.sendEvents(events, harvestType);
                if (!success) {
                    deadLetterHandler.handleFailedEvents(events, harvestType);
                }
            }

            if (retryFailures) {
                deadLetterHandler.retryFailedEvents();
            }

        } catch (Exception e) {
            System.err.println("[CrashSafeHarvest] " + harvestType + " harvest failed: " + e.getMessage());
        }
    }

    private void ensureSchedulerStarted() {
        if (!schedulerStarted) {
            synchronized (schedulerLock) {
                if (!schedulerStarted) {
                    scheduler.start();
                    schedulerStarted = true;
                }
            }
        }
    }

    /**
     * Register JVM shutdown hook for crash detection
     */
    private void registerCrashHandler() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                emergencyShutdown();
            } catch (Exception e) {
                // Ignore errors during shutdown
            }
        }));
    }

    /**
     * Get recovery statistics
     */
    public String getStats() {
        CrashSafeEventBuffer.RecoveryStats stats = eventBuffer.getRecoveryStats();
        return "CrashSafeHarvest: " + stats.toString();
    }

    /**
     * Crash-aware scheduler with app lifecycle integration
     */
    private static class CrashAwareScheduler extends MultiTaskHarvestScheduler {
        private final Context context;
        private final CrashSafeHarvestManager harvestManager;

        CrashAwareScheduler(Context context, Runnable regularTask, Runnable liveTask,
                           int regularInterval, int liveInterval) {
            super(regularTask, liveTask, regularInterval, liveInterval);
            this.context = context;
            this.harvestManager = null; // Will be set externally if needed
        }

        @Override
        public void onAppBackgrounded() {
            super.onAppBackgrounded();
            // Force harvest before going to background
            if (harvestManager != null) {
                harvestManager.forceHarvestAll();
            }
        }

        @Override
        public void onAppForegrounded() {
            super.onAppForegrounded();
            // Resume normal operation - recovery happens automatically in buffer
        }

        @Override
        public void shutdown() {
            // Emergency backup before shutdown
            if (harvestManager != null) {
                harvestManager.emergencyShutdown();
            }
            super.shutdown();
        }
    }
}
