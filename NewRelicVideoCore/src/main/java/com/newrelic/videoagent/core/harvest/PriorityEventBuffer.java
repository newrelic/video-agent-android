package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import com.newrelic.videoagent.core.NRVideoConstants;

/**
 * Video-optimized priority event buffer for mobile/TV environments
 * Separates live streaming events from on-demand content events
 * Uses thread-safe ConcurrentLinkedQueue for better reliability
 * Simple overflow detection triggers immediate harvest
 * Deduplicates CONTIGUOUS events by actionName with O(1) atomic tracking
 * OPTIMIZED: Reduced buffer sizes for 2KB events with dynamic device detection
 */
public class PriorityEventBuffer implements EventBufferInterface {
    // Live streaming events need immediate processing (live TV, sports, news)
    private final ConcurrentLinkedQueue<Map<String, Object>> liveEvents = new ConcurrentLinkedQueue<>();

    // On-demand events can tolerate some delay (movies, series, recorded content)
    private final ConcurrentLinkedQueue<Map<String, Object>> ondemandEvents = new ConcurrentLinkedQueue<>();

    // O(1) contiguous duplicate tracking - track the last actionName for each queue
    private final AtomicReference<String> lastLiveActionName = new AtomicReference<>();
    private final AtomicReference<String> lastOndemandActionName = new AtomicReference<>();

    // OPTIMIZED for 2KB events - device-specific buffer sizes
    private final int MAX_LIVE_EVENTS;
    private final int MAX_ONDEMAND_EVENTS;
    private final boolean isAndroidTVDevice;

    // Enhanced callback support
    private OverflowCallback overflowCallback;
    private CapacityCallback capacityCallback;

    public PriorityEventBuffer() {
        // Default constructor - assume mobile device
        this(false);
    }

    public PriorityEventBuffer(boolean isTV) {
        this.isAndroidTVDevice = isTV;

        if (isAndroidTVDevice) {
            // Android TV: More memory available, longer content sessions
            this.MAX_LIVE_EVENTS = 300;      // 300 × 2KB = 600KB (live needs quick processing)
            this.MAX_ONDEMAND_EVENTS = 700;  // 700 × 2KB = 1.4MB (can batch larger)
        } else {
            // Mobile: Memory-conscious, shorter sessions, frequent harvesting
            this.MAX_LIVE_EVENTS = 150;      // 150 × 2KB = 300KB (conservative for mobile)
            this.MAX_ONDEMAND_EVENTS = 350;  // 350 × 2KB = 700KB (balanced for mobile)
        }
    }

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
        boolean shouldStartScheduler = false;
        String harvestType = null;

        // Get the target queue and last action tracker for this event
        ConcurrentLinkedQueue<Map<String, Object>> targetQueue = isLiveContent ? liveEvents : ondemandEvents;
        AtomicReference<String> lastActionTracker = isLiveContent ? lastLiveActionName : lastOndemandActionName;
        int maxCapacity = isLiveContent ? MAX_LIVE_EVENTS : MAX_ONDEMAND_EVENTS;

        // SCHEDULER STARTUP: Start scheduler on FIRST event of each category
        boolean wasEmpty = targetQueue.isEmpty();

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

        // CRITICAL: Start scheduler on first event of this category
        if (wasEmpty) {
            shouldStartScheduler = true;
        }

        // Check capacity thresholds AFTER the event is added
        double currentCapacity = (double) targetQueue.size() / maxCapacity;

        // Check for 90% threshold (overflow prevention)
        if (currentCapacity >= 0.9) {
            shouldTriggerHarvest = true;
            harvestType = isLiveContent ? NRVideoConstants.EVENT_TYPE_LIVE : NRVideoConstants.EVENT_TYPE_ONDEMAND;
        }

        // Fallback: if we somehow still reach max capacity, remove oldest events
        while (liveEvents.size() > MAX_LIVE_EVENTS) {
            liveEvents.poll();
        }
        while (ondemandEvents.size() > MAX_ONDEMAND_EVENTS) {
            ondemandEvents.poll();
        }

        if (shouldStartScheduler) {
            capacityCallback.onCapacityThresholdReached(0.0, isLiveContent ? NRVideoConstants.EVENT_TYPE_LIVE : NRVideoConstants.EVENT_TYPE_ONDEMAND);
        }

        if (shouldTriggerHarvest) {
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
        ConcurrentLinkedQueue<Map<String, Object>> targetQueue = NRVideoConstants.EVENT_TYPE_LIVE.equals(priority) ? liveEvents : ondemandEvents;

        int currentSize = 0;
        // OPTIMIZED: Adjusted batch sizes for 2KB events and device capabilities
        int maxEvents;
        if (NRVideoConstants.EVENT_TYPE_LIVE.equals(priority)) {
            // Live events: Quick, small batches for low latency
            maxEvents = isAndroidTVDevice ? 25 : 15;  // TV: 50KB batches, Mobile: 30KB batches
        } else {
            // On-demand events: Larger batches for efficiency
            maxEvents = isAndroidTVDevice ? 50 : 30;  // TV: 100KB batches, Mobile: 60KB batches
        }

        for (int i = 0; i < maxEvents && !targetQueue.isEmpty(); i++) {
            Map<String, Object> event = targetQueue.poll();
            if (event == null) break;

            // Use actual 2KB size estimate if no estimator provided
            int eventSize = sizeEstimator != null ? sizeEstimator.estimate(event) : 2048;
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

    @Override
    public int getMaxCapacity() {
        return MAX_LIVE_EVENTS + MAX_ONDEMAND_EVENTS;
    }

    @Override
    public boolean hasReachedCapacityThreshold(double threshold) {
        int totalEvents = liveEvents.size() + ondemandEvents.size();
        double currentCapacity = (double) totalEvents / getMaxCapacity();
        return currentCapacity >= threshold;
    }

    @Override
    public void setCapacityCallback(CapacityCallback callback) {
        this.capacityCallback = callback;
    }
}
