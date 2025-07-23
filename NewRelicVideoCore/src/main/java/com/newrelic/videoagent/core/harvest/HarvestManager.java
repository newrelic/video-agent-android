package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import com.newrelic.videoagent.core.storage.CrashSafeHarvestFactory;
import com.newrelic.videoagent.core.storage.IntegratedDeadLetterHandler;
import com.newrelic.videoagent.core.storage.CrashSafeEventBuffer;
import com.newrelic.videoagent.core.lifecycle.NRVideoLifecycleObserver;

/**
 * Enhanced HarvestManager with integrated crash-safe storage
 * - Seamless integration with crash detection and recovery
 * - TV-optimized performance with automatic app lifecycle handling
 * - Zero performance impact during normal operation
 * - Proper 60% capacity threshold scheduler startup
 * - Integrated lifecycle management
 */
public class HarvestManager implements EventBufferInterface.CapacityCallback, NRVideoLifecycleObserver.LifecycleListener {

    private final EventBufferInterface eventBuffer;
    private final SchedulerInterface scheduler;
    private final HttpClientInterface httpClient;
    private final IntegratedDeadLetterHandler deadLetterHandler;
    private final CrashSafeHarvestFactory factory;
    private final NRVideoLifecycleObserver lifecycleObserver;

    public HarvestManager(HarvestComponentFactory factory) {
        // Support both regular and crash-safe factories
        if (factory instanceof CrashSafeHarvestFactory) {
            this.factory = (CrashSafeHarvestFactory) factory;
            this.eventBuffer = this.factory.createEventBuffer();
            this.httpClient = this.factory.createHttpClient();

            // Create IntegratedDeadLetterHandler with proper crash-safe integration
            CrashSafeEventBuffer crashSafeBuffer = (CrashSafeEventBuffer) this.eventBuffer;
            this.deadLetterHandler = new IntegratedDeadLetterHandler(
                this.factory.createDeadLetterQueue(),
                crashSafeBuffer,
                httpClient,
                factory.getConfiguration(),
                factory.getContext()
            );
        } else {
            // Fallback to regular factory
            this.factory = null;
            this.eventBuffer = factory.createEventBuffer();
            this.httpClient = factory.createHttpClient();

            // Create IntegratedDeadLetterHandler without crash-safe buffer
            this.deadLetterHandler = new IntegratedDeadLetterHandler(
                factory.createDeadLetterQueue(),
                null, // No crash-safe buffer for regular factory
                httpClient,
                factory.getConfiguration(),
                factory.getContext()
            );
        }

        this.scheduler = factory.createScheduler(this::harvestRegular, this::harvestLive);

        // Create and integrate lifecycle observer
        this.lifecycleObserver = new NRVideoLifecycleObserver(this, factory.getContext(), factory.getConfiguration());
        this.lifecycleObserver.setHarvestComponents(this, scheduler);

        // Set up event buffer monitoring for 60% threshold scheduler startup
        setupEventBufferMonitoring();

        // Register app lifecycle handler for crash safety
        registerLifecycleHandler();
    }

    /**
     * Get the lifecycle observer for app registration
     */
    public NRVideoLifecycleObserver getLifecycleObserver() {
        return lifecycleObserver;
    }

    // Implement LifecycleListener interface
    @Override
    public void onAppBackgrounded() {
        if (factory != null && factory.getConfiguration().isDebugLoggingEnabled()) {
            System.out.println("[HarvestManager] App backgrounded - immediate harvest triggered");
        }
    }

    @Override
    public void onAppForegrounded() {
        if (factory != null && factory.getConfiguration().isDebugLoggingEnabled()) {
            System.out.println("[HarvestManager] App foregrounded - normal operation resumed");
        }
    }

    @Override
    public void onAppTerminating() {
        if (factory != null && factory.getConfiguration().isDebugLoggingEnabled()) {
            System.out.println("[HarvestManager] App terminating - emergency harvest completed");
        }
    }

    /**
     * Set up event buffer monitoring for 60% capacity threshold
     */
    private void setupEventBufferMonitoring() {
        // Set overflow callback for immediate harvest when buffer is getting full
        eventBuffer.setOverflowCallback(this::harvestNow);

        // Set capacity callback for 60% threshold scheduler startup
        eventBuffer.setCapacityCallback(this);
    }

    /**
     * Implements CapacityCallback - called when buffer reaches 60% capacity
     */
    @Override
    public void onCapacityThresholdReached(double currentCapacity, String bufferType) {
        scheduler.start(bufferType);

        if (factory != null && factory.getConfiguration().isDebugLoggingEnabled()) {
            System.out.println("[HarvestManager] Capacity threshold reached for " + bufferType + ": " +
                String.format(Locale.US, "%.1f%%", currentCapacity * 100) +
                " - Attempting to start scheduler.");
        }
    }

    /**
     * Records a custom event with lazy scheduler initialization
     */
    public void recordCustomEvent(String eventType, Map<String, Object> attributes) {
        if (eventType != null && !eventType.trim().isEmpty()) {
            Map<String, Object> event = new HashMap<>(attributes != null ? attributes : new HashMap<>());
            event.put("eventType", eventType);
            event.put("timestamp", System.currentTimeMillis());

            // Add to event buffer - this will trigger capacity monitoring
            eventBuffer.addEvent(event);
        }
    }

    /**
     * CRITICAL: Force harvest all pending events
     * Enhanced with crash-safe emergency backup
     */
    public void forceHarvestAll() {
        try {
            if (scheduler.isRunning()) {
                harvestRegular();
                harvestLive();
                deadLetterHandler.retryFailedEvents();
            }

            // Perform emergency backup if crash-safe factory available
            if (factory != null) {
                factory.performEmergencyBackup();
            } else if (eventBuffer instanceof CrashSafeEventBuffer) {
                // Emergency backup for crash-safe event buffer
                ((CrashSafeEventBuffer) eventBuffer).emergencyBackup();
            }

            // Emergency backup for dead letter handler
            deadLetterHandler.emergencyBackup();
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
