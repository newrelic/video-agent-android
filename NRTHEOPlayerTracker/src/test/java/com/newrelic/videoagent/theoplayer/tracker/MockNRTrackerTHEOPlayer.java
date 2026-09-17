package com.newrelic.videoagent.theoplayer.tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test double for NRTrackerTHEOPlayer.
 *
 * Overrides sendVideoEvent / sendVideoAdEvent to capture emitted event types
 * without making real HTTP calls. All state-machine logic (goStart, goBufferStart,
 * etc.) runs from the real production implementation, so event-sequence tests
 * reflect real behaviour.
 */
public class MockNRTrackerTHEOPlayer extends NRTrackerTHEOPlayer {

    private final List<String> sentEvents = new ArrayList<>();

    MockNRTrackerTHEOPlayer() {
        super();
    }

    @Override
    public void sendVideoEvent(String eventType, Map<String, Object> attributes) {
        sentEvents.add(eventType);
    }

    @Override
    public void sendVideoAdEvent(String eventType, Map<String, Object> attributes) {
        sentEvents.add(eventType);
    }

    public List<String> getSentEvents() {
        return Collections.unmodifiableList(sentEvents);
    }

    public boolean wasEventSent(String eventType) {
        return sentEvents.contains(eventType);
    }

    public String getLastEvent() {
        return sentEvents.isEmpty() ? null : sentEvents.get(sentEvents.size() - 1);
    }

    public void clearEvents() {
        sentEvents.clear();
    }
}
