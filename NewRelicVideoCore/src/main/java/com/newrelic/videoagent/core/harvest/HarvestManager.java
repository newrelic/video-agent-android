package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.storage.CrashSafeHarvestFactory;
import com.newrelic.videoagent.core.storage.IntegratedDeadLetterHandler;

/**
 * Enhanced HarvestManager with integrated crash-safe storage
 * - Seamless integration with crash detection and recovery
 * - TV-optimized performance with automatic app lifecycle handling
 * - Zero performance impact during normal operation
 */
public class HarvestManager {

    private final EventBufferInterface eventBuffer;
    private final SchedulerInterface scheduler;
    private final HttpClientInterface httpClient;
    private final IntegratedDeadLetterHandler deadLetterHandler;
    private final CrashSafeHarvestFactory factory;
    private volatile boolean schedulerStarted = false;
    private final Object schedulerLock = new Object();

    public HarvestManager(HarvestComponentFactory factory) {
        // Support both regular and crash-safe factories
        if (factory instanceof CrashSafeHarvestFactory) {
            this.factory = (CrashSafeHarvestFactory) factory;
            this.eventBuffer = this.factory.createEventBuffer();
            this.httpClient = this.factory.createHttpClient();
            this.deadLetterHandler = this.factory.createIntegratedDeadLetterHandler(httpClient);
        } else {
            // Fallback to regular factory
            this.factory = null;
            this.eventBuffer = factory.createEventBuffer();
            this.httpClient = factory.createHttpClient();
            this.deadLetterHandler = new IntegratedDeadLetterHandler(
                factory.createDeadLetterQueue(), null, httpClient, factory.getConfiguration());
        }

        this.scheduler = factory.createScheduler(this::harvestRegular, this::harvestLive);

        // Set up overflow callback
        eventBuffer.setOverflowCallback(this::harvestNow);

        // Register app lifecycle handler for crash safety
        registerLifecycleHandler();
    }

    /**
     * Records a custom event with lazy scheduler initialization
     */
    public void recordCustomEvent(String eventType, Map<String, Object> attributes) {
        if (eventType != null && !eventType.trim().isEmpty()) {
            Map<String, Object> event = new HashMap<>(attributes != null ? attributes : new HashMap<>());
            event.put("eventType", eventType);
            event.put("timestamp", System.currentTimeMillis());

            eventBuffer.addEvent(event);
            ensureSchedulerStarted();
        }
    }

    /**
     * CRITICAL: Force harvest all pending events
     * Enhanced with crash-safe emergency backup
     */
    public void forceHarvestAll() {
        try {
            if (schedulerStarted) {
                harvestRegular();
                harvestLive();
                deadLetterHandler.retryFailedEvents();
            }

            // Perform emergency backup if crash-safe factory available
            if (factory != null) {
                factory.performEmergencyBackup();
            }
        } catch (Exception e) {
            System.err.println("[HarvestManager] Force harvest failed: " + e.getMessage());
        }
    }

    /**
     * Generic harvest method for both regular and live events
     * @param batchSizeBytes Maximum batch size in bytes
     * @param priorityFilter Priority level to filter ("normal", "high", etc.)
     * @param harvestType HTTP endpoint type ("regular", "live")
     * @param retryFailures Whether to retry failed events after harvest
     */
    private void harvest(int batchSizeBytes, String priorityFilter, String harvestType, boolean retryFailures) {
        try {
            SizeEstimator sizeEstimator = new DefaultSizeEstimator();
            List<Map<String, Object>> events = eventBuffer.pollBatchByPriority(
                batchSizeBytes,
                sizeEstimator,
                priorityFilter
            );

            if (!events.isEmpty()) {
                boolean success = httpClient.sendEvents(events, harvestType);
                if (!success) {
                    deadLetterHandler.handleFailedEvents(events, harvestType);
                }
            }

            // Retry failed events if requested (typically only for regular harvest)
            if (retryFailures) {
                deadLetterHandler.retryFailedEvents();
            }

        } catch (Exception e) {
            System.err.println("[HarvestManager] " + harvestType + " harvest failed: " + e.getMessage());
        }
    }

    /**
     * Regular harvest task - sends accumulated events with retry logic
     */
    private void harvestRegular() {
        harvest(8192, "ondemand", "regular", true); // 8KB batch, normal priority, with retries
    }

    /**
     * Live harvest task - sends high-priority events immediately
     */
    private void harvestLive() {
        harvest(4096, "live", "live", false); // 4KB batch, high priority, no retries
    }

    /**
     * Emergency harvest callback - harvest immediately when buffer is getting full
     * This is called by the buffer when it detects potential overflow
     */
    private void harvestNow(String bufferType) {
        try {
            if ("live".equals(bufferType)) {
                harvestLive();
            } else {
                harvestRegular();
            }
        } catch (Exception e) {
            System.err.println("[HarvestManager] Emergency harvest failed: " + e.getMessage());
        }
    }

    /**
     * Lazy scheduler initialization - starts only when first event is recorded
     * This saves resources if NRVideo is initialized but never used for tracking
     */
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
     * Register crash detection and app lifecycle handler
     */
    private void registerLifecycleHandler() {
        // Register JVM shutdown hook for crash detection
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                forceHarvestAll();
            } catch (Exception e) {
                // Ignore errors during shutdown
            }
        }));
    }

    /**
     * Get recovery statistics (if using crash-safe factory)
     */
    public String getRecoveryStats() {
        if (factory != null) {
            return factory.getRecoveryStats();
        }
        return "Regular harvest manager (no crash safety)";
    }

    /**
     * Check if currently recovering from crash
     */
    public boolean isRecovering() {
        return factory != null && factory.isRecovering();
    }
}
