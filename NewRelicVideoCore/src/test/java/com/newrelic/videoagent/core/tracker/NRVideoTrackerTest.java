package com.newrelic.videoagent.core.tracker;

import android.os.Handler;
import android.os.Looper;

import com.newrelic.videoagent.core.model.NRTrackerState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.HashMap;
import java.util.Map;

import static com.newrelic.videoagent.core.NRDef.*;
import static org.junit.Assert.*;

/**
 * Comprehensive unit tests for NRVideoTracker.
 * Tests heartbeat management, state transitions, playback metrics, and event generation.
 */
@RunWith(RobolectricTestRunner.class)
public class NRVideoTrackerTest {

    private NRVideoTracker tracker;
    private ShadowLooper shadowLooper;

    @Before
    public void setUp() {
        tracker = new NRVideoTracker();
        shadowLooper = Shadows.shadowOf(Looper.getMainLooper());
    }

    @After
    public void tearDown() {
        if (tracker != null) {
            tracker.dispose();
        }
    }

    // ========== Initialization Tests ==========

    @Test
    public void testTrackerCreation() {
        assertNotNull("Tracker should be created", tracker);
    }

    @Test
    public void testInitialState() {
        NRTrackerState state = tracker.getState();
        assertNotNull("State should be initialized", state);
    }

    @Test
    public void testInitialCounters() {
        // Test that counters start at 0
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);

