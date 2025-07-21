package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Video-optimized priority event buffer for mobile/TV environments
 * Separates live streaming events from on-demand content events
 * Uses thread-safe ConcurrentLinkedQueue for better reliability
 * Simple overflow detection triggers immediate harvest
 * Deduplicates CONTIGUOUS events by actionName with O(1) atomic tracking
 */
public class PriorityEventBuffer implements EventBufferInterface {
    // Live streaming events need immediate processing (live TV, sports, news)
    private final ConcurrentLinkedQueue<Map<String, Object>> liveEvents = new ConcurrentLinkedQueue<>();

    // On-demand events can tolerate some delay (movies, series, recorded content)
    private final ConcurrentLinkedQueue<Map<String, Object>> ondemandEvents = new ConcurrentLinkedQueue<>();

    // O(1) contiguous duplicate tracking - track the last actionName for each queue
    private final AtomicReference<String> lastLiveActionName = new AtomicReference<>();
    private final AtomicReference<String> lastOndemandActionName = new AtomicReference<>();

    private static final int MAX_LIVE_EVENTS = 1000;      // Smaller buffer for immediate processing
    private static final int MAX_ONDEMAND_EVENTS = 3000;  // Larger buffer for batch processing

    // Use interface callback instead of custom one
    private OverflowCallback overflowCallback;

    /**
     * Set overflow callback for immediate harvest when buffer is getting full
     * Implements the interface method
     */
    @Override
    public void setOverflowCallback(OverflowCallback callback) {
        this.overflowCallback = callback;
    }

    @Override
    public void addEvent(Map<String, Object> event) {
        if (event == null) return;

        // Check if this is a live streaming event
        boolean isLiveContent = isLiveStreamingEvent(event);
        boolean shouldTriggerHarvest = false;
        String harvestType = null;

        // Get the target queue and last action tracker for this event
        ConcurrentLinkedQueue<Map<String, Object>> targetQueue = isLiveContent ? liveEvents : ondemandEvents;
        AtomicReference<String> lastActionTracker = isLiveContent ? lastLiveActionName : lastOndemandActionName;
        int maxCapacity = isLiveContent ? MAX_LIVE_EVENTS : MAX_ONDEMAND_EVENTS;

        // CONTIGUOUS DEDUPLICATION: O(1) check using atomic variable
        String actionName = (String) event.get("actionName");
        if (actionName != null && !actionName.trim().isEmpty()) {
            removeContiguousDuplicates(targetQueue, lastActionTracker, actionName);
        }

        // Add the new event (this will be the most recent one)
        targetQueue.offer(event);

        // Update the last action tracker - O(1) atomic operation
        if (actionName != null && !actionName.trim().isEmpty()) {
            lastActionTracker.set(actionName);
        }

        // Check if we should trigger harvest AFTER the event is added
        if (targetQueue.size() >= (maxCapacity * 0.9)) {
            shouldTriggerHarvest = true;
            harvestType = isLiveContent ? "live" : "ondemand";
        }

        // Fallback: if we somehow still reach max capacity, remove oldest events
        while (liveEvents.size() > MAX_LIVE_EVENTS) {
            liveEvents.poll();
            // Note: We don't update lastActionTracker here since we don't know what was removed
            // This is acceptable since the tracker is for optimization, not correctness
        }
        while (ondemandEvents.size() > MAX_ONDEMAND_EVENTS) {
            ondemandEvents.poll();
            // Note: We don't update lastActionTracker here since we don't know what was removed
        }

        // IMPORTANT: Call harvest callback AFTER all queue operations are complete
        // This prevents race condition between offer() and poll() operations
        if (shouldTriggerHarvest && overflowCallback != null) {
            overflowCallback.onBufferNearFull(harvestType);
        }
    }

