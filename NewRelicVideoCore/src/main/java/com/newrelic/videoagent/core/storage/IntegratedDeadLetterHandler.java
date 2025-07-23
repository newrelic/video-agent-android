package com.newrelic.videoagent.core.storage;

import android.util.Log;
import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.DefaultSizeEstimator;

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

    private static final String TAG = "NRVideo.DeadLetter";

    private final EventBufferInterface inMemoryQueue;
    private final CrashSafeEventBuffer mainBuffer;
    private final HttpClientInterface httpClient;
    private final NRVideoConfiguration configuration;

    // Configuration-driven settings (no more hardcoded values)
    private final boolean isAndroidTV;
    private final int maxRetries;
    private final int regularBatchSizeForRetry;
    private final int liveBatchSizeForRetry;
    private final long retryInterval;
    private final long liveRetryInterval;
    private final int maxEventsPerBatch;

    // Add tracking fields for enhanced statistics
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong totalEventsRetried = new AtomicLong(0);
    private final AtomicLong totalEventsBackedUp = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong lastProcessingTime = new AtomicLong(0);
    private volatile long uptime = System.currentTimeMillis();

    // Thread safety
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public IntegratedDeadLetterHandler(EventBufferInterface deadLetterQueue,
                                     CrashSafeEventBuffer mainBuffer,
                                     HttpClientInterface httpClient,
                                     NRVideoConfiguration configuration) {
        this.inMemoryQueue = deadLetterQueue;
        this.mainBuffer = mainBuffer;
        this.httpClient = httpClient;
        this.configuration = configuration;

        // Use the already detected device type from configuration
        this.isAndroidTV = configuration.isTV();

        // Use configuration values instead of hardcoded numbers
        this.regularBatchSizeForRetry = configuration.getRegularBatchSizeBytes();
        this.liveBatchSizeForRetry = configuration.getLiveBatchSizeBytes();
        this.retryInterval = configuration.getDeadLetterRetryInterval();

        // Calculate live retry interval as a fraction of regular interval
        this.liveRetryInterval = Math.max(retryInterval / 2, 30000L); // Min 30 seconds for live

        if (isAndroidTV) {
            // TV settings: More aggressive retries, use configuration-based calculations
            this.maxRetries = configuration.isMemoryOptimized() ? 3 : 5;
            // TV can handle more events per batch - updated for ~2KB+ per event
            this.maxEventsPerBatch = Math.max(regularBatchSizeForRetry / 2048, 10); // ~2KB per event, min 10 events
        } else {
            // Mobile settings: Conservative retries based on memory optimization
            this.maxRetries = configuration.isMemoryOptimized() ? 2 : 3;
            // Mobile uses smaller batches - updated for ~2KB+ per event
            this.maxEventsPerBatch = Math.max(regularBatchSizeForRetry / 3072, 5); // ~3KB per event (conservative), min 5 events
        }

        if (configuration.isDebugLoggingEnabled()) {
            Log.d(TAG, "Initialized for " + (isAndroidTV ? NRVideoConstants.ANDROID_TV : NRVideoConstants.MOBILE) +
                " - MaxRetries: " + maxRetries +
                ", RegularBatchSize: " + regularBatchSizeForRetry +
                ", LiveBatchSize: " + liveBatchSizeForRetry +
                ", RetryInterval: " + retryInterval + "ms" +
                ", LiveRetryInterval: " + liveRetryInterval + "ms");
        }
    }

    /**
     * Handle failed events - retry in memory, backup when exhausted
     * Uses configuration-driven batch sizes and retry limits
     */
    public void handleFailedEvents(List<Map<String, Object>> failedEvents, String category) {
        if (failedEvents == null || failedEvents.isEmpty()) return;

        // Prevent concurrent processing
        if (!isProcessing.compareAndSet(false, true)) {
            if (configuration.isDebugLoggingEnabled()) {
                Log.d(TAG, "Already processing, queuing for later");
            }
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
                    Map<String, Object> retryEvent = addRetryMetadata(event, category, retryCount + 1);
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

                if (configuration.isDebugLoggingEnabled()) {
                    Log.d(TAG, "Device: " + (isAndroidTV ? NRVideoConstants.ANDROID_TV : NRVideoConstants.MOBILE) +
                        " - Retrying: " + toRetry.size() +
                        ", Backed up: " + toBackup.size());
                }
            }
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Retry failed events from in-memory queue with configuration-driven batching
     */
    public void retryFailedEvents() {
        retryFailedEvents(NRVideoConstants.EVENT_TYPE_ONDEMAND);
    }

    /**
     * Retry failed events with category-specific batch sizing
     */
    public void retryFailedEvents(String eventType) {
        if (!isProcessing.compareAndSet(false, true)) {
            return; // Already processing
        }

        try {
            // Use appropriate batch size based on event type and configuration
            int batchSize = NRVideoConstants.EVENT_TYPE_LIVE.equals(eventType) ? liveBatchSizeForRetry : regularBatchSizeForRetry;

            List<Map<String, Object>> events = inMemoryQueue.pollBatchByPriority(
                batchSize, new DefaultSizeEstimator(), NRVideoConstants.CATEGORY_NORMAL);

            if (events.isEmpty()) return;

            // Limit batch size based on configuration-driven limits
            if (events.size() > maxEventsPerBatch) {
                List<Map<String, Object>> excess = events.subList(maxEventsPerBatch, events.size());
                events = events.subList(0, maxEventsPerBatch);

                // Re-queue excess events
                for (Map<String, Object> event : excess) {
                    inMemoryQueue.addEvent(event);
                }
            }

            // Group by category for efficient processing
            Map<String, List<Map<String, Object>>> grouped = groupByCategory(events);

            for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
                retryBatch(entry.getValue(), entry.getKey(), eventType);
            }

        } catch (Exception e) {
            Log.e(TAG, "Retry failed: " + e.getMessage(), e);
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
            int emergencyBatchSize = isAndroidTV ?
                configuration.getMaxDeadLetterSize() * 3 : // TV can handle more
                configuration.getMaxDeadLetterSize() * 2;   // Mobile is conservative

            List<Map<String, Object>> pendingEvents = inMemoryQueue.pollBatchByPriority(
                emergencyBatchSize, null, NRVideoConstants.CATEGORY_NORMAL);

            if (!pendingEvents.isEmpty()) {
                List<Map<String, Object>> cleanEvents = new ArrayList<>();
                for (Map<String, Object> event : pendingEvents) {
                    cleanEvents.add(extractOriginalEvent(event));
                }
                mainBuffer.backupFailedEvents(cleanEvents);
                totalEventsBackedUp.addAndGet(cleanEvents.size());

                if (configuration.isDebugLoggingEnabled()) {
                    Log.d(TAG, "Emergency backup (" + (isAndroidTV ? NRVideoConstants.ANDROID_TV : NRVideoConstants.MOBILE) +
                        "): " + cleanEvents.size() + " events");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Emergency backup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get next retry delay based on configuration and event type
     */
    public long getRetryDelay(String eventType, int retryCount) {
        long baseInterval = NRVideoConstants.EVENT_TYPE_LIVE.equals(eventType) ? liveRetryInterval : retryInterval;

        // Exponential backoff with jitter
        long delay = baseInterval * (1L << Math.min(retryCount - 1, 4)); // Cap at 2^4 = 16x

        // Add jitter (10-20% random variation)
        double jitter = 0.1 + (Math.random() * 0.1);
        delay = (long) (delay * (1 + jitter));

        // Cap maximum delay
        long maxDelay = isAndroidTV ?
            retryInterval * 10 : // TV can wait longer
            retryInterval * 5;   // Mobile needs faster recovery

        return Math.min(delay, maxDelay);
    }

    /**
     * Get comprehensive statistics about dead letter handler performance
     * Now includes both configuration and operational metrics
     */
    public Map<String, Object> getStatistics() {
        long currentTime = System.currentTimeMillis();
        long uptimeMs = currentTime - uptime;

        Map<String, Object> stats = new HashMap<>();

        // Device and Configuration Stats
        stats.put("deviceType", isAndroidTV ? NRVideoConstants.ANDROID_TV : NRVideoConstants.MOBILE);

        // Configuration Settings
        stats.put("maxRetries", maxRetries);
        stats.put("regularBatchSize", regularBatchSizeForRetry);
        stats.put("liveBatchSize", liveBatchSizeForRetry);
        stats.put("retryInterval", retryInterval);
        stats.put("liveRetryInterval", liveRetryInterval);
        stats.put("maxEventsPerBatch", maxEventsPerBatch);
        stats.put("maxDeadLetterSize", configuration.getMaxDeadLetterSize());

        // Current State
        stats.put("queueSize", inMemoryQueue.getEventCount());
        stats.put("isProcessing", isProcessing.get());
        stats.put("queueUtilization", calculateQueueUtilization());

        // Operational Metrics
        stats.put("totalEventsProcessed", totalEventsProcessed.get());
        stats.put("totalEventsRetried", totalEventsRetried.get());
        stats.put("totalEventsBackedUp", totalEventsBackedUp.get());
        stats.put("totalFailures", totalFailures.get());

        // Performance Metrics
        stats.put("uptimeMs", uptimeMs);
        stats.put("lastProcessingTime", lastProcessingTime.get());
        stats.put("timeSinceLastProcessing", lastProcessingTime.get() > 0 ?
            currentTime - lastProcessingTime.get() : -1);

        // Success Rates (calculated metrics)
        long totalProcessed = totalEventsProcessed.get();
        if (totalProcessed > 0) {
            stats.put("retryRate", (double) totalEventsRetried.get() / totalProcessed);
            stats.put("backupRate", (double) totalEventsBackedUp.get() / totalProcessed);
            stats.put("failureRate", (double) totalFailures.get() / totalProcessed);
            stats.put("throughputPerHour", uptimeMs > 0 ?
                (totalProcessed * 3600000.0) / uptimeMs : 0.0);
        } else {
            stats.put("retryRate", 0.0);
            stats.put("backupRate", 0.0);
            stats.put("failureRate", 0.0);
            stats.put("throughputPerHour", 0.0);
        }

        // Health Status
        stats.put("healthStatus", calculateHealthStatus());

        return stats;
    }

    /**
     * Calculate queue utilization percentage
     */
    private double calculateQueueUtilization() {
        int currentSize = inMemoryQueue.getEventCount();
        int maxSize = configuration.getMaxDeadLetterSize();
        return maxSize > 0 ? (double) currentSize / maxSize : 0.0;
    }

    /**
     * Calculate overall health status of the dead letter handler
     */
    private String calculateHealthStatus() {
        double queueUtil = calculateQueueUtilization();
        long totalProcessed = totalEventsProcessed.get();

        if (totalProcessed == 0) {
            return NRVideoConstants.HEALTH_IDLE;
        }

        double failureRate = totalProcessed > 0 ?
            (double) totalFailures.get() / totalProcessed : 0.0;

        if (queueUtil > 0.9) {
            return NRVideoConstants.HEALTH_CRITICAL; // Queue nearly full
        } else if (failureRate > 0.5) {
            return NRVideoConstants.HEALTH_DEGRADED; // High failure rate
        } else if (queueUtil > 0.7 || failureRate > 0.2) {
            return NRVideoConstants.HEALTH_WARNING; // Moderate issues
        } else {
            return NRVideoConstants.HEALTH_HEALTHY;
        }
    }

    // Helper methods
    private void retryBatch(List<Map<String, Object>> events, String category, String eventType) {
        List<Map<String, Object>> cleanEvents = new ArrayList<>();
        for (Map<String, Object> event : events) {
            cleanEvents.add(extractOriginalEvent(event));
        }

        // Update processing metrics
        totalEventsProcessed.addAndGet(cleanEvents.size());
        lastProcessingTime.set(System.currentTimeMillis());

        try {
            boolean success = httpClient.sendEvents(cleanEvents, category);
            if (!success) {
                // Failed again - re-queue with incremented retry count
                totalFailures.incrementAndGet();
                totalEventsRetried.addAndGet(cleanEvents.size());
                handleFailedEvents(cleanEvents, category);
            } else if (configuration.isDebugLoggingEnabled()) {
                Log.d(TAG, "Successfully retried " + cleanEvents.size() + " " + eventType +
                    " events for category: " + category);
            }
        } catch (Exception e) {
            totalFailures.incrementAndGet();
            if (configuration.isDebugLoggingEnabled()) {
                Log.w(TAG, "Retry batch failed: " + e.getMessage(), e);
            }
            handleFailedEvents(cleanEvents, category);
        }
    }

    private void queueRetryEvents(List<Map<String, Object>> toRetry) {
        for (Map<String, Object> event : toRetry) {
            // Check memory constraints before adding
            if (inMemoryQueue.getEventCount() >= configuration.getMaxDeadLetterSize()) {
                // Make room by removing oldest events - use configuration-based approach
                int eventsToRemove = Math.max(configuration.getMaxDeadLetterSize() / 20, 1); // Remove 5% at minimum
                if (isAndroidTV) {
                    eventsToRemove *= 2; // TV can afford to remove more
                }

                for (int i = 0; i < eventsToRemove; i++) {
                    List<Map<String, Object>> removed = inMemoryQueue.pollBatchByPriority(
                        1, new DefaultSizeEstimator(), NRVideoConstants.CATEGORY_NORMAL);
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

        if (isAndroidTV && !configuration.isMemoryOptimized()) {
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

    private Map<String, Object> addRetryMetadata(Map<String, Object> event, String category, int retryCount) {
        Map<String, Object> retryEvent = new HashMap<>(event);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("retryCount", retryCount);
        metadata.put("category", category);
        metadata.put("deviceType", isAndroidTV ? NRVideoConstants.ANDROID_TV : NRVideoConstants.MOBILE);
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

    private Map<String, List<Map<String, Object>>> groupByCategory(List<Map<String, Object>> events) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        for (Map<String, Object> event : events) {
            String category = extractCategory(event);
            List<Map<String, Object>> categoryList = grouped.get(category);
            if (categoryList == null) {
                categoryList = new ArrayList<>();
                grouped.put(category, categoryList);
            }
            categoryList.add(event);
        }
        return grouped;
    }

    private String extractCategory(Map<String, Object> event) {
        Object metadata = event.get("retryMetadata");
        if (metadata instanceof Map) {
            Object category = ((Map<?, ?>) metadata).get("category");
            return category instanceof String ? (String) category : NRVideoConstants.CATEGORY_DEFAULT;
        }
        return NRVideoConstants.CATEGORY_DEFAULT;
    }
}
