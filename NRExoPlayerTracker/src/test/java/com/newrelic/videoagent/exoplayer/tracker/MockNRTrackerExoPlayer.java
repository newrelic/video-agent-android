package com.newrelic.videoagent.exoplayer.tracker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class MockNRTrackerExoPlayer extends NRTrackerExoPlayer {

    private boolean eventSent = false;
    private String lastEventType = null;
    private Map<String, Object> lastEventAttributes = null;

    private long currentTimeMs = System.currentTimeMillis();
    private final ConcurrentHashMap<String, Object> mockLastTrackData = new ConcurrentHashMap<>();
    private final Map<String, Object> mockAggregationStatus = new ConcurrentHashMap<>();
    private boolean mockAggregationEnabled = true;

    public MockNRTrackerExoPlayer() {
        super();
        mockLastTrackData.put("lastFrameDropCount", 0);
        mockLastTrackData.put("lastFrameDropDuration", 0);
        mockLastTrackData.put("lastFrameDropTime", 0L);
        mockLastTrackData.put("lastUpdateTime", System.currentTimeMillis());
        mockLastTrackData.put("currentTotalFrames", 0);
        mockLastTrackData.put("currentTotalDuration", 0);
        mockLastTrackData.put("currentEventCount", 0);

        mockAggregationStatus.put("hasActiveAggregation", false);
        mockAggregationStatus.put("totalLostFrames", 0);
        mockAggregationStatus.put("totalLostFramesDuration", 0);
        mockAggregationStatus.put("eventCount", 0);
        mockAggregationStatus.put("firstDropTimestamp", 0L);
        mockAggregationStatus.put("lastDropTimestamp", 0L);
    }

    public void simulateTimeExpiry() {
        this.currentTimeMs += 6000; // Beyond DEFAULT_AGGREGATION_WINDOW_MS (5000)
        simulateFlush();
    }

    @Override
    public void sendVideoEvent(String eventType, Map<String, Object> attributes) {
        this.eventSent = true;
        this.lastEventType = eventType;
        this.lastEventAttributes = attributes != null ? new ConcurrentHashMap<>(attributes) : null;
    }

    @Override
    public void sendVideoAdEvent(String eventType, Map<String, Object> attributes) {
        this.eventSent = true;
        this.lastEventType = eventType;
        this.lastEventAttributes = attributes != null ? new ConcurrentHashMap<>(attributes) : null;
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

    @Override
    protected void scheduleDelayedFlush() {
        // Bypass Android Handler for unit tests - use explicit flush calls instead
    }

    @Override
    public void sendDroppedFrame(int count, int elapsed) {
        long currentTime = System.currentTimeMillis();

        mockLastTrackData.put("lastFrameDropCount", count);
        mockLastTrackData.put("lastFrameDropDuration", elapsed);
        mockLastTrackData.put("lastFrameDropTime", currentTime);
        mockLastTrackData.put("lastUpdateTime", currentTime);

        if (mockAggregationEnabled) {
            boolean hasActive = (boolean) mockAggregationStatus.get("hasActiveAggregation");
            int currentTotal = (int) mockAggregationStatus.get("totalLostFrames");
            int currentDuration = (int) mockAggregationStatus.get("totalLostFramesDuration");
            int eventCount = (int) mockAggregationStatus.get("eventCount");

            if (!hasActive) {
                // Start new aggregation
                mockAggregationStatus.put("hasActiveAggregation", true);
                mockAggregationStatus.put("totalLostFrames", count);
                mockAggregationStatus.put("totalLostFramesDuration", elapsed);
                mockAggregationStatus.put("eventCount", 1);
                mockAggregationStatus.put("firstDropTimestamp", currentTime);
                mockAggregationStatus.put("lastDropTimestamp", currentTime);
            } else {
                // Add to existing aggregation
                int newEventCount = eventCount + 1;
                mockAggregationStatus.put("totalLostFrames", currentTotal + count);
                mockAggregationStatus.put("totalLostFramesDuration", currentDuration + elapsed);
                mockAggregationStatus.put("eventCount", newEventCount);
                mockAggregationStatus.put("lastDropTimestamp", currentTime);

                if (newEventCount >= 50) {
                    simulateFlush();
                }
            }
            mockLastTrackData.put("currentTotalFrames", mockAggregationStatus.get("totalLostFrames"));
            mockLastTrackData.put("currentTotalDuration", mockAggregationStatus.get("totalLostFramesDuration"));
            mockLastTrackData.put("currentEventCount", mockAggregationStatus.get("eventCount"));
        } else {
            Map<String, Object> attributes = new ConcurrentHashMap<>();
            attributes.put("lostFrames", count);
            attributes.put("lostFramesDuration", elapsed);
            sendVideoEvent("CONTENT_DROPPED_FRAMES", attributes);
        }
    }

    public ConcurrentHashMap<String, Object> getLastTrackData() {
        return mockLastTrackData;
    }
    public Map<String, Object> getCurrentAggregationStatus() {
        return mockAggregationStatus;
    }
    @Override
    public void setDroppedFrameAggregationEnabled(boolean enabled) {
        if (!enabled && mockAggregationEnabled) {
            // Flush current aggregation when disabling
            simulateFlush();
        }
        this.mockAggregationEnabled = enabled;
    }
    @Override
    public boolean isDroppedFrameAggregationEnabled() {
        return mockAggregationEnabled;
    }
    public void resetAggregationState() {
        // Reset aggregation state but preserve last track data
        mockAggregationStatus.put("hasActiveAggregation", false);
        mockAggregationStatus.put("totalLostFrames", 0);
        mockAggregationStatus.put("totalLostFramesDuration", 0);
        mockAggregationStatus.put("eventCount", 0);
        mockAggregationStatus.put("firstDropTimestamp", 0L);
        mockAggregationStatus.put("lastDropTimestamp", 0L);

        // Reset event capture state - only clear if no event was just sent
        // This is needed for external resets, not after flushing
        eventSent = false;
        lastEventType = null;
        lastEventAttributes = null;
    }
    public void simulateFlush() {
        boolean hasActive = (boolean) mockAggregationStatus.get("hasActiveAggregation");
        if (hasActive) {
            Map<String, Object> attributes = new ConcurrentHashMap<>();
            attributes.put("lostFrames", mockAggregationStatus.get("totalLostFrames"));
            attributes.put("lostFramesDuration", mockAggregationStatus.get("totalLostFramesDuration"));
            attributes.put("eventCount", mockAggregationStatus.get("eventCount"));
            attributes.put("aggregationWindowMs", 5000L); // DEFAULT_AGGREGATION_WINDOW_MS
            attributes.put("firstDropTimestamp", mockAggregationStatus.get("firstDropTimestamp"));
            attributes.put("lastDropTimestamp", mockAggregationStatus.get("lastDropTimestamp"));

            long firstTime = (long) mockAggregationStatus.get("firstDropTimestamp");
            long lastTime = (long) mockAggregationStatus.get("lastDropTimestamp");
            attributes.put("actualAggregationDurationMs", lastTime - firstTime);

            sendVideoEvent("CONTENT_DROPPED_FRAMES", attributes);
            resetAggregationAfterFlush();
        }
    }
    private void resetAggregationAfterFlush() {
        mockAggregationStatus.put("hasActiveAggregation", false);
        mockAggregationStatus.put("totalLostFrames", 0);
        mockAggregationStatus.put("totalLostFramesDuration", 0);
        mockAggregationStatus.put("eventCount", 0);
        mockAggregationStatus.put("firstDropTimestamp", 0L);
        mockAggregationStatus.put("lastDropTimestamp", 0L);
        // DON'T reset eventSent flag here - we want to preserve it after flushing
    }
}