package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.newrelic.videoagent.core.NRVideoConstants;

/**
 * Specialized event buffer for dead letter queue - retry events only
 * Does NOT trigger schedulers or callbacks - just stores and retrieves events
 * Simpler, focused implementation without scheduler integration
 */
public class DeadLetterEventBuffer implements EventBufferInterface {

    private final ConcurrentLinkedQueue<Map<String, Object>> retryEvents = new ConcurrentLinkedQueue<>();
    private final int maxCapacity;
    private final boolean isAndroidTVDevice;

    public DeadLetterEventBuffer(boolean isTV) {
        this.isAndroidTVDevice = isTV;
        // Smaller capacity for dead letter queue - it's just for retries
        this.maxCapacity = isTV ? 200 : 100;
    }

    @Override
    public void addEvent(Map<String, Object> event) {
        if (event == null) return;

        // Simple add with capacity management - no callbacks, no scheduler triggers
        retryEvents.offer(event);

        // Simple overflow protection - remove oldest if over capacity
        while (retryEvents.size() > maxCapacity) {
            retryEvents.poll();
        }
    }

    @Override
    public List<Map<String, Object>> pollBatchByPriority(int maxSizeBytes, SizeEstimator sizeEstimator, String priority) {
        List<Map<String, Object>> batch = new ArrayList<>();

        // Simple FIFO - no priority separation needed for retry events
        int maxEvents = isAndroidTVDevice ? 20 : 10; // Small batches for retries
        int currentSize = 0;

        for (int i = 0; i < maxEvents && !retryEvents.isEmpty(); i++) {
            Map<String, Object> event = retryEvents.poll();
            if (event == null) break;

            int eventSize = sizeEstimator != null ? sizeEstimator.estimate(event) : 2048;
            if (currentSize + eventSize > maxSizeBytes && !batch.isEmpty()) {
                retryEvents.offer(event); // Put back
                break;
            }

            batch.add(event);
            currentSize += eventSize;
        }

        return batch;
    }

    @Override
    public int getEventCount() {
        return retryEvents.size();
    }

    @Override
    public boolean isEmpty() {
        return retryEvents.isEmpty();
    }

    @Override
    public void cleanup() {
        retryEvents.clear();
    }

    @Override
    public int getMaxCapacity() {
        return maxCapacity;
    }

    @Override
    public boolean hasReachedCapacityThreshold(double threshold) {
        double currentCapacity = (double) retryEvents.size() / maxCapacity;
        return currentCapacity >= threshold;
    }

    // Dead letter queues don't need callbacks - they're just retry storage
    @Override
    public void setOverflowCallback(OverflowCallback callback) {
        // No-op - dead letter queues don't trigger harvests
    }

    @Override
    public void setCapacityCallback(CapacityCallback callback) {
        // No-op - dead letter queues don't start schedulers
    }
}
