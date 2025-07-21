package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.newrelic.videoagent.core.NRVideoConfiguration;

/**
 * Clean HarvestManager implementation with simple overflow prevention
 * - Dependency Injection instead of Singleton anti-pattern
 * - Factory Pattern for component creation
 * - Lazy scheduler initialization for better performance
 * - Simple overflow detection triggers immediate harvest
 * - Optimized for mobile/TV environments
 */
public class HarvestManager {

    private final EventBufferInterface eventBuffer;
    private final SchedulerInterface scheduler;
    private final HttpClientInterface httpClient;
    private final DeadLetterHandler deadLetterHandler;
    private volatile boolean schedulerStarted = false;
    private final Object schedulerLock = new Object();

    public HarvestManager(HarvestComponentFactory factory) {
        this.eventBuffer = factory.createEventBuffer();
        this.httpClient = factory.createHttpClient();
        this.deadLetterHandler = new DeadLetterHandler(factory.createDeadLetterQueue(), httpClient, factory.getConfiguration());
        this.scheduler = factory.createScheduler(this::harvestRegular, this::harvestLive);

        // Set up overflow callback using interface method - no instanceof check needed!
        eventBuffer.setOverflowCallback(this::harvestNow);
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

            // LAZY LOADING: Start scheduler only when first event arrives
            ensureSchedulerStarted();
        }
    }

    /**
     * Lazy initialization of scheduler - starts only when first event is recorded
     * This saves resources if NRVideo is initialized but never used for tracking
     */
    private void ensureSchedulerStarted() {
        if (!schedulerStarted) {
            synchronized (schedulerLock) {
                if (!schedulerStarted) {
                    scheduler.start();
                    schedulerStarted = true;
                    // Optional debug logging
                    System.out.println("[HarvestManager] Scheduler started lazily on first event");
                }
            }
        }
    }

    /**
     * CRITICAL: Force immediate harvest of all pending events
     * Called during app backgrounding/closing to prevent data loss
     * Note: Scheduler shutdown is handled by lifecycle manager, not here
     */
    public void forceHarvestAll() {
        try {
            // Only harvest if scheduler was actually started (events exist)
            if (schedulerStarted) {
                // Harvest both regular and live events immediately
                harvestRegular();
                harvestLive();

                // Also retry any failed events in dead letter queue
                deadLetterHandler.retryFailedEvents();
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
        harvest(8192, "normal", "regular", true); // 8KB batch, normal priority, with retries
    }

    /**
     * Live harvest task - sends high-priority events immediately
     */
    private void harvestLive() {
        harvest(4096, "high", "live", false); // 4KB batch, high priority, no retries
    }

    /**
     * Simple overflow callback - harvest immediately when buffer is getting full
     * This is called by the buffer when it detects potential overflow
     */
    private void harvestNow(String bufferType) {
        try {
            if ("live".equals(bufferType)) {
                harvestLive();
                System.out.println("[HarvestManager] Emergency live harvest completed");
            } else {
                harvestRegular();
                System.out.println("[HarvestManager] Emergency regular harvest completed");
            }
        } catch (Exception e) {
            System.err.println("[HarvestManager] Emergency harvest failed: " + e.getMessage());
        }
    }
}
