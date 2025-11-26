package com.newrelic.videoagent.core.mediatailor;

import androidx.media3.exoplayer.ExoPlayer;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorAdBreak;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorLinearAd;
import com.newrelic.videoagent.core.mediatailor.model.PlayingAdBreak;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MediaTailorAdPlaybackTracker.
 * Tests ad playback tracking with a single ad break containing one ad.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class MediaTailorAdPlaybackTrackerTest {

    @Mock
    private ExoPlayer mockPlayer;

    private MediaTailorAdPlaybackTracker tracker;
    private List<MediaTailorAdBreak> adBreaks;

    // Test data constants matching the example
    private static final String AD_BREAK_ID = "3";
    private static final double AD_BREAK_SCHEDULE_TIME = 18.0; // seconds
    private static final double AD_BREAK_DURATION = 10.0; // seconds
    private static final String AD_BREAK_FORMATTED_DURATION = "PT10S";
    private static final double AD_MARKER_DURATION = 0.0;

    private static final String AD_ID = "ad-1";
    private static final double AD_DURATION = 10.0; // seconds
    private static final String AD_FORMATTED_DURATION = "PT10S";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create a single ad
        MediaTailorLinearAd ad = new MediaTailorLinearAd(
                AD_ID,
                AD_BREAK_SCHEDULE_TIME, // Ad starts at same time as ad break
                AD_DURATION,
                AD_FORMATTED_DURATION,
                Collections.emptyList() // No tracking events for this test
        );

        // Create a single ad break with one ad
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
    }

    @Test
    public void testInitialization() {
        assertNotNull("Tracker should be initialized", tracker);
        assertFalse("Tracker should not be tracking initially", tracker.isTracking());
        assertNull("No ad break should be playing initially", tracker.getPlayingAdBreak());
        assertNull("Next ad break should be null initially", tracker.getNextAdBreak());
    }

    @Test
    public void testStartTracking() {
        tracker.start();
        assertTrue("Tracker should be tracking after start", tracker.isTracking());

        // Starting again should not cause issues
        tracker.start();
        assertTrue("Tracker should still be tracking", tracker.isTracking());
    }

    @Test
    public void testStopTracking() {
        tracker.start();
        assertTrue("Tracker should be tracking", tracker.isTracking());

        tracker.stop();
        assertFalse("Tracker should not be tracking after stop", tracker.isTracking());

        // Stopping again should not cause issues
        tracker.stop();
        assertFalse("Tracker should still not be tracking", tracker.isTracking());
    }

    @Test
    public void testResetTracking() {
        // Set up tracking to a certain state
        when(mockPlayer.getCurrentPosition()).thenReturn(20000L); // 20 seconds
        tracker.start();

        // Allow some processing time
        advanceLooper();

        // Reset should clear state
        tracker.reset();
        assertNull("Playing ad break should be null after reset", tracker.getPlayingAdBreak());
        assertNull("Next ad break should be null after reset", tracker.getNextAdBreak());
    }

    /**
     * Helper method to advance Robolectric's looper and process pending tasks.
     * This simulates the passage of time and allows scheduled tasks to run.
     */
    private void advanceLooper() {
        ShadowLooper shadowLooper = ShadowLooper.shadowMainLooper();
        // Advance time by tracking interval (100ms) + buffer
        shadowLooper.idleFor(java.time.Duration.ofMillis(150));
    }

    @Test
    public void testPlayerBeforeAdBreak() {
        // Player at 5 seconds - before ad break starts at 18s
        when(mockPlayer.getCurrentPosition()).thenReturn(5000L); // 5 seconds in milliseconds

        tracker.start();

        // Allow tracking to run
        advanceLooper();

        // Should have next ad break but not be playing any ad
        MediaTailorAdBreak nextAdBreak = tracker.getNextAdBreak();
        assertNotNull("Next ad break should be available", nextAdBreak);
        assertEquals("Next ad break should be our test ad break", AD_BREAK_ID, nextAdBreak.getId());

        PlayingAdBreak playingAdBreak = tracker.getPlayingAdBreak();
        assertNull("Should not be playing any ad break yet", playingAdBreak);

        tracker.stop();
    }

    @Test
    public void testPlayerAtAdBreakStart() {
        // Player at exactly 18 seconds - ad break and ad start
        when(mockPlayer.getCurrentPosition()).thenReturn(18000L); // 18 seconds in milliseconds

        tracker.start();

        // Allow tracking to run
        advanceLooper();

        // Should be playing the ad
        PlayingAdBreak playingAdBreak = tracker.getPlayingAdBreak();
        assertNotNull("Should be playing ad break", playingAdBreak);
        assertEquals("Playing ad break ID should match", AD_BREAK_ID, playingAdBreak.getAdBreak().getId());
        assertEquals("Should be playing ad at index 0", 0, playingAdBreak.getAdIndex());
        assertEquals("Playing ad ID should match", AD_ID, playingAdBreak.getAd().getId());

        tracker.stop();
    }

    @Test
    public void testPlayerDuringAd() {
        // Player at 22 seconds - in the middle of the ad (18s-28s)
        when(mockPlayer.getCurrentPosition()).thenReturn(22000L); // 22 seconds in milliseconds

        tracker.start();

        // Allow tracking to run
        advanceLooper();

        // Should be playing the ad
        PlayingAdBreak playingAdBreak = tracker.getPlayingAdBreak();
        assertNotNull("Should be playing ad break", playingAdBreak);
        assertEquals("Playing ad break ID should match", AD_BREAK_ID, playingAdBreak.getAdBreak().getId());
        assertEquals("Should be playing ad at index 0", 0, playingAdBreak.getAdIndex());

        tracker.stop();
    }

    @Test
    public void testPlayerAtAdEnd() {
        // Player at exactly 28 seconds - end of ad and ad break
        when(mockPlayer.getCurrentPosition()).thenReturn(28000L); // 28 seconds in milliseconds

        tracker.start();

        // Allow tracking to run
        advanceLooper();

        // At the exact end time, should NOT be playing (range is [start, end))
        PlayingAdBreak playingAdBreak = tracker.getPlayingAdBreak();
        assertNull("Should not be playing ad break at exact end time", playingAdBreak);

        tracker.stop();
    }

    @Test
    public void testPlayerAfterAdBreak() {
        // Player at 30 seconds - after ad break ends at 28s
        when(mockPlayer.getCurrentPosition()).thenReturn(30000L); // 30 seconds in milliseconds

        tracker.start();

        // Allow tracking to run
        advanceLooper();

        // Should not be playing any ad
        PlayingAdBreak playingAdBreak = tracker.getPlayingAdBreak();
        assertNull("Should not be playing any ad break after it ends", playingAdBreak);

        // No more ad breaks
        MediaTailorAdBreak nextAdBreak = tracker.getNextAdBreak();
        assertNull("Should not have next ad break", nextAdBreak);

        tracker.stop();
    }

    @Test
    public void testGetCurrentPlayerTime() {
        when(mockPlayer.getCurrentPosition()).thenReturn(15500L); // 15.5 seconds

        double playerTime = tracker.getCurrentPlayerTime();
        assertEquals("Player time should be 15.5 seconds", 15.5, playerTime, 0.01);
    }

    @Test
    public void testGetStateDebugInfo() {
        when(mockPlayer.getCurrentPosition()).thenReturn(20000L); // 20 seconds

        tracker.start();

        // Allow tracking to run
        advanceLooper();

        String debugInfo = tracker.getStateDebugInfo();
        assertNotNull("Debug info should not be null", debugInfo);
        assertTrue("Debug info should contain player time", debugInfo.contains("playerTime="));
        assertTrue("Debug info should contain ad break index", debugInfo.contains("adBreakIndex="));

        tracker.stop();
    }

    @Test
    public void testEmptyAdBreaksList() {
        // Create tracker with empty ad breaks list
        MediaTailorAdPlaybackTracker emptyTracker = new MediaTailorAdPlaybackTracker(
                mockPlayer,
                Collections.emptyList()
        );

        when(mockPlayer.getCurrentPosition()).thenReturn(20000L);

        emptyTracker.start();

        // Allow tracking to run
        advanceLooper();

        // Should handle empty list gracefully
        assertNull("Should not have playing ad break", emptyTracker.getPlayingAdBreak());
        assertNull("Should not have next ad break", emptyTracker.getNextAdBreak());

        emptyTracker.stop();
    }
}
