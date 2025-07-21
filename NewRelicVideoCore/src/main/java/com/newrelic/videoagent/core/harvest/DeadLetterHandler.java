package com.newrelic.videoagent.core.harvest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.newrelic.videoagent.core.NRVideoConfiguration;

/**
 * Handler for dead letter queue operations (Single Responsibility Principle)
 * Separated from main HarvestManager for better organization
 */
public class DeadLetterHandler {
    private final EventBufferInterface deadLetterQueue;
    private final HttpClientInterface httpClient;
    private final NRVideoConfiguration configuration;

    public DeadLetterHandler(EventBufferInterface deadLetterQueue, HttpClientInterface httpClient, NRVideoConfiguration configuration) {
        this.deadLetterQueue = deadLetterQueue;
        this.httpClient = httpClient;
        this.configuration = configuration;
    }

    public void handleFailedEvents(List<Map<String, Object>> failedEvents, String category) {
        for (Map<String, Object> event : failedEvents) {
            if (deadLetterQueue.getEventCount() >= configuration.getMaxDeadLetterSize()) {
                // Remove old events to make space - use smaller batch for efficiency
                deadLetterQueue.pollBatchByPriority(512, new DefaultSizeEstimator(), "normal");
            }

            // Create a wrapper for the event with retry metadata
            // Keep original event data clean for analytics
            Map<String, Object> eventWrapper = new HashMap<>();
            eventWrapper.put("originalEvent", event); // Actual video analytics data
            eventWrapper.put("retryMetadata", createRetryMetadata(event, category)); // Internal metadata

            deadLetterQueue.addEvent(eventWrapper);
        }
    }

    private Map<String, Object> createRetryMetadata(Map<String, Object> originalEvent, String category) {
        Map<String, Object> metadata = new HashMap<>();

        // Check if this is already a retry (extract existing metadata)
        Object existingMetadata = originalEvent.get("retryMetadata");
        int currentRetryCount = 0;
        if (existingMetadata instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) existingMetadata;
            currentRetryCount = getIntValue(existing, "retryCount", 0);
        }

        metadata.put("retryCount", currentRetryCount);
        metadata.put("failedAt", System.currentTimeMillis());
        metadata.put("originalCategory", category);
        metadata.put("firstFailedAt", System.currentTimeMillis()); // Track when it first failed

        return metadata;
    }

    public void retryFailedEvents() {
        if (deadLetterQueue.isEmpty()) return;

        long now = System.currentTimeMillis();
        int retryBatchSize = configuration.getMaxBatchSizeBytes() / 4;

        List<Map<String, Object>> retryBatch = deadLetterQueue.pollBatchByPriority(
            retryBatchSize,
            new DefaultSizeEstimator(),
            "high"
        );

        if (retryBatch.isEmpty()) {
            retryBatch = deadLetterQueue.pollBatchByPriority(
                retryBatchSize,
                new DefaultSizeEstimator(),
                "normal"
            );
        }

        if (!retryBatch.isEmpty()) {
            List<Map<String, Object>> readyForRetry = new ArrayList<>();
            List<Map<String, Object>> notReadyYet = new ArrayList<>();

            for (Map<String, Object> eventWrapper : retryBatch) {
                @SuppressWarnings("unchecked")
                Map<String, Object> retryMetadata = (Map<String, Object>) eventWrapper.get("retryMetadata");

                if (retryMetadata == null) continue; // Skip malformed entries

                long failedAt = getLongValue(retryMetadata, "failedAt", 0L);
                int retryCount = getIntValue(retryMetadata, "retryCount", 0);

                // Exponential backoff optimized for video streaming scenarios
                // Users don't stay in video apps during network issues for long
                long baseInterval = configuration.getDeadLetterRetryInterval();

                // For video streaming: shorter, more aggressive retry schedule
                // Retry 1: 30 seconds, Retry 2: 1 minute, Retry 3: 2 minutes
                long minRetryInterval;
                if (retryCount == 0) {
                    minRetryInterval = Math.min(baseInterval / 10, 30000L); // 30 seconds max
                } else if (retryCount == 1) {
                    minRetryInterval = Math.min(baseInterval / 5, 60000L);  // 1 minute max
                } else {
                    minRetryInterval = Math.min(baseInterval * (1L << (retryCount - 2)), 120000L); // 2 minutes max
                }

                if (now - failedAt >= minRetryInterval) {
                    readyForRetry.add(eventWrapper);
                } else {
                    notReadyYet.add(eventWrapper);
                }
            }

            // Put back events not ready for retry
            for (Map<String, Object> eventWrapper : notReadyYet) {
                deadLetterQueue.addEvent(eventWrapper);
            }

            // Extract clean events for retry (without internal metadata)
            if (!readyForRetry.isEmpty()) {
                List<Map<String, Object>> cleanEvents = new ArrayList<>();
                String category = "regular";

                for (Map<String, Object> eventWrapper : readyForRetry) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> originalEvent = (Map<String, Object>) eventWrapper.get("originalEvent");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> retryMetadata = (Map<String, Object>) eventWrapper.get("retryMetadata");

                    if (originalEvent != null) {
                        cleanEvents.add(originalEvent); // Send ONLY the clean analytics data
                    }

                    if (retryMetadata != null) {
                        category = (String) getMapValue(retryMetadata, "originalCategory", "regular");
                    }
                }

                boolean success = httpClient.sendEvents(cleanEvents, category + "-retry");

                if (!success) {
                    // Update retry metadata and put back for later retry
                    for (Map<String, Object> eventWrapper : readyForRetry) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> retryMetadata = (Map<String, Object>) eventWrapper.get("retryMetadata");

                        if (retryMetadata != null) {
                            int retryCount = getIntValue(retryMetadata, "retryCount", 0);
                            retryMetadata.put("retryCount", retryCount + 1);
                            retryMetadata.put("failedAt", now);

                            // Max 3 retries for mobile/TV optimization (instead of 5)
                            if (retryCount < 3) {
                                deadLetterQueue.addEvent(eventWrapper);
                            } else if (configuration.isDebugLoggingEnabled()) {
                                long firstFailedAt = getLongValue(retryMetadata, "firstFailedAt", now);
                                System.out.println("[DeadLetterHandler] Dropping event after 3 retries (mobile optimized). " +
                                    "Total time in dead letter queue: " + ((now - firstFailedAt) / 1000) + " seconds");
                            }
                        }
                    }
                }
            }
        }
    }

    // Helper methods for API 16+ compatibility
    private Object getMapValue(Map<String, Object> map, String key, Object defaultValue) {
        Object value = map.get(key);
        return value != null ? value : defaultValue;
    }

    private long getLongValue(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return defaultValue;
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        return defaultValue;
    }
}
