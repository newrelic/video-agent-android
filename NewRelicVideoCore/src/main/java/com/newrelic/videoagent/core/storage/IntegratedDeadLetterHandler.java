package com.newrelic.videoagent.core.storage;

import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.harvest.DeadLetterEventBuffer;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.DefaultSizeEstimator;
import com.newrelic.videoagent.core.utils.NRLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Integrated dead letter handler with crash-safe backup
 * Optimized for Android TV and Mobile devices using NRVideoConfiguration settings
 */
public class IntegratedDeadLetterHandler {

    private final EventBufferInterface inMemoryQueue;
    private final CrashSafeEventBuffer mainBuffer;
    private final HttpClientInterface httpClient;
    private final NRVideoConfiguration configuration;

    private final int maxRetries;
    private final int regularBatchSizeForRetry;
    private final int liveBatchSizeForRetry;
    private final long retryInterval;
    private final long liveRetryInterval;

    // Add tracking fields for enhanced statistics
    private final AtomicLong totalEventsBackedUp = new AtomicLong(0);

    // Thread safety
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public IntegratedDeadLetterHandler(CrashSafeEventBuffer mainBuffer,
                                     HttpClientInterface httpClient,
                                     NRVideoConfiguration configuration) {
        this.inMemoryQueue = new DeadLetterEventBuffer(configuration.isTV());
        this.mainBuffer = mainBuffer;
        this.httpClient = httpClient;
        this.configuration = configuration;

        // Use configuration values instead of hardcoded numbers
        this.regularBatchSizeForRetry = configuration.getRegularBatchSizeBytes();
        this.liveBatchSizeForRetry = configuration.getLiveBatchSizeBytes();
        this.retryInterval = configuration.getDeadLetterRetryInterval();

        // Calculate live retry interval as a fraction of regular interval
        this.liveRetryInterval = Math.max(retryInterval / 2, 30000L); // Min 30 seconds for live

        if (configuration.isTV()) {
            // TV settings: More aggressive retries, use configuration-based calculations
            this.maxRetries = configuration.isMemoryOptimized() ? 3 : 5;
            // TV can handle more events per batch - updated for ~2KB+ per event
        } else {
            // Mobile settings: Conservative retries based on memory optimization
            this.maxRetries = configuration.isMemoryOptimized() ? 2 : 3;
            // Mobile uses smaller batches - updated for ~2KB+ per event
        }

        NRLog.d("Initialized for " + (configuration.isTV() ? NRVideoConstants.ANDROID_TV : NRVideoConstants.MOBILE) +
            " - MaxRetries: " + maxRetries +
            ", RegularBatchSize: " + regularBatchSizeForRetry +
            ", LiveBatchSize: " + liveBatchSizeForRetry +
            ", RetryInterval: " + retryInterval + "ms" +
            ", LiveRetryInterval: " + liveRetryInterval + "ms");
    }

    /**
     * Handle failed events - retry in memory, backup when exhausted
     * Uses configuration-driven batch sizes and retry limits
     */
    public void handleFailedEvents(List<Map<String, Object>> failedEvents, String bufferType) {
        if (failedEvents == null || failedEvents.isEmpty()) return;

        // Prevent concurrent processing
        if (!isProcessing.compareAndSet(false, true)) {
            NRLog.d("Already retry is processing, queuing for later");
            return;
        }

        try {
            List<Map<String, Object>> toRetry = new ArrayList<>();
            List<Map<String, Object>> toBackup = new ArrayList<>();

            // Separate events based on retry count and configuration-driven limits
            for (Map<String, Object> event : failedEvents) {
                int retryCount = getRetryCount(event);

                if (retryCount < maxRetries && hasMemoryCapacity()) {
                    // Add retry metadata and queue for retry
                    Map<String, Object> retryEvent = addRetryMetadata(event, bufferType, retryCount + 1);
                    toRetry.add(retryEvent);
                } else {
                    // Retries exhausted or memory constrained - backup to SQLite
                    toBackup.add(cleanEvent(event));
                }
            }

            // Queue events for retry with configuration-appropriate limits
            queueRetryEvents(toRetry);

            // Backup exhausted events
            if (!toBackup.isEmpty()) {
                mainBuffer.backupFailedEvents(toBackup);
                totalEventsBackedUp.addAndGet(toBackup.size());

                NRLog.d("Device: " + (configuration.isTV() ? NRVideoConstants.ANDROID_TV : NRVideoConstants.MOBILE) +
                    " - Retrying: " + toRetry.size() +
                    ", Backed up: " + toBackup.size());
            }
        } finally {
            isProcessing.set(false);
        }
    }



