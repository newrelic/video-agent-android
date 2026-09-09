package com.newrelic.videoagent.theoplayer.tracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.Map;

import static com.newrelic.videoagent.core.NRDef.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class NRTrackerTHEOPlayerTest {

    private NRTrackerTHEOPlayer tracker;

    @Before
    public void setUp() {
        tracker = new NRTrackerTHEOPlayer();
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Test
    public void getPlayerName_returnsTheoPlayer() {
        assertEquals("THEOplayer", tracker.getPlayerName());
    }

    @Test
    public void getTrackerName_returnsTheoPlayerTracker() {
        assertEquals("THEOplayerTracker", tracker.getTrackerName());
    }

    // ── setPlayer type guard ──────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void setPlayer_withArbitraryObject_throwsIllegalArgumentException() {
        tracker.setPlayer(new Object());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setPlayer_withNull_throwsIllegalArgumentException() {
        tracker.setPlayer(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setPlayer_withString_throwsIllegalArgumentException() {
        tracker.setPlayer("rtsp://stream.example.com/live");
    }

    // ── getAttributes — shift field only on CONTENT_RENDITION_CHANGE ─────────

    @Test
    public void getAttributes_contentRenditionChange_includesShiftKey() {
        setRenditionShift(tracker, "up");
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_RENDITION_CHANGE, null);
        assertTrue("CONTENT_RENDITION_CHANGE must include 'shift'", attrs.containsKey("shift"));
        assertEquals("shift value must be 'up'", "up", attrs.get("shift"));
    }

    @Test
    public void getAttributes_contentStart_doesNotIncludeShiftKey() {
        setRenditionShift(tracker, "up");   // shift is set — it should still be absent for other events
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);
        assertFalse("CONTENT_START must NOT include 'shift'", attrs.containsKey("shift"));
    }

    @Test
    public void getAttributes_contentEnd_doesNotIncludeShiftKey() {
        setRenditionShift(tracker, "down");
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_END, null);
        assertFalse("CONTENT_END must NOT include 'shift'", attrs.containsKey("shift"));
    }

    // ── getAttributes — contentPlayrate always present ────────────────────────

    @Test
    public void getAttributes_alwaysIncludesContentPlayrateKey() {
        // player is null so getPlayrate() returns null — the key must still be present
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);
        assertTrue("contentPlayrate must always be present in every event", attrs.containsKey("contentPlayrate"));
    }

    @Test
    public void getAttributes_renditionChange_alsoIncludesContentPlayrateKey() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_RENDITION_CHANGE, null);
        assertTrue("contentPlayrate must also be present on CONTENT_RENDITION_CHANGE", attrs.containsKey("contentPlayrate"));
    }

    // ── getDuration null-guard ────────────────────────────────────────────────

    @Test
    public void getDuration_whenPlayerIsNull_returnsNull() {
        // No player attached — must not throw, must return null
        assertNull(tracker.getDuration());
    }

    // ── getTitle null-guard ───────────────────────────────────────────────────

    @Test
    public void getTitle_whenPlayerIsNull_returnsNull() {
        assertNull(tracker.getTitle());
    }

    // ── getRenditionChangeShift initial value ─────────────────────────────────

    @Test
    public void getRenditionChangeShift_initiallyNull() {
        assertNull("Rendition shift must start null — no change has occurred yet",
                tracker.getRenditionChangeShift());
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private static void setRenditionShift(NRTrackerTHEOPlayer tracker, String shift) {
        try {
            Field f = NRTrackerTHEOPlayer.class.getDeclaredField("renditionChangeShift");
            f.setAccessible(true);
            f.set(tracker, shift);
        } catch (Exception e) {
            throw new RuntimeException("Could not set renditionChangeShift via reflection", e);
        }
    }
}
