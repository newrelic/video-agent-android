package com.newrelic.videoagent.core.storage;

import android.content.Context;
import android.content.pm.PackageManager;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.DefaultSizeEstimator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Integrated dead letter handler with crash-safe backup
 * Optimized for Android TV and Mobile devices with device-specific settings
 */
public class IntegratedDeadLetterHandler {

    private final EventBufferInterface inMemoryQueue;
    private final CrashSafeEventBuffer mainBuffer;
    private final HttpClientInterface httpClient;
    private final NRVideoConfiguration configuration;

    // Device-specific settings
    private final boolean isAndroidTV;
    private final int maxRetries;
    private final int batchSizeForRetry;
    private final int maxEventsPerBatch;

    // Thread safety
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public IntegratedDeadLetterHandler(EventBufferInterface deadLetterQueue,
                                     CrashSafeEventBuffer mainBuffer,
                                     HttpClientInterface httpClient,
                                     NRVideoConfiguration configuration,
                                     Context context) {
        this.inMemoryQueue = deadLetterQueue;
        this.mainBuffer = mainBuffer;
        this.httpClient = httpClient;
        this.configuration = configuration;

        // Detect device type and set optimized parameters
        this.isAndroidTV = detectAndroidTV(context);

        if (isAndroidTV) {
            // TV settings: More aggressive retries and larger batches
            this.maxRetries = 5;
            this.batchSizeForRetry = 16384; // 16KB
            this.maxEventsPerBatch = 100;
        } else {
            // Mobile settings: Conservative retries and smaller batches
            this.maxRetries = 3;
            this.batchSizeForRetry = 4096; // 4KB
            this.maxEventsPerBatch = 50;
        }

        if (configuration.isDebugLoggingEnabled()) {
            System.out.println("[IntegratedDeadLetter] Initialized for " +
                (isAndroidTV ? "Android TV" : "Mobile") +
                " - MaxRetries: " + maxRetries +
                ", BatchSize: " + batchSizeForRetry);
        }
    }

    /**
     * Handle failed events - retry in memory, backup when exhausted
     * Optimized for device type with appropriate batch sizes and retry limits
     */
    public void handleFailedEvents(List<Map<String, Object>> failedEvents, String category) {
        if (failedEvents == null || failedEvents.isEmpty()) return;

        // Prevent concurrent processing
        if (!isProcessing.compareAndSet(false, true)) {
            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[IntegratedDeadLetter] Already processing, queuing for later");
            }
            return;
        }

