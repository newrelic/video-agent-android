package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;

/**
 * Interface for event buffering with priority support
 * Handles temporary storage of video analytics events before harvest
 * Enhanced with capacity monitoring for 60% threshold scheduler startup
 */
public interface EventBufferInterface {

    /**
     * Add an event to the buffer
     */
    void addEvent(Map<String, Object> event);

    /**
     * Poll a batch of events based on priority and size constraints
     */
    List<Map<String, Object>> pollBatchByPriority(int maxSizeBytes, SizeEstimator sizeEstimator, String priority);

    /**
     * Get total number of events in buffer
     */
    int getEventCount();

    /**
     * Get estimated size of all events in buffer
     */
    int getSize(SizeEstimator sizeEstimator);

    /**
     * Check if buffer is empty
     */
    boolean isEmpty();

    /**
     * Clean up resources
     */
    void cleanup();

    /**
     * Get maximum capacity of this buffer
     */
    int getMaxCapacity();

    /**
     * Check if buffer has reached capacity threshold (for scheduler startup)
     */
    boolean hasReachedCapacityThreshold(double threshold);

    /**
     * Set overflow callback for buffers that support overflow prevention
     */
    default void setOverflowCallback(OverflowCallback callback) {
        // Default: no-op for buffers that don't support overflow prevention
    }

    /**
     * Set capacity callback for monitoring buffer fill levels
     */
    default void setCapacityCallback(CapacityCallback callback) {
        // Default: no-op for buffers that don't support capacity monitoring
    }

    /**
     * Interface for overflow notification callback
     */
    interface OverflowCallback {
        void onBufferNearFull(String bufferType);
    }

    /**
     * Interface for capacity monitoring callback
     */
    interface CapacityCallback {
        void onCapacityThresholdReached(double currentCapacity, String bufferType);
    }
}
