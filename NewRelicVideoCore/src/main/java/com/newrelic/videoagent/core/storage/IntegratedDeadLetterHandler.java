package com.newrelic.videoagent.core.storage;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.DefaultSizeEstimator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integrated dead letter handler with crash-safe backup
 * Clean integration with CrashSafeEventBuffer for seamless operation
 */
public class IntegratedDeadLetterHandler {

    private final EventBufferInterface inMemoryQueue;
    private final CrashSafeEventBuffer mainBuffer;
    private final HttpClientInterface httpClient;
    private final NRVideoConfiguration configuration;

    // Retry limits
    private static final int MAX_RETRIES = 3;

    public IntegratedDeadLetterHandler(EventBufferInterface deadLetterQueue,
                                     CrashSafeEventBuffer mainBuffer,
                                     HttpClientInterface httpClient,
                                     NRVideoConfiguration configuration) {
        this.inMemoryQueue = deadLetterQueue;
        this.mainBuffer = mainBuffer;
        this.httpClient = httpClient;
        this.configuration = configuration;
    }

    /**
     * Handle failed events - retry in memory, backup when exhausted
     */
    public void handleFailedEvents(List<Map<String, Object>> failedEvents, String category) {
        if (failedEvents == null || failedEvents.isEmpty()) return;

        List<Map<String, Object>> toRetry = new ArrayList<>();
        List<Map<String, Object>> toBackup = new ArrayList<>();

        // Separate events based on retry count
        for (Map<String, Object> event : failedEvents) {
            int retryCount = getRetryCount(event);

            if (retryCount < MAX_RETRIES) {
                // Add retry metadata and queue for retry
                Map<String, Object> retryEvent = addRetryMetadata(event, category, retryCount + 1);
                toRetry.add(retryEvent);
            } else {
                // Retries exhausted - backup to SQLite
                toBackup.add(cleanEvent(event));
            }
        }

        // Queue events for retry
        for (Map<String, Object> event : toRetry) {
            if (inMemoryQueue.getEventCount() >= configuration.getMaxDeadLetterSize()) {
                inMemoryQueue.pollBatchByPriority(512, new DefaultSizeEstimator(), "normal");
            }
            inMemoryQueue.addEvent(event);
        }

        // Backup exhausted events
        if (!toBackup.isEmpty()) {
            mainBuffer.backupFailedEvents(toBackup);

            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[IntegratedDeadLetter] Retrying: " + toRetry.size() +
                                 ", Backed up: " + toBackup.size());
            }
        }
    }

    /**
     * Retry failed events from in-memory queue
     */
    public void retryFailedEvents() {
        try {
            List<Map<String, Object>> events = inMemoryQueue.pollBatchByPriority(
                8192, new DefaultSizeEstimator(), "normal");

            if (events.isEmpty()) return;

            // Group by category for efficient processing
            Map<String, List<Map<String, Object>>> grouped = groupByCategory(events);

            for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
                retryBatch(entry.getValue(), entry.getKey());
            }

        } catch (Exception e) {
            System.err.println("[IntegratedDeadLetter] Retry failed: " + e.getMessage());
        }
    }

    /**
     * Emergency backup of pending retries during app kill
     */
    public void emergencyBackup() {
        try {
            List<Map<String, Object>> pendingEvents = inMemoryQueue.pollBatchByPriority(
                Integer.MAX_VALUE, null, "normal");

            if (!pendingEvents.isEmpty()) {
                List<Map<String, Object>> cleanEvents = new ArrayList<>();
                for (Map<String, Object> event : pendingEvents) {
                    cleanEvents.add(extractOriginalEvent(event));
                }
                mainBuffer.backupFailedEvents(cleanEvents);
                System.out.println("[IntegratedDeadLetter] Emergency backup: " + cleanEvents.size() + " events");
            }
        } catch (Exception e) {
            System.err.println("[IntegratedDeadLetter] Emergency backup failed: " + e.getMessage());
        }
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
            }
        } catch (Exception e) {
            handleFailedEvents(cleanEvents, category);
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
        retryEvent.put("retryMetadata", metadata);
        return retryEvent;
    }

    private Map<String, Object> cleanEvent(Map<String, Object> event) {
        Map<String, Object> clean = new HashMap<>(event);
        clean.remove("retryMetadata");
        return clean;
    }

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
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(event);
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