        try {
            List<Map<String, Object>> toRetry = new ArrayList<>();
            List<Map<String, Object>> toBackup = new ArrayList<>();

            // Separate events based on retry count and device capabilities
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

            // Queue events for retry with device-appropriate limits
            queueRetryEvents(toRetry);

            // Backup exhausted events
            if (!toBackup.isEmpty()) {
                mainBuffer.backupFailedEvents(toBackup);

                if (configuration.isDebugLoggingEnabled()) {
                    System.out.println("[IntegratedDeadLetter] Device: " +
                        (isAndroidTV ? "TV" : "Mobile") +
                        " - Retrying: " + toRetry.size() +
                        ", Backed up: " + toBackup.size());
                }
            }
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Retry failed events from in-memory queue with device-optimized batching
     */
    public void retryFailedEvents() {
        if (!isProcessing.compareAndSet(false, true)) {
            return; // Already processing
        }

        try {
            List<Map<String, Object>> events = inMemoryQueue.pollBatchByPriority(
                batchSizeForRetry, new DefaultSizeEstimator(), "normal");

            if (events.isEmpty()) return;

            // Limit batch size based on device capabilities
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
                retryBatch(entry.getValue(), entry.getKey());
            }

        } catch (Exception e) {
            System.err.println("[IntegratedDeadLetter] Retry failed: " + e.getMessage());
            if (configuration.isDebugLoggingEnabled()) {
                e.printStackTrace();
            }
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Emergency backup of pending retries during app kill
     * Enhanced with device-specific memory management
     */
    public void emergencyBackup() {
        try {
            // Use device-appropriate batch size for emergency backup
            int emergencyBatchSize = isAndroidTV ? Integer.MAX_VALUE :
                configuration.getMaxDeadLetterSize() * 2;

            List<Map<String, Object>> pendingEvents = inMemoryQueue.pollBatchByPriority(
                emergencyBatchSize, null, "normal");

            if (!pendingEvents.isEmpty()) {
                List<Map<String, Object>> cleanEvents = new ArrayList<>();
                for (Map<String, Object> event : pendingEvents) {
                    cleanEvents.add(extractOriginalEvent(event));
                }
                mainBuffer.backupFailedEvents(cleanEvents);

                if (configuration.isDebugLoggingEnabled()) {
                    System.out.println("[IntegratedDeadLetter] Emergency backup (" +
                        (isAndroidTV ? "TV" : "Mobile") +
                        "): " + cleanEvents.size() + " events");
                }
            }
        } catch (Exception e) {
            System.err.println("[IntegratedDeadLetter] Emergency backup failed: " + e.getMessage());
        }
    }

    /**
     * Get statistics about dead letter handler performance
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("deviceType", isAndroidTV ? "AndroidTV" : "Mobile");
        stats.put("maxRetries", maxRetries);
        stats.put("batchSize", batchSizeForRetry);
        stats.put("queueSize", inMemoryQueue.getEventCount());
        stats.put("isProcessing", isProcessing.get());
        return stats;
    }

    // Helper methods
    private void retryBatch(List<Map<String, Object>> events, String category) {
        List<Map<String, Object>> cleanEvents = new ArrayList<>();
        for (Map<String, Object> event : events) {
            cleanEvents.add(extractOriginalEvent(event));
        }

        try {
            boolean success = httpClient.sendEvents(cleanEvents, category);
            if (!success) {
                // Failed again - re-queue with incremented retry count
                handleFailedEvents(cleanEvents, category);
            } else if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[IntegratedDeadLetter] Successfully retried " +
                    cleanEvents.size() + " events for category: " + category);
            }
        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                System.err.println("[IntegratedDeadLetter] Retry batch failed: " + e.getMessage());
            }
            handleFailedEvents(cleanEvents, category);
        }
    }

    private void queueRetryEvents(List<Map<String, Object>> toRetry) {
        for (Map<String, Object> event : toRetry) {
            // Check memory constraints before adding
            if (inMemoryQueue.getEventCount() >= configuration.getMaxDeadLetterSize()) {
                // Make room by removing oldest events
                int eventsToRemove = isAndroidTV ? 10 : 5; // TV can afford to remove more
                for (int i = 0; i < eventsToRemove; i++) {
                    List<Map<String, Object>> removed = inMemoryQueue.pollBatchByPriority(
                        1, new DefaultSizeEstimator(), "normal");
                    if (removed.isEmpty()) break;
                }
            }
            inMemoryQueue.addEvent(event);
        }
    }

    private boolean hasMemoryCapacity() {
        // Device-specific memory capacity check
        int currentSize = inMemoryQueue.getEventCount();
        int maxSize = configuration.getMaxDeadLetterSize();

        if (isAndroidTV) {
            // TV has more memory, allow 90% usage
            return currentSize < (maxSize * 0.9);
        } else {
            // Mobile is more conservative, allow 75% usage
            return currentSize < (maxSize * 0.75);
        }
    }

    private boolean detectAndroidTV(Context context) {
        if (context == null) return false;

        try {
            PackageManager pm = context.getPackageManager();

            // Primary detection: Android TV Leanback UI
            if (pm.hasSystemFeature("android.software.leanback")) {
                return true;
            }

            // Secondary detection: Television feature
            if (pm.hasSystemFeature("android.hardware.type.television")) {
                return true;
            }

            // Tertiary detection: No touchscreen (TV indicator)
            return !pm.hasSystemFeature("android.hardware.touchscreen");

        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                System.err.println("[IntegratedDeadLetter] Device detection failed: " + e.getMessage());
            }
            return false; // Default to mobile if detection fails
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
        metadata.put("deviceType", isAndroidTV ? "TV" : "Mobile");
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
            return category instanceof String ? (String) category : "default";
        }
        return "default";
    }
}
