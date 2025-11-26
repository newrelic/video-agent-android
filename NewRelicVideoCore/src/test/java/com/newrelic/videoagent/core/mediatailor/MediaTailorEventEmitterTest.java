package com.newrelic.videoagent.core.mediatailor;

import androidx.media3.exoplayer.ExoPlayer;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorAdBreak;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorLinearAd;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MediaTailorEventEmitter.
 * Tests event emission throughout the complete ad lifecycle with simulated time progression.
 *
 * Test scenario:
 * - Content plays from 0s to 18s
 * - Ad break starts at 18s
 * - Single ad plays from 18s to 28s
 * - Ad break ends at 28s
 * - Content resumes at 28s
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class MediaTailorEventEmitterTest {

    @Mock
    private ExoPlayer mockPlayer;

    private MediaTailorAdPlaybackTracker tracker;
    private MediaTailorEventEmitter emitter;
    private List<MediaTailorAdBreak> adBreaks;

    // Test data constants
    private static final String AD_BREAK_ID = "3";
    private static final double AD_BREAK_SCHEDULE_TIME = 18.0; // seconds
    private static final double AD_BREAK_DURATION = 10.0; // seconds
    private static final String AD_BREAK_FORMATTED_DURATION = "PT10S";
    private static final double AD_MARKER_DURATION = 0.0;

    private static final String AD_ID = "ad-1";
    private static final double AD_DURATION = 10.0; // seconds
    private static final String AD_FORMATTED_DURATION = "PT10S";

    // Simulated player time (in milliseconds)
    private AtomicLong simulatedPlayerTime;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Initialize simulated player time
        simulatedPlayerTime = new AtomicLong(0);

        // Mock player to return simulated time
        when(mockPlayer.getCurrentPosition()).thenAnswer(invocation -> simulatedPlayerTime.get());

        // Create test data
        MediaTailorLinearAd ad = new MediaTailorLinearAd(
                AD_ID,
                AD_BREAK_SCHEDULE_TIME,
                AD_DURATION,
                AD_FORMATTED_DURATION,
                Collections.emptyList()
        );

        MediaTailorAdBreak adBreak = new MediaTailorAdBreak(
                AD_BREAK_ID,
                Collections.singletonList(ad),
                AD_BREAK_SCHEDULE_TIME,
                AD_BREAK_DURATION,
                AD_BREAK_FORMATTED_DURATION,
                AD_MARKER_DURATION
        );

        adBreaks = Collections.singletonList(adBreak);
        tracker = new MediaTailorAdPlaybackTracker(mockPlayer, adBreaks);
        emitter = new MediaTailorEventEmitter(tracker);
    }

    @Test
    public void testInitialization() {
        assertNotNull("Emitter should be initialized", emitter);
        assertFalse("Emitter should not be emitting initially", emitter.isEmitting());
    }

    @Test
    public void testStartEmitting() {
        emitter.start();
        assertTrue("Emitter should be emitting after start", emitter.isEmitting());

        // Starting again should not cause issues
        emitter.start();
        assertTrue("Emitter should still be emitting", emitter.isEmitting());

        emitter.stop();
    }

    @Test
    public void testStopEmitting() {
        emitter.start();
        assertTrue("Emitter should be emitting", emitter.isEmitting());

        emitter.stop();
        assertFalse("Emitter should not be emitting after stop", emitter.isEmitting());

        // Stopping again should not cause issues
        emitter.stop();
        assertFalse("Emitter should still not be emitting", emitter.isEmitting());
    }

    @Test
    public void testResetEmitter() {
        emitter.start();
        emitter.reset();
        // After reset, state should be cleared (tested via debug info)
        String debugInfo = emitter.getStateDebugInfo();
        assertNotNull("Debug info should not be null", debugInfo);
        assertTrue("Debug info should indicate null previous state", debugInfo.contains("previousPlayingAdBreak=null"));
        emitter.stop();
    }

    @Test
    public void testGetStateDebugInfo() {
        String debugInfo = emitter.getStateDebugInfo();
        assertNotNull("Debug info should not be null", debugInfo);
        assertTrue("Debug info should contain isEmitting", debugInfo.contains("isEmitting="));
        assertTrue("Debug info should contain previousPlayingAdBreak", debugInfo.contains("previousPlayingAdBreak="));
    }

    /**
     * Test complete ad lifecycle with simulated time progression.
     * This test simulates the full flow:
     * 1. Content plays (before ad break)
     * 2. Ad break starts
     * 3. Ad starts
     * 4. Ad plays
     * 5. Ad finishes
     * 6. Ad break finishes
     * 7. Content resumes
     */
    @Test
    public void testCompleteAdLifecycleWithTimeProgression() {
        // Start both tracker and emitter
        tracker.start();
        emitter.start();

        // Phase 1: Content plays from 0s to 17s (before ad break)
        System.out.println("\n=== Phase 1: Content playing (0s - 17s) ===");
        setPlayerTime(0.0);
        waitForTracking();
        assertNull("Should not be playing ad at 0s", tracker.getPlayingAdBreak());
        assertNotNull("Should have next ad break at 0s", tracker.getNextAdBreak());

        setPlayerTime(10.0);
        waitForTracking();
        assertNull("Should not be playing ad at 10s", tracker.getPlayingAdBreak());

        setPlayerTime(17.0);
        waitForTracking();
        assertNull("Should not be playing ad at 17s", tracker.getPlayingAdBreak());

        // Phase 2: Ad break starts and ad starts (at 18s)
        System.out.println("\n=== Phase 2: Ad break and ad start (18s) ===");
        setPlayerTime(18.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 18s", tracker.getPlayingAdBreak());
        assertEquals("Should be playing ad break 3", AD_BREAK_ID, tracker.getPlayingAdBreak().getAdBreak().getId());
        assertEquals("Should be playing ad-1", AD_ID, tracker.getPlayingAdBreak().getAd().getId());
        assertEquals("Should be at ad index 0", 0, tracker.getPlayingAdBreak().getAdIndex());

        // Phase 3: Ad continues playing (19s - 27s)
        System.out.println("\n=== Phase 3: Ad playing (19s - 27s) ===");
        setPlayerTime(19.0);
        waitForTracking();
        assertNotNull("Should still be playing ad at 19s", tracker.getPlayingAdBreak());

        setPlayerTime(22.0);
        waitForTracking();
        assertNotNull("Should still be playing ad at 22s (mid-ad)", tracker.getPlayingAdBreak());

        setPlayerTime(27.0);
        waitForTracking();
        assertNotNull("Should still be playing ad at 27s (near end)", tracker.getPlayingAdBreak());

        // Phase 4: Ad and ad break finish (at 28s)
        System.out.println("\n=== Phase 4: Ad and ad break finish (28s) ===");
        setPlayerTime(28.0);
        waitForTracking();
        assertNull("Should not be playing ad at 28s (exact end)", tracker.getPlayingAdBreak());

        // Phase 5: Content resumes (after 28s)
        System.out.println("\n=== Phase 5: Content resumes (29s+) ===");
        setPlayerTime(29.0);
        waitForTracking();
        assertNull("Should not be playing ad at 29s", tracker.getPlayingAdBreak());
        assertNull("Should not have next ad break at 29s", tracker.getNextAdBreak());

        setPlayerTime(35.0);
        waitForTracking();
        assertNull("Should not be playing ad at 35s", tracker.getPlayingAdBreak());

        // Stop tracker and emitter
        tracker.stop();
        emitter.stop();

        System.out.println("\n=== Test Complete ===");
    }

    /**
     * Test seeking behavior - jumping into middle of ad.
     */
    @Test
    public void testSeekIntoAd() {
        tracker.start();
        emitter.start();

        // Start at beginning
        setPlayerTime(5.0);
        waitForTracking();
        assertNull("Should not be playing ad at 5s", tracker.getPlayingAdBreak());

        // Seek directly into middle of ad
        System.out.println("\n=== Seeking into ad (5s -> 22s) ===");
        setPlayerTime(22.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 22s after seek", tracker.getPlayingAdBreak());
        assertEquals("Should be playing correct ad", AD_ID, tracker.getPlayingAdBreak().getAd().getId());

        tracker.stop();
        emitter.stop();
    }

    /**
     * Test seeking behavior - jumping out of ad.
     */
    @Test
    public void testSeekOutOfAd() {
        tracker.start();
        emitter.start();

        // Start in middle of ad
        setPlayerTime(22.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 22s", tracker.getPlayingAdBreak());

        // Seek past the ad
        System.out.println("\n=== Seeking out of ad (22s -> 30s) ===");
        setPlayerTime(30.0);
        waitForTracking();
        assertNull("Should not be playing ad at 30s after seek", tracker.getPlayingAdBreak());

        tracker.stop();
        emitter.stop();
    }

    /**
     * Test edge case: player exactly at ad break start boundary.
     */
    @Test
    public void testExactAdBreakStartBoundary() {
        tracker.start();
        emitter.start();

        setPlayerTime(AD_BREAK_SCHEDULE_TIME); // Exactly 18.0s
        waitForTracking();

        assertNotNull("Should be playing ad at exact start time", tracker.getPlayingAdBreak());
        assertEquals("Should be playing correct ad break", AD_BREAK_ID, tracker.getPlayingAdBreak().getAdBreak().getId());

        tracker.stop();
        emitter.stop();
    }

    /**
     * Test edge case: player exactly at ad break end boundary.
     */
    @Test
    public void testExactAdBreakEndBoundary() {
        tracker.start();
        emitter.start();

        setPlayerTime(AD_BREAK_SCHEDULE_TIME + AD_BREAK_DURATION); // Exactly 28.0s
        waitForTracking();

        assertNull("Should not be playing ad at exact end time (exclusive)", tracker.getPlayingAdBreak());

        tracker.stop();
        emitter.stop();
    }

    /**
     * Test state transitions with minimal time gaps.
     */
    @Test
    public void testRapidStateTransitions() {
        tracker.start();
        emitter.start();

        // Quickly transition through states
        setPlayerTime(17.9); // Just before ad
        waitForTracking();
        assertNull("Should not be playing ad at 17.9s", tracker.getPlayingAdBreak());

        setPlayerTime(18.0); // Ad starts
        waitForTracking();
        assertNotNull("Should be playing ad at 18.0s", tracker.getPlayingAdBreak());

        setPlayerTime(18.1); // Just into ad
        waitForTracking();
        assertNotNull("Should be playing ad at 18.1s", tracker.getPlayingAdBreak());

        setPlayerTime(27.9); // Near ad end
        waitForTracking();
        assertNotNull("Should be playing ad at 27.9s", tracker.getPlayingAdBreak());

        setPlayerTime(28.0); // Ad ends
        waitForTracking();
        assertNull("Should not be playing ad at 28.0s", tracker.getPlayingAdBreak());

        tracker.stop();
        emitter.stop();
    }

    // Helper methods

    /**
     * Set the simulated player time.
     * @param seconds Time in seconds
     */
    private void setPlayerTime(double seconds) {
        long milliseconds = (long) (seconds * 1000);
        simulatedPlayerTime.set(milliseconds);
        System.out.println(String.format("Player time set to: %.1fs", seconds));
    }

    /**
     * Wait for tracking and event emission to process.
     * This allows the periodic handlers to run by advancing Robolectric's looper.
     */
    private void waitForTracking() {
        // Get shadow looper for main thread
        ShadowLooper shadowLooper = ShadowLooper.shadowMainLooper();

        // Advance time by tracking interval (100ms) + event check interval (50ms) + buffer
        shadowLooper.idleFor(java.time.Duration.ofMillis(250));
    }
}