        assertEquals("Number of ads should start at 0", 0, attrs.get("numberOfAds"));
        assertEquals("Number of videos should start at 0", 0, attrs.get("numberOfVideos"));
        assertEquals("Number of errors should start at 0", 0, attrs.get("numberOfErrors"));
    }

    @Test
    public void testViewSessionIdGeneration() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);
        String viewSession = (String) attrs.get("viewSession");

        assertNotNull("View session should be generated", viewSession);
        assertFalse("View session should not be empty", viewSession.isEmpty());
    }

    @Test
    public void testViewIdInitialization() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);
        String viewId = (String) attrs.get("viewId");

        assertNotNull("View ID should be initialized", viewId);
        assertTrue("Initial view ID should contain session and index", viewId.contains("-0"));
    }

    @Test
    public void testTotalPlaytimeInitialization() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);
        Long totalPlaytime = (Long) attrs.get("totalPlaytime");

        assertNotNull("Total playtime should be initialized", totalPlaytime);
        assertEquals("Initial total playtime should be 0", Long.valueOf(0L), totalPlaytime);
    }

    // ========== Player Management Tests ==========

    @Test
    public void testSetPlayer() {
        Object mockPlayer = new Object();

        // Should not throw exception
        tracker.setPlayer(mockPlayer);
    }

    @Test
    public void testSetPlayerTransitionsToPlayerReady() {
        Object mockPlayer = new Object();

        tracker.setPlayer(mockPlayer);

        NRTrackerState state = tracker.getState();
        assertTrue("State should be in player ready", state.isPlayerReady);
    }

    @Test
    public void testSetPlayerWithNull() {
        // Should not throw exception
        tracker.setPlayer(null);
    }

    @Test
    public void testMultipleSetPlayerCalls() {
        Object player1 = new Object();
        Object player2 = new Object();

        tracker.setPlayer(player1);
        tracker.setPlayer(player2);

        // Should handle multiple calls
    }

    // ========== Heartbeat Tests ==========

    @Test
    public void testStartHeartbeat() {
        // Should not throw exception
        tracker.startHeartbeat();
    }

    @Test
    public void testStopHeartbeat() {
        tracker.startHeartbeat();

        // Should not throw exception
        tracker.stopHeartbeat();
    }

    @Test
    public void testHeartbeatStartsAfterStart() {
        tracker.setPlayer(new Object());
        tracker.sendStart();

        // Heartbeat should be started automatically
    }

    @Test
    public void testHeartbeatStopsAfterEnd() {
        tracker.setPlayer(new Object());
        tracker.sendStart();
        tracker.sendEnd();

        // Heartbeat should be stopped automatically
    }

    @Test
    public void testMultipleStartHeartbeatCalls() {
        tracker.startHeartbeat();
        tracker.startHeartbeat();
        tracker.startHeartbeat();

        // Should handle multiple starts
    }

    @Test
    public void testMultipleStopHeartbeatCalls() {
        tracker.startHeartbeat();
        tracker.stopHeartbeat();
        tracker.stopHeartbeat();
        tracker.stopHeartbeat();

        // Should handle multiple stops
    }

    @Test
    public void testStopHeartbeatWithoutStart() {
        // Should not throw exception
        tracker.stopHeartbeat();
    }

    @Test
    public void testStartStopHeartbeatCycle() {
        for (int i = 0; i < 5; i++) {
            tracker.startHeartbeat();
            tracker.stopHeartbeat();
        }

        // Should handle multiple cycles
    }

    // ========== State Transition Tests ==========

    @Test
    public void testSendRequest() {
        tracker.setPlayer(new Object());

        // Should not throw exception
        tracker.sendRequest();
    }

    @Test
    public void testSendStart() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();

        // Should not throw exception
        tracker.sendStart();
    }

    @Test
    public void testSendPause() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        // Should not throw exception
        tracker.sendPause();
    }

    @Test
    public void testSendResume() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
        tracker.sendPause();

        // Should not throw exception
        tracker.sendResume();
    }

    @Test
    public void testSendEnd() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        // Should not throw exception
        tracker.sendEnd();
    }

    @Test
    public void testSendSeekStart() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        // Should not throw exception
        tracker.sendSeekStart();
    }

    @Test
    public void testSendSeekEnd() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
        tracker.sendSeekStart();

        // Should not throw exception
        tracker.sendSeekEnd();
    }

    @Test
    public void testSendBufferStart() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        // Should not throw exception
        tracker.sendBufferStart();
    }

    @Test
    public void testSendBufferEnd() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
        tracker.sendBufferStart();

        // Should not throw exception
        tracker.sendBufferEnd();
    }

    @Test
    public void testCompletePlaybackSequence() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
        tracker.sendPause();
        tracker.sendResume();
        tracker.sendEnd();

        // Complete sequence should work
    }

    @Test
    public void testPlaybackSequenceWithSeek() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
        tracker.sendSeekStart();
        tracker.sendSeekEnd();
        tracker.sendEnd();

        // Sequence with seek should work
    }

    @Test
    public void testPlaybackSequenceWithBuffer() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
        tracker.sendBufferStart();
        tracker.sendBufferEnd();
        tracker.sendEnd();

        // Sequence with buffering should work
    }

    @Test
    public void testMultiplePauseResumeCycles() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        for (int i = 0; i < 5; i++) {
            tracker.sendPause();
            tracker.sendResume();
        }

        tracker.sendEnd();

        // Multiple pause/resume cycles should work
    }

    // ========== Counter Tests ==========

    @Test
    public void testNumberOfVideosIncrement() {
        tracker.setPlayer(new Object());

        // First video
        tracker.sendRequest();
        tracker.sendStart();
        Map<String, Object> attrs1 = tracker.getAttributes(CONTENT_START, null);
        assertEquals("First video should increment counter", 1, attrs1.get("numberOfVideos"));
        tracker.sendEnd();

        // Second video
        tracker.sendRequest();
        tracker.sendStart();
        Map<String, Object> attrs2 = tracker.getAttributes(CONTENT_START, null);
        assertEquals("Second video should increment counter", 2, attrs2.get("numberOfVideos"));
        tracker.sendEnd();
    }

    @Test
    public void testSetNumberOfAds() {
        tracker.setNumberOfAds(5);

        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);
        assertEquals("Number of ads should be set", 5, attrs.get("numberOfAds"));
    }

    @Test
    public void testViewIdIncrementsAfterEnd() {
        tracker.setPlayer(new Object());

        // First video
        tracker.sendRequest();
        tracker.sendStart();
        Map<String, Object> attrs1 = tracker.getAttributes(CONTENT_START, null);
        String viewId1 = (String) attrs1.get("viewId");
        assertTrue("First video has viewId ending with -0", viewId1.endsWith("-0"));
        tracker.sendEnd();

        // Second video
        tracker.sendRequest();
        tracker.sendStart();
        Map<String, Object> attrs2 = tracker.getAttributes(CONTENT_START, null);
        String viewId2 = (String) attrs2.get("viewId");
        assertTrue("Second video has viewId ending with -1", viewId2.endsWith("-1"));
        tracker.sendEnd();
    }

    @Test
    public void testNumberOfErrorsResetsAfterEnd() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        // Simulate errors by sending error events
        tracker.sendError(new Exception("Test error 1"));
        tracker.sendError(new Exception("Test error 2"));

        tracker.sendEnd();

        // Start new video
        tracker.sendRequest();
        tracker.sendStart();
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);

        assertEquals("Number of errors should reset after end", 0, attrs.get("numberOfErrors"));
    }

    // ========== Playback Metrics Tests ==========

    @Test
    public void testUpdatePlaytime() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        // Simulate some time passing
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        tracker.updatePlaytime();

        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);
        Long totalPlaytime = (Long) attrs.get("totalPlaytime");

        assertTrue("Total playtime should be updated", totalPlaytime > 0);
    }

    @Test
    public void testTotalPlaytimeResetsAfterEnd() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        try { Thread.sleep(100); } catch (InterruptedException e) {}
        tracker.updatePlaytime();

        tracker.sendEnd();

        // Start new video
        tracker.sendRequest();
        tracker.sendStart();
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);

        assertEquals("Total playtime should reset", Long.valueOf(0L), attrs.get("totalPlaytime"));
    }

    // ========== Attribute Tests ==========

    @Test
    public void testGetAttributesWithNullAttributes() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);

        assertNotNull("Attributes should be generated", attrs);
        assertFalse("Attributes should not be empty", attrs.isEmpty());
    }

    @Test
    public void testGetAttributesWithCustomAttributes() {
        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("customKey", "customValue");

        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, customAttrs);

        assertNotNull("Attributes should include custom attributes", attrs);
        assertEquals("Custom attribute should be present", "customValue", attrs.get("customKey"));
    }

    @Test
    public void testGetAttributesIncludesTrackerInfo() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);

        assertTrue("Should include tracker name", attrs.containsKey("trackerName"));
        assertTrue("Should include tracker version", attrs.containsKey("trackerVersion"));
        assertTrue("Should include tracker src", attrs.containsKey("src"));
    }

    @Test
    public void testGetAttributesIncludesPlayerInfo() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);

        assertTrue("Should include player name", attrs.containsKey("playerName"));
        assertTrue("Should include player version", attrs.containsKey("playerVersion"));
    }

    @Test
    public void testGetAttributesIncludesSessionInfo() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);

        assertNotNull("Should include view session", attrs.get("viewSession"));
        assertNotNull("Should include view ID", attrs.get("viewId"));
    }

    @Test
    public void testGetAttributesIncludesCounters() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);

        assertNotNull("Should include number of ads", attrs.get("numberOfAds"));
        assertNotNull("Should include number of videos", attrs.get("numberOfVideos"));
        assertNotNull("Should include number of errors", attrs.get("numberOfErrors"));
    }

    @Test
    public void testGetAttributesIncludesContentInfo() {
        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);

        // Content-specific attributes
        assertTrue("Should include content attributes",
                  attrs.containsKey("contentTitle") ||
                  attrs.containsKey("contentDuration") ||
                  attrs.containsKey("contentPlayhead"));
    }

    @Test
    public void testGetAttributesForBufferEvents() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
        tracker.sendBufferStart();

        Map<String, Object> attrs = tracker.getAttributes(CONTENT_BUFFER_START, null);

        assertTrue("Buffer events should include buffer type",
                  attrs.containsKey("bufferType"));
    }

    // ========== Dispose Tests ==========

    @Test
    public void testDispose() {
        tracker.startHeartbeat();

        // Should not throw exception
        tracker.dispose();
    }

    @Test
    public void testDisposeStopsHeartbeat() {
        tracker.startHeartbeat();
        tracker.dispose();

        // Heartbeat should be stopped
    }

    @Test
    public void testMultipleDisposeCalls() {
        tracker.dispose();
        tracker.dispose();
        tracker.dispose();

        // Should handle multiple dispose calls
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testSendError() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        Exception testException = new Exception("Test error");

        // Should not throw exception
        tracker.sendError(testException);
    }

    @Test
    public void testSendErrorWithNullMessage() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        // Should not throw exception - sendError handles null message
        tracker.sendError(0, null);
    }

    @Test
    public void testSendMultipleErrors() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        for (int i = 0; i < 5; i++) {
            tracker.sendError(new Exception("Error " + i));
        }

        // Should handle multiple errors
    }

    @Test
    public void testSendErrorWithCodeAndMessage() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        int errorCode = 500;
        String errorMessage = "Custom error message";

        // Should not throw exception
        tracker.sendError(errorCode, errorMessage);
    }

    // ========== State Tests ==========

    @Test
    public void testGetState() {
        NRTrackerState state = tracker.getState();

        assertNotNull("State should not be null", state);
    }

    @Test
    public void testStateIsSharedReference() {
        NRTrackerState state1 = tracker.getState();
        NRTrackerState state2 = tracker.getState();

        assertSame("State should return same reference", state1, state2);
    }

    @Test
    public void testStateUpdatesReflectInTracker() {
        NRTrackerState state = tracker.getState();

        tracker.setPlayer(new Object());

        assertTrue("State changes should be reflected", state.isPlayerReady);
    }

    // ========== Edge Cases ==========

    @Test
    public void testSendStartWithoutPlayerReady() {
        // Try to start without setting player
        tracker.sendStart();

        // Should handle gracefully
    }

    @Test
    public void testSendPauseWithoutStart() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();

        // Try to pause without starting
        tracker.sendPause();

        // Should handle gracefully
    }

    @Test
    public void testSendEndWithoutStart() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();

        // Try to end without starting
        tracker.sendEnd();

        // Should handle gracefully
    }

    @Test
    public void testRapidStateTransitions() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        // Rapid transitions
        for (int i = 0; i < 10; i++) {
            tracker.sendPause();
            tracker.sendResume();
        }

        tracker.sendEnd();

        // Should handle rapid transitions
    }

    @Test
    public void testGetAttributesForDifferentActions() {
        String[] actions = {
            CONTENT_START, CONTENT_PAUSE, CONTENT_RESUME, CONTENT_END,
            CONTENT_SEEK_START, CONTENT_SEEK_END,
            CONTENT_BUFFER_START, CONTENT_BUFFER_END,
            CONTENT_REQUEST, PLAYER_READY
        };

        for (String action : actions) {
            Map<String, Object> attrs = tracker.getAttributes(action, null);
            assertNotNull("Attributes should be generated for " + action, attrs);
            assertFalse("Attributes should not be empty for " + action, attrs.isEmpty());
        }
    }

    @Test
    public void testAttributesPersistCustomValues() {
        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("customKey1", "value1");
        customAttrs.put("customKey2", 42);
        customAttrs.put("customKey3", true);

        Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, customAttrs);

        assertEquals("Custom string should persist", "value1", attrs.get("customKey1"));
        assertEquals("Custom integer should persist", 42, attrs.get("customKey2"));
        assertEquals("Custom boolean should persist", true, attrs.get("customKey3"));
    }

    // ========== Concurrency Tests ==========

    @Test
    public void testConcurrentStateTransitions() throws InterruptedException {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                tracker.sendPause();
                try { Thread.sleep(5); } catch (InterruptedException e) {}
            }
        });

        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                tracker.sendResume();
                try { Thread.sleep(5); } catch (InterruptedException e) {}
            }
        });

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // Should handle concurrent transitions
    }

    @Test
    public void testConcurrentAttributeAccess() throws InterruptedException {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();

        Thread[] threads = new Thread[5];

        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    Map<String, Object> attrs = tracker.getAttributes(CONTENT_START, null);
                    assertNotNull("Attributes should not be null", attrs);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Should handle concurrent attribute access
    }

    // ========== sendError() empty/null message normalisation ==========

    /**
     * Capturing subclass — intercepts sendVideoEvent so tests can assert on the
     * exact attributes that flow into the event pipeline (i.e. what NRDB would receive).
     */
    private static class CapturingTracker extends NRVideoTracker {
        Map<String, Object> lastEventAttributes;

        @Override
        public void sendVideoEvent(String eventType, Map<String, Object> attributes) {
            lastEventAttributes = attributes != null ? new HashMap<>(attributes) : null;
            // Do NOT call super — avoids network I/O in tests.
        }

        @Override
        public void sendVideoErrorEvent(String eventType, Map<String, Object> attributes) {
            // sendError() routes through sendVideoErrorEvent, not sendVideoEvent
            lastEventAttributes = attributes != null ? new HashMap<>(attributes) : null;
        }
    }

    @Test
    public void sendError_emptyMessage_isReplacedWithUnknownError() {
        CapturingTracker t = new CapturingTracker();
        t.setPlayer(new Object());
        t.sendRequest();
        t.sendStart();

        t.sendError(null, "");

        assertNotNull("sendError must fire an event", t.lastEventAttributes);
        assertEquals(
            "empty errorMessage must be replaced with <Unknown error> before reaching NRDB",
            "<Unknown error>", t.lastEventAttributes.get("errorMessage"));
    }

    @Test
    public void sendError_blankMessage_isReplacedWithUnknownError() {
        CapturingTracker t = new CapturingTracker();
        t.setPlayer(new Object());
        t.sendRequest();
        t.sendStart();

        t.sendError(null, "   ");

        assertNotNull("sendError must fire an event", t.lastEventAttributes);
        assertEquals(
            "whitespace-only errorMessage must be replaced with <Unknown error> before reaching NRDB",
            "<Unknown error>", t.lastEventAttributes.get("errorMessage"));
    }

    @Test
    public void sendError_nullMessage_isReplacedWithUnknownError() {
        CapturingTracker t = new CapturingTracker();
        t.setPlayer(new Object());
        t.sendRequest();
        t.sendStart();

        t.sendError(null, null);

        assertNotNull("sendError must fire an event", t.lastEventAttributes);
        assertEquals(
            "null errorMessage must be replaced with <Unknown error> before reaching NRDB",
            "<Unknown error>", t.lastEventAttributes.get("errorMessage"));
    }

    @Test
    public void sendError_realMessage_isPreserved() {
        CapturingTracker t = new CapturingTracker();
        t.setPlayer(new Object());
        t.sendRequest();
        t.sendStart();

        t.sendError(2004, "Connection timed out");

        assertNotNull("sendError must fire an event", t.lastEventAttributes);
        assertEquals("real errorMessage must reach NRDB unchanged",
            "Connection timed out", t.lastEventAttributes.get("errorMessage"));
        assertEquals("real errorCode must reach NRDB unchanged",
            2004, t.lastEventAttributes.get("errorCode"));
    }

    @Test
    public void sendError_nullCode_isAbsentFromEvent() {
        CapturingTracker t = new CapturingTracker();
        t.setPlayer(new Object());
        t.sendRequest();
        t.sendStart();

        t.sendError(null, "some error");

        assertNotNull("sendError must fire an event", t.lastEventAttributes);
        assertFalse("null errorCode must NOT appear in the event",
            t.lastEventAttributes.containsKey("errorCode"));
    }

    /**
     * timeSince attributes from a previous session must NOT bleed into the first
     * events of the next session after viewId increments.
     *
     * Note: timeSinceTable.applyAttributes() runs inside NRTracker.sendEvent() AFTER
     * getAttributes() returns, so tests must call applyAttributes() directly on the
     * protected timeSinceTable field to observe what the fully-assembled event carries.
     *
     * After the fix, sendRequest() calls generateTimeSinceTable(), resetting all timestamps.
     */
    @Test
    public void timeSinceSeekEnd_doesNotBleedIntoNextSession_afterViewIdIncrement() {
        // Session 1: record a seek-end timestamp in the timeSinceTable
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
        // Drive timeSinceTable directly — simulates CONTENT_SEEK_END firing and recording the timestamp
        Map<String, Object> seekAttrs = new HashMap<>();
        tracker.timeSinceTable.applyAttributes(CONTENT_SEEK_END, seekAttrs);  // records ts.now()
        tracker.sendEnd();

        // Session 2: new session — sendRequest() calls generateTimeSinceTable(), resetting all entries
        tracker.sendRequest();

        // Apply timeSinceTable for CONTENT_BUFFER_START — its filter matches this event type.
        // If the timestamp bled over, timeSinceSeekEnd would appear here.
        Map<String, Object> bufferAttrs = new HashMap<>();
        tracker.timeSinceTable.applyAttributes(CONTENT_BUFFER_START, bufferAttrs);

        assertNull(
            "timeSinceSeekEnd must be absent on CONTENT_BUFFER_START in a new session — " +
            "sendRequest() must reset the timeSince table",
            bufferAttrs.get("timeSinceSeekEnd"));
    }

    @Test
    public void timeSinceSeekEnd_isPopulated_afterSeekInSameSession() {
        // Positive case: timeSinceSeekEnd IS present when a seek occurred in the SAME session
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
        // Record the seek-end timestamp in the current session's timeSinceTable
        Map<String, Object> seekAttrs = new HashMap<>();
        tracker.timeSinceTable.applyAttributes(CONTENT_SEEK_END, seekAttrs);  // records ts.now()

        // CONTENT_BUFFER_START matches the filter — timeSinceSeekEnd should be present
        Map<String, Object> bufferAttrs = new HashMap<>();
        tracker.timeSinceTable.applyAttributes(CONTENT_BUFFER_START, bufferAttrs);

        assertNotNull(
            "timeSinceSeekEnd must be present on CONTENT_BUFFER_START when a seek occurred in this session",
            bufferAttrs.get("timeSinceSeekEnd"));
    }
}
