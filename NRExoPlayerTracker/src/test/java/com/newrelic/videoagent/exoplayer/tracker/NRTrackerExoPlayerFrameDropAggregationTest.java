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
        // Clear TRACKER_READY / PLAYER_READY events fired during setup
        tracker.clearEvents();
    }

    // -------------------------------------------------------------------------
    // Single-event payload shape (eventCount=1)
    // -------------------------------------------------------------------------

    @Test
    public void singleEvent_payloadContainsCoreFields() {
        tracker.sendDroppedFrame(50, 1800);
        tracker.simulateFlush();

        Map<String, Object> attrs = tracker.getLastEventAttributes();
        assertNotNull("Event should have been emitted", attrs);
        assertEquals("lostFrames should match", 50, attrs.get("lostFrames"));
        assertEquals("lostFramesDuration should match", 1800, attrs.get("lostFramesDuration"));
        assertEquals("eventCount should be 1", 1, attrs.get("eventCount"));
    }

    @Test
    public void singleEvent_timingFieldsAbsent() {
        tracker.sendDroppedFrame(50, 1800);
        tracker.simulateFlush();

        Map<String, Object> attrs = tracker.getLastEventAttributes();
        assertNull("firstDropTimestamp must be absent when eventCount=1", attrs.get("firstDropTimestamp"));
        assertNull("lastDropTimestamp must be absent when eventCount=1", attrs.get("lastDropTimestamp"));
        assertNull("actualAggregationDurationMs must be absent when eventCount=1", attrs.get("actualAggregationDurationMs"));
    }

    @Test
    public void singleEvent_aggregationWindowMsNeverPresent() {
        tracker.sendDroppedFrame(50, 1800);
        tracker.simulateFlush();

        assertNull("aggregationWindowMs must never appear in the event",
                tracker.getLastEventAttributes().get("aggregationWindowMs"));
    }

    // -------------------------------------------------------------------------
    // Multi-event payload shape (eventCount > 1)
    // -------------------------------------------------------------------------

    @Test
    public void multipleEvents_framesAndDurationAreSummed() {
        tracker.sendDroppedFrame(5, 100);
        tracker.sendDroppedFrame(3, 50);
        tracker.sendDroppedFrame(2, 25);
        tracker.simulateFlush();

        Map<String, Object> attrs = tracker.getLastEventAttributes();
        assertEquals("lostFrames should be sum of all callbacks", 10, attrs.get("lostFrames"));
        assertEquals("lostFramesDuration should be sum of all elapsed windows", 175, attrs.get("lostFramesDuration"));
        assertEquals("eventCount should be 3", 3, attrs.get("eventCount"));
    }

    @Test
    public void multipleEvents_timingFieldsPresent() {
        tracker.sendDroppedFrame(8, 160);
        tracker.sendDroppedFrame(4, 80);
        tracker.simulateFlush();

        Map<String, Object> attrs = tracker.getLastEventAttributes();
        assertNotNull("firstDropTimestamp must be present when eventCount > 1", attrs.get("firstDropTimestamp"));
        assertNotNull("lastDropTimestamp must be present when eventCount > 1", attrs.get("lastDropTimestamp"));
        assertNotNull("actualAggregationDurationMs must be present when eventCount > 1", attrs.get("actualAggregationDurationMs"));
    }

    @Test
    public void multipleEvents_actualAggregationDurationMsIsNonNegative() {
        tracker.sendDroppedFrame(8, 160);
        tracker.sendDroppedFrame(4, 80);
        tracker.simulateFlush();

        long duration = (long) tracker.getLastEventAttributes().get("actualAggregationDurationMs");
        assertTrue("actualAggregationDurationMs must be >= 0", duration >= 0);
    }

    @Test
    public void multipleEvents_aggregationWindowMsStillAbsent() {
        tracker.sendDroppedFrame(8, 160);
        tracker.sendDroppedFrame(4, 80);
        tracker.simulateFlush();

        assertNull("aggregationWindowMs must never appear in the event",
                tracker.getLastEventAttributes().get("aggregationWindowMs"));
    }

    // -------------------------------------------------------------------------
    // Flush triggers
    // -------------------------------------------------------------------------

    @Test
    public void maxEventsThreshold_triggersFlushAtFiftyCallbacks() {
        // Production checks isMaxEventsReached() before processing each call.
        // 50 calls accumulate eventCount=50; the 51st call sees count>=50 and flushes.
        for (int i = 0; i < 51; i++) {
            tracker.sendDroppedFrame(1, 10);
        }

        assertTrue("Reaching MAX_EVENTS_PER_AGGREGATE should trigger an automatic flush",
                tracker.wasEventSent());
        assertEquals("CONTENT_DROPPED_FRAMES", tracker.getLastEventType());
    }

    @Test
    public void afterMaxEventsFlush_nextCallbackStartsFreshWindow() {
        for (int i = 0; i < 50; i++) {
            tracker.sendDroppedFrame(1, 10);
        }
        tracker.clearEvents();

        tracker.sendDroppedFrame(7, 70);
        tracker.simulateFlush();

        Map<String, Object> attrs = tracker.getLastEventAttributes();
        assertEquals("New window should start fresh after flush", 7, attrs.get("lostFrames"));
        assertEquals("eventCount should reset to 1", 1, attrs.get("eventCount"));
    }

    @Test
    public void explicitFlush_emitsEventAndResetState() {
        tracker.sendDroppedFrame(5, 100);
        tracker.simulateFlush();

        assertTrue(tracker.wasEventSent());
        assertEquals("CONTENT_DROPPED_FRAMES", tracker.getLastEventType());

        tracker.clearEvents();
        tracker.simulateFlush();
        assertFalse("Flush on empty window should not emit an event", tracker.wasEventSent());
    }

    // -------------------------------------------------------------------------
    // Aggregation toggle
    // -------------------------------------------------------------------------

    @Test
    public void aggregationEnabled_byDefault() {
        assertTrue(tracker.isDroppedFrameAggregationEnabled());
    }

    @Test
    public void disablingAggregation_flushesPendingEvents() {
        tracker.sendDroppedFrame(5, 100);
        tracker.setDroppedFrameAggregationEnabled(false);

        assertTrue("Disabling aggregation should flush pending events", tracker.wasEventSent());
    }

    @Test
    public void immediateMode_sendsEventPerCallback() {
        tracker.setDroppedFrameAggregationEnabled(false);
        tracker.sendDroppedFrame(3, 50);

        assertTrue(tracker.wasEventSent());
        Map<String, Object> attrs = tracker.getLastEventAttributes();
        assertEquals(3, attrs.get("lostFrames"));
        assertEquals(50, attrs.get("lostFramesDuration"));
        assertNull("eventCount must be absent in immediate mode", attrs.get("eventCount"));
    }

    @Test
    public void reEnablingAggregation_aggregatesSubsequentEvents() {
        tracker.setDroppedFrameAggregationEnabled(false);
        tracker.sendDroppedFrame(2, 40);
        tracker.clearEvents();

        tracker.setDroppedFrameAggregationEnabled(true);
        tracker.sendDroppedFrame(4, 80);
        tracker.sendDroppedFrame(3, 60);
        tracker.simulateFlush();

        Map<String, Object> attrs = tracker.getLastEventAttributes();
        assertEquals("Frames should be aggregated after re-enable", 7, attrs.get("lostFrames"));
        assertEquals("eventCount should reflect two callbacks", 2, attrs.get("eventCount"));
    }
}
