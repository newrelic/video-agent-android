package com.newrelic.videoagent.core.telemetry.harvest;

import com.newrelic.videoagent.core.model.NREventAttributes;
import com.newrelic.videoagent.core.telemetry.configuration.VideoAgentConfiguration;
import com.newrelic.videoagent.core.utils.NRLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class VideoHarvestData {

    // A queue for events. ConcurrentLinkedQueue is generally thread-safe for add/poll operations
    // without explicit locking for individual queue operations.
    private final ConcurrentLinkedQueue<Map<String, Object>> events;

    // A list for video metrics. Manual synchronization is applied using a ReentrantLock
    // to protect against concurrent modifications and reads.
    private final List<Map<String, Object>> videoMetrics;

    // Lock for synchronizing access to videoMetrics and any other shared mutable state
    // where Concurrent collections are not used or more complex synchronization is needed.
    private final ReentrantLock dataLock = new ReentrantLock();

    /**
     * Constructor for VideoHarvestData. Initializes the internal data structures.
     */
    public VideoHarvestData() {
        this.events = new ConcurrentLinkedQueue<>();
        this.videoMetrics = new ArrayList<>();
        NRLog.d("VideoHarvestData initialized.");
    }

    /**
     * Adds a generic event (e.g., custom event) to the harvest data queue.
     * This method is thread-safe due to the use of ConcurrentLinkedQueue.
     *
     * @param event A map representing the event data.
     */
    public void addEvent(Map<String, Object> event) {
        if (event == null) {
            NRLog.d("Attempted to add a null event to harvest data.");
            return;
        }
        events.add(event);
        NRLog.d("Added event to harvest data: " + event.get("eventType"));
    }

    /**
     * Adds a video-specific metric to the harvest data list.
     * This method is thread-safe, utilizing a ReentrantLock to prevent race conditions
     * during list modifications.
     *
     * @param metric A map representing the video metric data.
     */
    public void addVideoMetric(Map<String, Object> metric) {
        if (metric == null) {
            NRLog.d("Attempted to add a null video metric to harvest data.");
            return;
        }
        dataLock.lock(); // Acquire lock to ensure exclusive access
        try {
            videoMetrics.add(metric);
            NRLog.d("Added video metric to harvest data: " + metric.get(NREventAttributes.VIDEO_EVENT_TYPE));
        } finally {
            dataLock.unlock(); // Release lock
        }
    }

    /**
     * Retrieves and atomically clears all accumulated events from the queue.
     *
     * @return A collection of event maps that were present in the queue.
     */
    public Collection<Map<String, Object>> getAndClearEvents() {
        List<Map<String, Object>> currentEvents = new ArrayList<>();
        // Atomically move all elements from the queue to the list for processing.
        // This effectively "clears" the queue for the next harvest cycle.
        while (!events.isEmpty()) {
            currentEvents.add(events.poll());
        }
        NRLog.d("Retrieved and cleared " + currentEvents.size() + " events for harvest.");
        return currentEvents;
    }

    /**
     * Retrieves and atomically clears all accumulated video metrics from the list.
     * This method is thread-safe, utilizing a ReentrantLock to prevent race conditions.
     *
     * @return A collection of video metric maps that were present in the list.
     */
    public Collection<Map<String, Object>> getAndClearVideoMetrics() {
        dataLock.lock(); // Acquire lock
        try {
            List<Map<String, Object>> currentMetrics = new ArrayList<>(videoMetrics);
            videoMetrics.clear(); // Clear the list after copying its contents
            NRLog.d("Retrieved and cleared " + currentMetrics.size() + " video metrics for harvest.");
            return currentMetrics;
        } finally {
            dataLock.unlock(); // Release lock
        }
    }

    /**
     * Checks if there is any data (events or metrics) currently held for harvesting.
     * This method is thread-safe due to the use of a ReentrantLock for `videoMetrics`
     * and the thread-safe nature of `ConcurrentLinkedQueue.isEmpty()`.
     *
     * @return true if there is data in either the events queue or video metrics list, false otherwise.
     */
    public boolean hasData() {
        dataLock.lock(); // Acquire lock for consistent check across data structures
        try {
            return !events.isEmpty() || !videoMetrics.isEmpty();
        } finally {
            dataLock.unlock(); // Release lock
        }
    }

    /**
     * Clears all data held in this harvest data instance.
     * This method is thread-safe, ensuring all data structures are cleared consistently.
     */
    public void clear() {
        dataLock.lock(); // Acquire lock
        try {
            events.clear();       // Clear events queue
            videoMetrics.clear(); // Clear video metrics list
            NRLog.d("All harvest data cleared.");
        } finally {
            dataLock.unlock(); // Release lock
        }
    }

    /**
     * Placeholder for updating internal configuration or limits based on new harvest configuration.
     * For example, this could be used to adjust the maximum number of events or metrics to store.
     *
     * @param config The updated VideoAgentConfiguration.
     */
    public void updateConfiguration(VideoAgentConfiguration config) {
        // Implement logic to adjust data limits or other behaviors based on config changes.
        // Example: this.maxEvents = config.getMaxEvents();
        NRLog.d("VideoHarvestData configuration updated.");
    }
}
