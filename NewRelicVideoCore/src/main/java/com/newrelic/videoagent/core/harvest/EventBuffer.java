package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import com.newrelic.videoagent.core.harvest.SizeEstimator;

public class EventBuffer {
    private final Map<String, Object>[] buffer;
    private static final int MAX_BUFFER_SIZE = 10000; // Example limit, tune as needed
    private int head;
    private int tail;
    private int size;

    @SuppressWarnings("unchecked")
    public EventBuffer() {
        buffer = new Map[MAX_BUFFER_SIZE];
    }

    public void addEvent(Map<String, Object> event) {
        if (event == null) {
            // Log or handle null event error
            System.err.println("[EventBuffer] Attempted to add null event.");
            return;
        }
        if (size < MAX_BUFFER_SIZE) {
            buffer[tail] = event;
            tail = (tail + 1) % MAX_BUFFER_SIZE;
            size++;
        }
    }

    public boolean addEventOrHarvest(Map<String, Object> event, int maxBatchBytes, SizeEstimator estimator, List<Map<String, Object>> harvestBatchOut) {
        if (event == null) {
            // Log or handle null event error
            System.err.println("[EventBuffer] Attempted to add null event.");
            return false;
        }
        if (size < MAX_BUFFER_SIZE) {
            buffer[tail] = event;
            tail = (tail + 1) % MAX_BUFFER_SIZE;
            size++;
            return false; // No harvest needed
        } else {
            // Buffer is full, harvest current batch
            List<Map<String, Object>> batch = pollBatch(maxBatchBytes, estimator);
            if (harvestBatchOut != null) {
                harvestBatchOut.addAll(batch);
            }
            // Add the incoming event after harvest
            buffer[tail] = event;
            tail = (tail + 1) % MAX_BUFFER_SIZE;
            size++;
            return true; // Harvest triggered
        }
    }

    public int getSize(SizeEstimator estimator) {
        if (estimator == null) return 0;
        int totalSize = 0;
        for (int i = 0, idx = head; i < size; i++, idx = (idx + 1) % MAX_BUFFER_SIZE) {
            Map<String, Object> event = buffer[idx];
            if (event != null) {
                try {
                    totalSize += estimator.estimate(event);
                } catch (Exception e) {
                    System.err.println("[EventBuffer] Error estimating event size: " + e.getMessage());
                }
            }
        }
        return totalSize;
    }

    public List<Map<String, Object>> pollBatch(int maxBytes, SizeEstimator estimator) {
        List<Map<String, Object>> batch = new ArrayList<>();
        int batchSize = 0;
        int count = 0;
        while (count < size) {
            int idx = (head + count) % MAX_BUFFER_SIZE;
            Map<String, Object> event = buffer[idx];
            if (event == null) {
                System.err.println("[EventBuffer] Null event found in buffer during batch poll.");
                count++;
                continue;
            }
            int eventSize = 0;
            try {
                eventSize = estimator.estimate(event);
            } catch (Exception e) {
                System.err.println("[EventBuffer] Error estimating event size: " + e.getMessage());
                count++;
                continue;
            }
            if (batchSize + eventSize > maxBytes) break;
            batch.add(event);
            batchSize += eventSize;
            count++;
        }
        // Remove polled events from buffer
        head = (head + count) % MAX_BUFFER_SIZE;
        size -= count;
        return batch;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size >= MAX_BUFFER_SIZE;
    }

    public int getEventCount() {
        return size;
    }

    public void clear() {
        head = 0;
        tail = 0;
        size = 0;
    }
}
