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

    /**
     * Test complete ad lifecycle with multiple ads in a single ad break.
     *
     * Scenario:
     * - Content plays from 0s to 10s
     * - Ad break starts at 10s
     * - Ad 1 plays from 10s to 15s (5 second ad)
     * - Ad 2 plays from 15s to 22s (7 second ad)
     * - Ad 3 plays from 22s to 25s (3 second ad)
     * - Ad break ends at 25s (total duration 15s)
     * - Content resumes at 25s
     */
    @Test
    public void testMultipleAdsInSingleAdBreak() {
        // Create test data for multiple ads
        MediaTailorLinearAd ad1 = new MediaTailorLinearAd(
                "ad-1",
                10.0,  // starts at 10s
                5.0,   // duration 5s
                "PT5S",
                Collections.emptyList()
        );

        MediaTailorLinearAd ad2 = new MediaTailorLinearAd(
                "ad-2",
                15.0,  // starts at 15s
                7.0,   // duration 7s
                "PT7S",
                Collections.emptyList()
        );

        MediaTailorLinearAd ad3 = new MediaTailorLinearAd(
                "ad-3",
                22.0,  // starts at 22s
                3.0,   // duration 3s
                "PT3S",
                Collections.emptyList()
        );

        List<MediaTailorLinearAd> ads = java.util.Arrays.asList(ad1, ad2, ad3);

        MediaTailorAdBreak adBreak = new MediaTailorAdBreak(
                "break-1",
                ads,
                10.0,  // scheduled at 10s
                15.0,  // total duration 15s
                "PT15S",
                0.0
        );

        // Create new tracker and emitter with this data
        MediaTailorAdPlaybackTracker multiAdTracker = new MediaTailorAdPlaybackTracker(
                mockPlayer,
                Collections.singletonList(adBreak)
        );
        MediaTailorEventEmitter multiAdEmitter = new MediaTailorEventEmitter(multiAdTracker);

        multiAdTracker.start();
        multiAdEmitter.start();

        // Phase 1: Content before ad break (0s - 9s)
        System.out.println("\n=== Phase 1: Content playing (0s - 9s) ===");
        setPlayerTime(0.0);
        waitForTracking();
        assertNull("Should not be playing ad at 0s", multiAdTracker.getPlayingAdBreak());

        setPlayerTime(9.0);
        waitForTracking();
        assertNull("Should not be playing ad at 9s", multiAdTracker.getPlayingAdBreak());

        // Phase 2: Ad break starts, Ad 1 starts (10s)
        System.out.println("\n=== Phase 2: Ad break and Ad 1 start (10s) ===");
        setPlayerTime(10.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 10s", multiAdTracker.getPlayingAdBreak());
        assertEquals("Should be playing ad-1", "ad-1", multiAdTracker.getPlayingAdBreak().getAd().getId());
        assertEquals("Should be at ad index 0", 0, multiAdTracker.getPlayingAdBreak().getAdIndex());

        // Phase 3: Ad 1 continues (12s)
        System.out.println("\n=== Phase 3: Ad 1 playing (12s) ===");
        setPlayerTime(12.0);
        waitForTracking();
        assertNotNull("Should still be playing ad at 12s", multiAdTracker.getPlayingAdBreak());
        assertEquals("Should still be playing ad-1", "ad-1", multiAdTracker.getPlayingAdBreak().getAd().getId());

        // Phase 4: Ad 1 ends, Ad 2 starts (15s)
        System.out.println("\n=== Phase 4: Ad 1 ends, Ad 2 starts (15s) ===");
        setPlayerTime(15.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 15s", multiAdTracker.getPlayingAdBreak());
        assertEquals("Should be playing ad-2", "ad-2", multiAdTracker.getPlayingAdBreak().getAd().getId());
        assertEquals("Should be at ad index 1", 1, multiAdTracker.getPlayingAdBreak().getAdIndex());

        // Phase 5: Ad 2 continues (18s)
        System.out.println("\n=== Phase 5: Ad 2 playing (18s) ===");
        setPlayerTime(18.0);
        waitForTracking();
        assertNotNull("Should still be playing ad at 18s", multiAdTracker.getPlayingAdBreak());
        assertEquals("Should still be playing ad-2", "ad-2", multiAdTracker.getPlayingAdBreak().getAd().getId());

        // Phase 6: Ad 2 ends, Ad 3 starts (22s)
        System.out.println("\n=== Phase 6: Ad 2 ends, Ad 3 starts (22s) ===");
        setPlayerTime(22.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 22s", multiAdTracker.getPlayingAdBreak());
        assertEquals("Should be playing ad-3", "ad-3", multiAdTracker.getPlayingAdBreak().getAd().getId());
        assertEquals("Should be at ad index 2", 2, multiAdTracker.getPlayingAdBreak().getAdIndex());

        // Phase 7: Ad 3 continues (23s)
        System.out.println("\n=== Phase 7: Ad 3 playing (23s) ===");
        setPlayerTime(23.0);
        waitForTracking();
        assertNotNull("Should still be playing ad at 23s", multiAdTracker.getPlayingAdBreak());
        assertEquals("Should still be playing ad-3", "ad-3", multiAdTracker.getPlayingAdBreak().getAd().getId());

        // Phase 8: Ad 3 ends, Ad break ends (25s)
        System.out.println("\n=== Phase 8: Ad 3 and ad break end (25s) ===");
        setPlayerTime(25.0);
        waitForTracking();
        assertNull("Should not be playing ad at 25s", multiAdTracker.getPlayingAdBreak());

        // Phase 9: Content resumes (26s+)
        System.out.println("\n=== Phase 9: Content resumes (26s+) ===");
        setPlayerTime(26.0);
        waitForTracking();
        assertNull("Should not be playing ad at 26s", multiAdTracker.getPlayingAdBreak());

        multiAdTracker.stop();
        multiAdEmitter.stop();

        System.out.println("\n=== Test Complete: Multiple ads in single ad break ===");
    }

    /**
     * Test complete ad lifecycle with multiple ad breaks.
     *
     * Scenario:
     * - Content plays from 0s to 10s
     * - Ad break 1 at 10s with 1 ad (10s-15s)
     * - Content plays from 15s to 30s
     * - Ad break 2 at 30s with 1 ad (30s-38s)
     * - Content plays from 38s to 60s
     * - Ad break 3 at 60s with 1 ad (60s-65s)
     * - Content resumes at 65s
     */
    @Test
    public void testMultipleAdBreaksWithSingleAds() {
        // Create ad break 1 (10s-15s)
        MediaTailorLinearAd ad1 = new MediaTailorLinearAd(
                "ad-1",
                10.0,
                5.0,
                "PT5S",
                Collections.emptyList()
        );
        MediaTailorAdBreak adBreak1 = new MediaTailorAdBreak(
                "break-1",
                Collections.singletonList(ad1),
                10.0,
                5.0,
                "PT5S",
                0.0
        );

        // Create ad break 2 (30s-38s)
        MediaTailorLinearAd ad2 = new MediaTailorLinearAd(
                "ad-2",
                30.0,
                8.0,
                "PT8S",
                Collections.emptyList()
        );
        MediaTailorAdBreak adBreak2 = new MediaTailorAdBreak(
                "break-2",
                Collections.singletonList(ad2),
                30.0,
                8.0,
                "PT8S",
                0.0
        );

        // Create ad break 3 (60s-65s)
        MediaTailorLinearAd ad3 = new MediaTailorLinearAd(
                "ad-3",
                60.0,
                5.0,
                "PT5S",
                Collections.emptyList()
        );
        MediaTailorAdBreak adBreak3 = new MediaTailorAdBreak(
                "break-3",
                Collections.singletonList(ad3),
                60.0,
                5.0,
                "PT5S",
                0.0
        );

        List<MediaTailorAdBreak> multipleBreaks = java.util.Arrays.asList(adBreak1, adBreak2, adBreak3);

        // Create new tracker and emitter
        MediaTailorAdPlaybackTracker multiBreakTracker = new MediaTailorAdPlaybackTracker(
                mockPlayer,
                multipleBreaks
        );
        MediaTailorEventEmitter multiBreakEmitter = new MediaTailorEventEmitter(multiBreakTracker);

        multiBreakTracker.start();
        multiBreakEmitter.start();

        // Phase 1: Content before first ad break (0s-9s)
        System.out.println("\n=== Phase 1: Content (0s-9s) ===");
        setPlayerTime(5.0);
        waitForTracking();
        assertNull("Should not be playing ad at 5s", multiBreakTracker.getPlayingAdBreak());

        // Phase 2: First ad break (10s-15s)
        System.out.println("\n=== Phase 2: Ad Break 1 (10s-15s) ===");
        setPlayerTime(10.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 10s", multiBreakTracker.getPlayingAdBreak());
        assertEquals("Should be playing break-1", "break-1", multiBreakTracker.getPlayingAdBreak().getAdBreak().getId());
        assertEquals("Should be playing ad-1", "ad-1", multiBreakTracker.getPlayingAdBreak().getAd().getId());

        setPlayerTime(12.0);
        waitForTracking();
        assertNotNull("Should still be playing ad at 12s", multiBreakTracker.getPlayingAdBreak());
        assertEquals("Should still be playing ad-1", "ad-1", multiBreakTracker.getPlayingAdBreak().getAd().getId());

        // Phase 3: Content between ad breaks (15s-29s)
        System.out.println("\n=== Phase 3: Content between breaks (15s-29s) ===");
        setPlayerTime(15.0);
        waitForTracking();
        assertNull("Should not be playing ad at 15s", multiBreakTracker.getPlayingAdBreak());

        setPlayerTime(20.0);
        waitForTracking();
        assertNull("Should not be playing ad at 20s", multiBreakTracker.getPlayingAdBreak());

        setPlayerTime(29.0);
        waitForTracking();
        assertNull("Should not be playing ad at 29s", multiBreakTracker.getPlayingAdBreak());

        // Phase 4: Second ad break (30s-38s)
        System.out.println("\n=== Phase 4: Ad Break 2 (30s-38s) ===");
        setPlayerTime(30.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 30s", multiBreakTracker.getPlayingAdBreak());
        assertEquals("Should be playing break-2", "break-2", multiBreakTracker.getPlayingAdBreak().getAdBreak().getId());
        assertEquals("Should be playing ad-2", "ad-2", multiBreakTracker.getPlayingAdBreak().getAd().getId());

        setPlayerTime(34.0);
        waitForTracking();
        assertNotNull("Should still be playing ad at 34s", multiBreakTracker.getPlayingAdBreak());
        assertEquals("Should still be playing ad-2", "ad-2", multiBreakTracker.getPlayingAdBreak().getAd().getId());

        // Phase 5: Content between ad breaks (38s-59s)
        System.out.println("\n=== Phase 5: Content between breaks (38s-59s) ===");
        setPlayerTime(38.0);
        waitForTracking();
        assertNull("Should not be playing ad at 38s", multiBreakTracker.getPlayingAdBreak());

        setPlayerTime(50.0);
        waitForTracking();
        assertNull("Should not be playing ad at 50s", multiBreakTracker.getPlayingAdBreak());

        // Phase 6: Third ad break (60s-65s)
        System.out.println("\n=== Phase 6: Ad Break 3 (60s-65s) ===");
        setPlayerTime(60.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 60s", multiBreakTracker.getPlayingAdBreak());
        assertEquals("Should be playing break-3", "break-3", multiBreakTracker.getPlayingAdBreak().getAdBreak().getId());
        assertEquals("Should be playing ad-3", "ad-3", multiBreakTracker.getPlayingAdBreak().getAd().getId());

        setPlayerTime(62.0);
        waitForTracking();
        assertNotNull("Should still be playing ad at 62s", multiBreakTracker.getPlayingAdBreak());

        // Phase 7: Content after all ad breaks (65s+)
        System.out.println("\n=== Phase 7: Content after all breaks (65s+) ===");
        setPlayerTime(65.0);
        waitForTracking();
        assertNull("Should not be playing ad at 65s", multiBreakTracker.getPlayingAdBreak());

        setPlayerTime(70.0);
        waitForTracking();
        assertNull("Should not be playing ad at 70s", multiBreakTracker.getPlayingAdBreak());

        multiBreakTracker.stop();
        multiBreakEmitter.stop();

        System.out.println("\n=== Test Complete: Multiple ad breaks ===");
    }

    /**
     * Test complete ad lifecycle with multiple ad breaks, each containing multiple ads.
     *
     * Scenario:
     * - Content plays from 0s to 10s
     * - Ad break 1 at 10s with 2 ads (10s-20s total)
     *   - Ad 1a: 10s-15s (5s)
     *   - Ad 1b: 15s-20s (5s)
     * - Content plays from 20s to 40s
     * - Ad break 2 at 40s with 3 ads (40s-55s total)
     *   - Ad 2a: 40s-45s (5s)
     *   - Ad 2b: 45s-50s (5s)
     *   - Ad 2c: 50s-55s (5s)
     * - Content resumes at 55s
     */
    @Test
    public void testMultipleAdBreaksWithMultipleAds() {
        // Ad break 1 with 2 ads (10s-20s)
        MediaTailorLinearAd ad1a = new MediaTailorLinearAd("ad-1a", 10.0, 5.0, "PT5S", Collections.emptyList());
        MediaTailorLinearAd ad1b = new MediaTailorLinearAd("ad-1b", 15.0, 5.0, "PT5S", Collections.emptyList());
        MediaTailorAdBreak adBreak1 = new MediaTailorAdBreak(
                "break-1",
                java.util.Arrays.asList(ad1a, ad1b),
                10.0,
                10.0,
                "PT10S",
                0.0
        );

        // Ad break 2 with 3 ads (40s-55s)
        MediaTailorLinearAd ad2a = new MediaTailorLinearAd("ad-2a", 40.0, 5.0, "PT5S", Collections.emptyList());
        MediaTailorLinearAd ad2b = new MediaTailorLinearAd("ad-2b", 45.0, 5.0, "PT5S", Collections.emptyList());
        MediaTailorLinearAd ad2c = new MediaTailorLinearAd("ad-2c", 50.0, 5.0, "PT5S", Collections.emptyList());
        MediaTailorAdBreak adBreak2 = new MediaTailorAdBreak(
                "break-2",
                java.util.Arrays.asList(ad2a, ad2b, ad2c),
                40.0,
                15.0,
                "PT15S",
                0.0
        );

        List<MediaTailorAdBreak> complexBreaks = java.util.Arrays.asList(adBreak1, adBreak2);

        // Create tracker and emitter
        MediaTailorAdPlaybackTracker complexTracker = new MediaTailorAdPlaybackTracker(
                mockPlayer,
                complexBreaks
        );
        MediaTailorEventEmitter complexEmitter = new MediaTailorEventEmitter(complexTracker);

        complexTracker.start();
        complexEmitter.start();

        // Phase 1: Content (0s-9s)
        System.out.println("\n=== Phase 1: Content (0s-9s) ===");
        setPlayerTime(5.0);
        waitForTracking();
        assertNull("Should not be playing ad at 5s", complexTracker.getPlayingAdBreak());

        // Phase 2: Ad Break 1, Ad 1a (10s-15s)
        System.out.println("\n=== Phase 2: Break 1, Ad 1a (10s-15s) ===");
        setPlayerTime(10.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 10s", complexTracker.getPlayingAdBreak());
        assertEquals("Should be playing break-1", "break-1", complexTracker.getPlayingAdBreak().getAdBreak().getId());
        assertEquals("Should be playing ad-1a", "ad-1a", complexTracker.getPlayingAdBreak().getAd().getId());
        assertEquals("Should be at index 0", 0, complexTracker.getPlayingAdBreak().getAdIndex());

        setPlayerTime(12.0);
        waitForTracking();
        assertEquals("Should still be playing ad-1a", "ad-1a", complexTracker.getPlayingAdBreak().getAd().getId());

        // Phase 3: Ad Break 1, Ad 1b (15s-20s)
        System.out.println("\n=== Phase 3: Break 1, Ad 1b (15s-20s) ===");
        setPlayerTime(15.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 15s", complexTracker.getPlayingAdBreak());
        assertEquals("Should be playing ad-1b", "ad-1b", complexTracker.getPlayingAdBreak().getAd().getId());
        assertEquals("Should be at index 1", 1, complexTracker.getPlayingAdBreak().getAdIndex());

        setPlayerTime(17.0);
        waitForTracking();
        assertEquals("Should still be playing ad-1b", "ad-1b", complexTracker.getPlayingAdBreak().getAd().getId());

        // Phase 4: Content (20s-39s)
        System.out.println("\n=== Phase 4: Content (20s-39s) ===");
        setPlayerTime(20.0);
        waitForTracking();
        assertNull("Should not be playing ad at 20s", complexTracker.getPlayingAdBreak());

        setPlayerTime(30.0);
        waitForTracking();
        assertNull("Should not be playing ad at 30s", complexTracker.getPlayingAdBreak());

        // Phase 5: Ad Break 2, Ad 2a (40s-45s)
        System.out.println("\n=== Phase 5: Break 2, Ad 2a (40s-45s) ===");
        setPlayerTime(40.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 40s", complexTracker.getPlayingAdBreak());
        assertEquals("Should be playing break-2", "break-2", complexTracker.getPlayingAdBreak().getAdBreak().getId());
        assertEquals("Should be playing ad-2a", "ad-2a", complexTracker.getPlayingAdBreak().getAd().getId());
        assertEquals("Should be at index 0", 0, complexTracker.getPlayingAdBreak().getAdIndex());

        // Phase 6: Ad Break 2, Ad 2b (45s-50s)
        System.out.println("\n=== Phase 6: Break 2, Ad 2b (45s-50s) ===");
        setPlayerTime(45.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 45s", complexTracker.getPlayingAdBreak());
        assertEquals("Should be playing ad-2b", "ad-2b", complexTracker.getPlayingAdBreak().getAd().getId());
        assertEquals("Should be at index 1", 1, complexTracker.getPlayingAdBreak().getAdIndex());

        // Phase 7: Ad Break 2, Ad 2c (50s-55s)
        System.out.println("\n=== Phase 7: Break 2, Ad 2c (50s-55s) ===");
        setPlayerTime(50.0);
        waitForTracking();
        assertNotNull("Should be playing ad at 50s", complexTracker.getPlayingAdBreak());
        assertEquals("Should be playing ad-2c", "ad-2c", complexTracker.getPlayingAdBreak().getAd().getId());
        assertEquals("Should be at index 2", 2, complexTracker.getPlayingAdBreak().getAdIndex());

        setPlayerTime(52.0);
        waitForTracking();
        assertEquals("Should still be playing ad-2c", "ad-2c", complexTracker.getPlayingAdBreak().getAd().getId());

        // Phase 8: Content (55s+)
        System.out.println("\n=== Phase 8: Content (55s+) ===");
        setPlayerTime(55.0);
        waitForTracking();
        assertNull("Should not be playing ad at 55s", complexTracker.getPlayingAdBreak());

        setPlayerTime(60.0);
        waitForTracking();
        assertNull("Should not be playing ad at 60s", complexTracker.getPlayingAdBreak());

        complexTracker.stop();
        complexEmitter.stop();

        System.out.println("\n=== Test Complete: Multiple ad breaks with multiple ads ===");
    }
}
