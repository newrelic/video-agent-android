package com.newrelic.videoagent.exoplayer.tracker;

import java.util.HashMap;
import java.util.Map;

/**
 * Test double for NRTrackerExoPlayer.
 *
 * Only intercepts event emission (sendVideoEvent / sendVideoAdEvent) and
 * suppresses the Android Handler used for delayed flush scheduling.
 * All aggregation logic runs from the real production implementation.
 */
public class MockNRTrackerExoPlayer extends NRTrackerExoPlayer {

    private boolean eventSent = false;
    private String lastEventType = null;
    private Map<String, Object> lastEventAttributes = null;

    @Override
    public void sendVideoEvent(String eventType, Map<String, Object> attributes) {
        this.eventSent = true;
        this.lastEventType = eventType;
        this.lastEventAttributes = attributes != null ? new HashMap<>(attributes) : null;
    }

    @Override
    public void sendVideoAdEvent(String eventType, Map<String, Object> attributes) {
        this.eventSent = true;
        this.lastEventType = eventType;
        this.lastEventAttributes = attributes != null ? new HashMap<>(attributes) : null;
    }

    @Override
    protected void scheduleDelayedFlush() {
        // Suppress Android Handler — tests call simulateFlush() explicitly.
    }

    public boolean wasEventSent() {
        return eventSent;
    }

    public String getLastEventType() {
        return lastEventType;
    }

    public Map<String, Object> getLastEventAttributes() {
        return lastEventAttributes;
    }

    public void clearEvents() {
        eventSent = false;
        lastEventType = null;
        lastEventAttributes = null;
    }

    /** Force-flush the current aggregation window without waiting for the 5-second timer. */
    public void simulateFlush() {
        flushCurrentAggregation();
    }
}
