package com.newrelic.videoagent.core.model;

import com.newrelic.videoagent.core.tracker.NRTracker;

/**
 * Tracker pair model.
 */
public class NRTrackerPair {
    private final NRTracker first;
    private final NRTracker second;

    /**
     * Init a NSTrackerPair with two trackers.
     *
     * @param first First tracker.
     * @param second Second tracker.
     */
    public NRTrackerPair(NRTracker first, NRTracker second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Get first tracker.
     *
     * @return First tracker.
     */
    public NRTracker getFirst() {
        return first;
    }

    /**
     * Get second tracker.
     *
     * @return Second tracker.
     */
    public NRTracker getSecond() {
        return second;
    }
}