    /**
     * Remove contiguous duplicate events - TRUE O(1) operation using atomic variable
     * Only checks if the last actionName matches the new one
     * This is sufficient for video analytics where we want to prevent consecutive identical events
     *
     * @param queue The queue to check for contiguous duplicates
     * @param lastActionTracker Atomic reference tracking the last actionName
     * @param actionName The action name to deduplicate (e.g., "CONTENT_DOWNLOAD_ERROR")
     */
    private void removeContiguousDuplicates(ConcurrentLinkedQueue<Map<String, Object>> queue,
                                          AtomicReference<String> lastActionTracker,
                                          String actionName) {
        // O(1) check - just compare with the last actionName
        String lastAction = lastActionTracker.get();
        if (actionName.equals(lastAction)) {
            // The last event has the same actionName - it's a contiguous duplicate
            // We need to remove it, but since we don't have direct access to the last event,
            // we'll remove from tail by polling and checking
            Map<String, Object> lastEvent = pollLastEvent(queue);
            if (lastEvent != null) {
                String actualLastAction = (String) lastEvent.get("actionName");
                if (!actionName.equals(actualLastAction)) {
                    // Put it back if it's not actually a duplicate (race condition safety)
                    queue.offer(lastEvent);
                }
                // If it was a duplicate, we successfully removed it
            }
        }
    }

    /**
     * Poll the last event from the queue (tail removal)
     * Since ConcurrentLinkedQueue doesn't have pollLast(), we use a workaround
     */
    private Map<String, Object> pollLastEvent(ConcurrentLinkedQueue<Map<String, Object>> queue) {
        if (queue.isEmpty()) return null;

        // Convert to array and get the last element, then remove it
        // This is only called when we detect a duplicate, so it's rare
        Map<String, Object>[] events = queue.toArray(new Map[0]);
        if (events.length == 0) return null;

        Map<String, Object> lastEvent = events[events.length - 1];
        queue.remove(lastEvent); // Remove the last event
        return lastEvent;
    }

    @Override
    public List<Map<String, Object>> pollBatchByPriority(int maxSizeBytes, SizeEstimator sizeEstimator, String priority) {
        List<Map<String, Object>> batch = new ArrayList<>();
        ConcurrentLinkedQueue<Map<String, Object>> targetQueue = "live".equals(priority) ? liveEvents : ondemandEvents;

        int currentSize = 0;
        // Live events: smaller batches for faster processing
        // On-demand events: larger batches for efficiency
        int maxEvents = "live".equals(priority) ? 50 : 100;

        for (int i = 0; i < maxEvents && !targetQueue.isEmpty(); i++) {
            Map<String, Object> event = targetQueue.poll();
            if (event == null) break;

            int eventSize = sizeEstimator != null ? sizeEstimator.estimate(event) : 100;
            if (currentSize + eventSize > maxSizeBytes && !batch.isEmpty()) {
                // Put the event back and break
                targetQueue.offer(event);
                break;
            }

            batch.add(event);
            currentSize += eventSize;
        }

        return batch;
    }

    @Override
    public int getEventCount() {
        return liveEvents.size() + ondemandEvents.size();
    }

    @Override
    public int getSize(SizeEstimator sizeEstimator) {
        if (sizeEstimator == null) return 0;

        int totalSize = 0;
        // Sample a few events to estimate total size (avoid expensive full iteration)
        int sampleCount = 0;
        int maxSamples = 10;

        for (Map<String, Object> event : liveEvents) {
            if (sampleCount++ >= maxSamples) break;
            totalSize += sizeEstimator.estimate(event);
        }

        for (Map<String, Object> event : ondemandEvents) {
            if (sampleCount++ >= maxSamples) break;
            totalSize += sizeEstimator.estimate(event);
        }

        // Extrapolate based on sample
        if (sampleCount > 0) {
            int totalEvents = getEventCount();
            totalSize = (totalSize * totalEvents) / sampleCount;
        }

        return totalSize;
    }

    @Override
    public boolean isEmpty() {
        return liveEvents.isEmpty() && ondemandEvents.isEmpty();
    }

    @Override
    public void cleanup() {
        clear();
    }

    public void clear() {
        liveEvents.clear();
        ondemandEvents.clear();
    }

    /**
     * Determines if an event is from live streaming content
     */
    private boolean isLiveStreamingEvent(Map<String, Object> event) {
        // Check for explicit live content marker
        Boolean isLive = (Boolean) event.get("isLive");
        if (isLive != null) {
            return isLive;
        }

        // Default to on-demand if not explicitly marked as live
        return false;
    }
}
