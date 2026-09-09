package com.newrelic.videoagent.theoplayer.tracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.newrelic.videoagent.core.NRDef.*;
import static org.junit.Assert.*;

/**
 * Tests for the named event-handler methods in NRTrackerTHEOPlayer.
 *
 * Handlers are invoked directly — no real THEOplayerView or mocked player events
 * needed. MockNRTrackerTHEOPlayer captures sendVideoEvent() calls so each test
 * asserts on emitted NR event types, not just internal state.
 *
 * Covers two reviewer gaps:
 *   1. Regression test for the WAITING seek guard (handleWaiting(boolean)).
 *   2. Lifecycle event → NR action mapping (SOURCE_CHANGE → PLAY → PAUSE → SEEK → END).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class NRTrackerTHEOPlayerEventTest {

    private MockNRTrackerTHEOPlayer tracker;

    @Before
    public void setUp() {
        tracker = new MockNRTrackerTHEOPlayer();
    }

    // ── handleWaiting seek guard (reviewer point 1) ───────────────────────────

    @Test
    public void handleWaiting_notSeeking_emitsContentBufferStart() {
        tracker.handleSourceChange();       // state: isRequested=true (required by goBufferStart)
        tracker.clearEvents();

        tracker.handleWaiting(false);

        assertTrue("WAITING while not seeking must emit CONTENT_BUFFER_START",
                tracker.wasEventSent(CONTENT_BUFFER_START));
    }

    @Test
    public void handleWaiting_seeking_doesNotEmitContentBufferStart() {
        tracker.handleSourceChange();
        tracker.clearEvents();

        tracker.handleWaiting(true);        // guard must suppress the event

        assertFalse("WAITING during seek must NOT emit CONTENT_BUFFER_START",
                tracker.wasEventSent(CONTENT_BUFFER_START));
        assertTrue("No events at all should be emitted during seeking WAITING",
                tracker.getSentEvents().isEmpty());
    }

    // ── Lifecycle event → NR action mapping (reviewer point 2) ───────────────

    @Test
    public void handleSourceChange_emitsContentRequest() {
        tracker.handleSourceChange();
        assertTrue(tracker.wasEventSent(CONTENT_REQUEST));
    }

    @Test
    public void handlePlaying_firstTime_emitsContentStart() {
        tracker.handleSourceChange();       // isRequested=true
        tracker.clearEvents();

        tracker.handlePlaying();

        assertTrue("First PLAYING must emit CONTENT_START", tracker.wasEventSent(CONTENT_START));
    }

    @Test
    public void handlePlaying_withPendingBuffer_emitsBufferEndThenStart() {
        tracker.handleSourceChange();
        tracker.handleWaiting(false);       // CONTENT_BUFFER_START (initial buffer)
        tracker.clearEvents();

        tracker.handlePlaying();            // isBuffering=true → CONTENT_BUFFER_END, then CONTENT_START

        assertEquals("CONTENT_BUFFER_END must precede CONTENT_START",
                CONTENT_BUFFER_END, tracker.getSentEvents().get(0));
        assertEquals(CONTENT_START, tracker.getSentEvents().get(1));
    }

    @Test
    public void handlePause_afterStart_emitsContentPause() {
        tracker.handleSourceChange();
        tracker.handlePlaying();
        tracker.clearEvents();

        tracker.handlePause();

        assertTrue(tracker.wasEventSent(CONTENT_PAUSE));
    }

    @Test
    public void handlePlay_afterPause_emitsContentResume() {
        tracker.handleSourceChange();
        tracker.handlePlaying();
        tracker.handlePause();
        tracker.clearEvents();

        tracker.handlePlay();

        assertTrue(tracker.wasEventSent(CONTENT_RESUME));
    }

    @Test
    public void handleSeeking_afterStart_emitsContentSeekStart() {
        tracker.handleSourceChange();
        tracker.handlePlaying();
        tracker.clearEvents();

        tracker.handleSeeking();

        assertTrue(tracker.wasEventSent(CONTENT_SEEK_START));
    }

    @Test
    public void handleSeeked_afterSeeking_emitsContentSeekEnd() {
        tracker.handleSourceChange();
        tracker.handlePlaying();
        tracker.handleSeeking();
        tracker.clearEvents();

        tracker.handleSeeked();

        assertTrue(tracker.wasEventSent(CONTENT_SEEK_END));
    }

    @Test
    public void handleSeeked_withOpenBuffer_emitsBufferEndBeforeSeekEnd() {
        // WAITING fires during seek (without guard, this would happen); simulate the
        // edge case where a buffer is open at SEEKED time.
        tracker.handleSourceChange();
        tracker.handlePlaying();
        tracker.handleSeeking();
        tracker.handleWaiting(false);       // force a buffer open during seek
        tracker.clearEvents();

        tracker.handleSeeked();

        assertEquals("CONTENT_BUFFER_END must precede CONTENT_SEEK_END",
                CONTENT_BUFFER_END, tracker.getSentEvents().get(0));
        assertEquals(CONTENT_SEEK_END, tracker.getSentEvents().get(1));
    }

    @Test
    public void handleEnded_afterStart_emitsContentEnd() {
        tracker.handleSourceChange();
        tracker.handlePlaying();
        tracker.clearEvents();

        tracker.handleEnded();

        assertTrue(tracker.wasEventSent(CONTENT_END));
    }

    // ── Full lifecycle sequence ───────────────────────────────────────────────

    @Test
    public void fullLifecycle_sourceChangeThroughEnd_emitsExpectedEventSequence() {
        tracker.handleSourceChange();
        assertTrue("SOURCE_CHANGE → CONTENT_REQUEST", tracker.wasEventSent(CONTENT_REQUEST));

        tracker.handlePlaying();
        assertTrue("PLAYING → CONTENT_START", tracker.wasEventSent(CONTENT_START));

        tracker.clearEvents();
        tracker.handlePause();
        assertTrue("PAUSE → CONTENT_PAUSE", tracker.wasEventSent(CONTENT_PAUSE));

        tracker.clearEvents();
        tracker.handlePlay();
        assertTrue("PLAY (after pause) → CONTENT_RESUME", tracker.wasEventSent(CONTENT_RESUME));

        tracker.clearEvents();
        tracker.handleSeeking();
        assertTrue("SEEKING → CONTENT_SEEK_START", tracker.wasEventSent(CONTENT_SEEK_START));

        tracker.clearEvents();
        tracker.handleSeeked();
        assertTrue("SEEKED → CONTENT_SEEK_END", tracker.wasEventSent(CONTENT_SEEK_END));

        tracker.clearEvents();
        tracker.handleEnded();
        assertTrue("ENDED → CONTENT_END", tracker.wasEventSent(CONTENT_END));
    }

    // ── handleActiveQualityChanged shift direction ────────────────────────────

    @Test
    public void handleActiveQualityChanged_largerArea_setsShiftUp() {
        tracker.handleSourceChange();
        tracker.handlePlaying();
        tracker.handleActiveQualityChanged(1280, 720);  // establish baseline
        tracker.clearEvents();

        tracker.handleActiveQualityChanged(1920, 1080); // upgrade

        assertEquals("Larger area must set shift to 'up'", "up", tracker.getRenditionChangeShift());
        assertTrue(tracker.wasEventSent(CONTENT_RENDITION_CHANGE));
    }

    @Test
    public void handleActiveQualityChanged_smallerArea_setsShiftDown() {
        tracker.handleSourceChange();
        tracker.handlePlaying();
        tracker.handleActiveQualityChanged(1920, 1080); // establish baseline
        tracker.clearEvents();

        tracker.handleActiveQualityChanged(640, 360);   // downgrade

        assertEquals("Smaller area must set shift to 'down'", "down", tracker.getRenditionChangeShift());
        assertTrue(tracker.wasEventSent(CONTENT_RENDITION_CHANGE));
    }

    @Test
    public void handleActiveQualityChanged_sameArea_doesNotEmitRenditionChange() {
        tracker.handleSourceChange();
        tracker.handlePlaying();
        tracker.handleActiveQualityChanged(1280, 720);
        tracker.clearEvents();

        tracker.handleActiveQualityChanged(1280, 720);  // identical dimensions

        assertFalse("Same area must not emit CONTENT_RENDITION_CHANGE",
                tracker.wasEventSent(CONTENT_RENDITION_CHANGE));
    }
}
