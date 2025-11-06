package com.newrelic.videoagent.exoplayer.tracker;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import androidx.media3.exoplayer.ExoPlayer;
import android.content.Context;
import org.robolectric.RuntimeEnvironment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive unit tests for NRTrackerExoPlayer frame drop aggregation functionality.
 *
 * This test class covers:
 * - Core aggregation logic (single/multiple frame drops)
 * - "Last track always" pattern verification
 * - Configuration management (enable/disable aggregation)
 * - Event attributes validation
 * - State management and reset functionality
 * - Data consistency verification
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class NRTrackerExoPlayerFrameDropAggregationTest {

    private MockNRTrackerExoPlayer tracker;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        ExoPlayer realPlayer = new ExoPlayer.Builder(context).build();

        tracker = new MockNRTrackerExoPlayer();
        tracker.setPlayer(realPlayer);
        tracker.setDroppedFrameAggregationEnabled(true);
    }

    @Test
    public void testSingleFrameDropEvent() {
        tracker.sendDroppedFrame(5, 100);

        ConcurrentHashMap<String, Object> lastTrackData = tracker.getLastTrackData();

        assertEquals("Last frame drop count should match", 5, lastTrackData.get("lastFrameDropCount"));
        assertEquals("Last frame drop duration should match", 100, lastTrackData.get("lastFrameDropDuration"));
        assertNotNull("Last frame drop time should be set", lastTrackData.get("lastFrameDropTime"));
        assertNotNull("Last update time should be set", lastTrackData.get("lastUpdateTime"));

        // Verify aggregation state
        assertEquals("Current total frames should match", 5, lastTrackData.get("currentTotalFrames"));
        assertEquals("Current total duration should match", 100, lastTrackData.get("currentTotalDuration"));
        assertEquals("Current event count should be 1", 1, lastTrackData.get("currentEventCount"));
    }

    @Test
    public void testMultipleFrameDropAggregation() {
        tracker.sendDroppedFrame(5, 100);
        tracker.sendDroppedFrame(3, 50);
        tracker.sendDroppedFrame(2, 25);

        Map<String, Object> status = tracker.getCurrentAggregationStatus();

        assertEquals("Should have active aggregation", true, status.get("hasActiveAggregation"));
        assertEquals("Total frames should be sum", 10, status.get("totalLostFrames")); // 5+3+2
        assertEquals("Total duration should be sum", 175, status.get("totalLostFramesDuration")); // 100+50+25
        assertEquals("Event count should be 3", 3, status.get("eventCount"));

        ConcurrentHashMap<String, Object> lastTrack = tracker.getLastTrackData();
        assertEquals("Last event count should be latest", 2, lastTrack.get("lastFrameDropCount"));
        assertEquals("Last event duration should be latest", 25, lastTrack.get("lastFrameDropDuration"));
        assertEquals("Aggregated total should be maintained", 10, lastTrack.get("currentTotalFrames"));
    }

    @Test
    public void testAggregationWindowExpiry() throws InterruptedException {
        tracker.sendDroppedFrame(5, 100);
        tracker.simulateTimeExpiry();
        tracker.sendDroppedFrame(3, 50);

        Map<String, Object> status = tracker.getCurrentAggregationStatus();
        assertEquals("Should start new aggregation window", 3, status.get("totalLostFrames"));
        assertEquals("Event count should reset to 1", 1, status.get("eventCount"));

        assertTrue("Should have sent aggregated event", tracker.wasEventSent());
        assertEquals("Should send content dropped frames event", "CONTENT_DROPPED_FRAMES", tracker.getLastEventType());

        Map<String, Object> eventAttrs = tracker.getLastEventAttributes();
        assertEquals("Event should contain aggregated frames", 5, eventAttrs.get("lostFrames"));
        assertEquals("Event should contain aggregated duration", 100, eventAttrs.get("lostFramesDuration"));
        assertEquals("Event should contain event count", 1, eventAttrs.get("eventCount"));
    }

    @Test
    public void testMaxEventsFlushTrigger() {
        for (int i = 0; i < 50; i++) { // MAX_EVENTS_PER_AGGREGATE = 50
            tracker.sendDroppedFrame(1, 10);
        }

        assertTrue("Should flush when max events reached", tracker.wasEventSent());

        tracker.clearEvents();
        tracker.sendDroppedFrame(2, 20);

        Map<String, Object> status = tracker.getCurrentAggregationStatus();
        assertEquals("Should start new window", 2, status.get("totalLostFrames"));
        assertEquals("Event count should reset", 1, status.get("eventCount"));
        assertFalse("Should not send immediate event in aggregation mode", tracker.wasEventSent());
    }

    @Test
    public void testLastTrackAlwaysAccessible() {
        tracker.sendDroppedFrame(10, 200);

        ConcurrentHashMap<String, Object> data1 = tracker.getLastTrackData();
        assertNotNull("Data should never be null", data1);
        assertEquals("Should contain latest frame drop count", 10, data1.get("lastFrameDropCount"));

        tracker.sendDroppedFrame(5, 100);

        ConcurrentHashMap<String, Object> data2 = tracker.getLastTrackData();
        assertEquals("Should update to latest frame drop", 5, data2.get("lastFrameDropCount"));
        assertEquals("Should maintain aggregated total", 15, data2.get("currentTotalFrames"));
        assertSame("Should return same ConcurrentHashMap instance", data1, data2);
    }

    @Test
    public void testDataPreservationAcrossStates() {
        tracker.sendDroppedFrame(7, 140);

        ConcurrentHashMap<String, Object> beforeDisable = tracker.getLastTrackData();
        assertEquals("Should have data before disable", 7, beforeDisable.get("lastFrameDropCount"));

        tracker.setDroppedFrameAggregationEnabled(false);

        ConcurrentHashMap<String, Object> afterDisable = tracker.getLastTrackData();
        assertNotNull("Data should be preserved after disable", afterDisable);
        assertEquals("Frame drop count should be preserved", 7, afterDisable.get("lastFrameDropCount"));

        tracker.setDroppedFrameAggregationEnabled(true);

        ConcurrentHashMap<String, Object> afterEnable = tracker.getLastTrackData();
        assertEquals("Data should persist after re-enable", 7, afterEnable.get("lastFrameDropCount"));
    }

    @Test
    public void testAggregationToggle() {
        assertTrue("Aggregation should be enabled by default", tracker.isDroppedFrameAggregationEnabled());

        tracker.sendDroppedFrame(5, 100);
        tracker.setDroppedFrameAggregationEnabled(false);

        assertTrue("Should flush pending events when disabled", tracker.wasEventSent());

        tracker.clearEvents();
        tracker.sendDroppedFrame(3, 50);

        assertTrue("Should send immediate event when disabled", tracker.wasEventSent());

        Map<String, Object> eventAttrs = tracker.getLastEventAttributes();
        assertEquals("Should send individual frame count", 3, eventAttrs.get("lostFrames"));
        assertEquals("Should send individual duration", 50, eventAttrs.get("lostFramesDuration"));
        assertNull("Should not have eventCount in immediate mode", eventAttrs.get("eventCount"));
    }

    @Test
    public void testAggregationReEnable() {
        tracker.setDroppedFrameAggregationEnabled(false);
        tracker.sendDroppedFrame(2, 40); // Immediate mode

        tracker.clearEvents();
        tracker.setDroppedFrameAggregationEnabled(true);

        tracker.sendDroppedFrame(4, 80);
        tracker.sendDroppedFrame(3, 60);

        Map<String, Object> status = tracker.getCurrentAggregationStatus();
        assertEquals("Should aggregate after re-enable", 7, status.get("totalLostFrames"));
        assertEquals("Should count events after re-enable", 2, status.get("eventCount"));
    }

    @Test
    public void testEventAttributesCompleteness() {
        tracker.sendDroppedFrame(8, 160);
        tracker.sendDroppedFrame(4, 80);
        tracker.simulateFlush();

        Map<String, Object> attrs = tracker.getLastEventAttributes();

        assertEquals("Should contain total lost frames", 12, attrs.get("lostFrames"));
        assertEquals("Should contain total duration", 240, attrs.get("lostFramesDuration"));
        assertEquals("Should contain event count", 2, attrs.get("eventCount"));
        assertNotNull("Should contain aggregation window", attrs.get("aggregationWindowMs"));
        assertNotNull("Should contain first drop timestamp", attrs.get("firstDropTimestamp"));
        assertNotNull("Should contain last drop timestamp", attrs.get("lastDropTimestamp"));
        assertNotNull("Should contain actual duration", attrs.get("actualAggregationDurationMs"));
    }

    @Test
    public void testStateReset() {
        tracker.sendDroppedFrame(10, 200);
        tracker.sendDroppedFrame(5, 100);

        Map<String, Object> status = tracker.getCurrentAggregationStatus();
        assertEquals("Should have active aggregation", true, status.get("hasActiveAggregation"));
        assertEquals("Should have aggregated frames", 15, status.get("totalLostFrames"));

        tracker.resetAggregationState();

        status = tracker.getCurrentAggregationStatus();
        assertEquals("Should clear active aggregation", false, status.get("hasActiveAggregation"));
        assertEquals("Should reset total frames", 0, status.get("totalLostFrames"));
        assertEquals("Should reset event count", 0, status.get("eventCount"));
        assertEquals("Should reset timestamps", 0L, status.get("firstDropTimestamp"));

        ConcurrentHashMap<String, Object> lastTrack = tracker.getLastTrackData();
        assertEquals("Should preserve last frame drop count", 5, lastTrack.get("lastFrameDropCount"));
    }

    @Test
    public void testBasicConcurrentAccess() {
        tracker.sendDroppedFrame(5, 100);

        ConcurrentHashMap<String, Object> data = tracker.getLastTrackData();
        assertNotNull("Data should always be accessible", data);

        Map<String, Object> status = tracker.getCurrentAggregationStatus();
        assertNotNull("Status should always be accessible", status);

        assertEquals("Should maintain data consistency", 5, data.get("lastFrameDropCount"));
        assertEquals("Should maintain status consistency", 5, status.get("totalLostFrames"));
    }

    @Test
    public void testDataConsistency() {
        tracker.sendDroppedFrame(8, 160);
        tracker.sendDroppedFrame(3, 60);

        Map<String, Object> status = tracker.getCurrentAggregationStatus();
        ConcurrentHashMap<String, Object> lastTrack = tracker.getLastTrackData();

        assertEquals("Status should show total frames", 11, status.get("totalLostFrames"));
        assertEquals("Last track should show current total", 11, lastTrack.get("currentTotalFrames"));
        assertEquals("Last track should show latest individual event", 3, lastTrack.get("lastFrameDropCount"));
    }

}