    /**
     * Emergency backup of pending retries during app kill
     * Uses configuration-based memory management
     */
    public void emergencyBackup() {
        try {
            // Use configuration-based emergency batch size
            int emergencyBatchSize = configuration.isTV() ?
                configuration.getMaxDeadLetterSize() * 3 : // TV can handle more
                configuration.getMaxDeadLetterSize() * 2;   // Mobile is conservative

            List<Map<String, Object>> pendingEvents = inMemoryQueue.pollBatchByPriority(
                emergencyBatchSize, null, NRVideoConstants.EVENT_TYPE_ONDEMAND);

            if (!pendingEvents.isEmpty()) {
                List<Map<String, Object>> cleanEvents = new ArrayList<>();
                for (Map<String, Object> event : pendingEvents) {
                    cleanEvents.add(extractOriginalEvent(event));
                }
                mainBuffer.backupFailedEvents(cleanEvents);
                totalEventsBackedUp.addAndGet(cleanEvents.size());

                NRLog.d("Emergency backup (" + (configuration.isTV() ? NRVideoConstants.ANDROID_TV : NRVideoConstants.MOBILE) +
                    "): " + cleanEvents.size() + " events");
            }
        } catch (Exception e) {
            NRLog.e("Emergency backup failed: " + e.getMessage(), e);
        }
    }



    private void queueRetryEvents(List<Map<String, Object>> toRetry) {
        for (Map<String, Object> event : toRetry) {
            // Check memory constraints before adding
            if (inMemoryQueue.getEventCount() >= configuration.getMaxDeadLetterSize()) {
                // Make room by removing oldest events - use configuration-based approach
                int eventsToRemove = Math.max(configuration.getMaxDeadLetterSize() / 20, 1); // Remove 5% at minimum
                if (configuration.isTV()) {
                    eventsToRemove *= 2; // TV can afford to remove more
                }

                for (int i = 0; i < eventsToRemove; i++) {
                    List<Map<String, Object>> removed = inMemoryQueue.pollBatchByPriority(
                        1, new DefaultSizeEstimator(), NRVideoConstants.EVENT_TYPE_ONDEMAND);
                    if (removed.isEmpty()) break;
                }
            }
            inMemoryQueue.addEvent(event);
        }
    }

    private boolean hasMemoryCapacity() {
        // Configuration-driven memory capacity check
        int currentSize = inMemoryQueue.getEventCount();
        int maxSize = configuration.getMaxDeadLetterSize();

        if (configuration.isTV() && !configuration.isMemoryOptimized()) {
            // TV with plenty of memory, allow 90% usage
            return currentSize < (maxSize * 0.9);
        } else if (!configuration.isMemoryOptimized()) {
            // Non-memory-optimized mobile, allow 80% usage
            return currentSize < (maxSize * 0.8);
        } else {
            // Memory-optimized device, be conservative at 65% usage
            return currentSize < (maxSize * 0.65);
        }
    }

    private int getRetryCount(Map<String, Object> event) {
        Object metadata = event.get("retryMetadata");
        if (metadata instanceof Map) {
            Object count = ((Map<?, ?>) metadata).get("retryCount");
            return count instanceof Integer ? (Integer) count : 0;
        }
        return 0;
    }

    private Map<String, Object> addRetryMetadata(Map<String, Object> event, String bufferType, int retryCount) {
        Map<String, Object> retryEvent = new HashMap<>(event);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("retryCount", retryCount);
        metadata.put("category", bufferType);
        metadata.put("deviceType", configuration.isTV() ? NRVideoConstants.ANDROID_TV : NRVideoConstants.MOBILE);
        metadata.put("timestamp", System.currentTimeMillis());
        retryEvent.put("retryMetadata", metadata);
        return retryEvent;
    }

    private Map<String, Object> cleanEvent(Map<String, Object> event) {
        Map<String, Object> clean = new HashMap<>(event);
        clean.remove("retryMetadata");
        return clean;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractOriginalEvent(Map<String, Object> retryEvent) {
        Object original = retryEvent.get("originalEvent");
        if (original instanceof Map) {
            return (Map<String, Object>) original;
        }
        return cleanEvent(retryEvent);
    }
}
