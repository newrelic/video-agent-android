package com.newrelic.videoagent.core.model;

import com.newrelic.videoagent.core.tracker.NRTracker;

public class NRTrackerPair {
    private final NRTracker first;
    private final NRTracker second;

    public NRTrackerPair(NRTracker first, NRTracker second) {
        this.first = first;
        this.second = second;
    }

    public NRTracker getFirst() {
        return first;
    }

    public NRTracker getSecond() {
        return second;
    }
}
