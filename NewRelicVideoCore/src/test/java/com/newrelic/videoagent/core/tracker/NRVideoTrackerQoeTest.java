package com.newrelic.videoagent.core.tracker;

import android.os.SystemClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the additional QoE KPIs added to {@link NRVideoTracker}.
 *
 * Covers the cross-agent reference algorithm from the spec:
 *   §5.8 Group D transition table (all 6 cells)
 *   §5.0 rule 5: snapshot-not-mutate on emit
 *   §5.0 rule 6: pause × rendition interaction
 *   §5.0 rule 9: exhaustive per-view reset
 *   §7 invariants 1, 2, 4, 7, 10
 */
@RunWith(RobolectricTestRunner.class)
public class NRVideoTrackerQoeTest {

    /** Subclass that lets the test pin getRenditionBitrate/Shift for the QoE hooks. */
    private static class TestTracker extends NRVideoTracker {
        Long pinnedRendition;
        String pinnedShift;
        @Override public Long getRenditionBitrate() { return pinnedRendition; }
        @Override public String getRenditionShift() { return pinnedShift; }
    }

    private TestTracker tracker;

    @Before
    public void setUp() {
        tracker = new TestTracker();
    }

    @After
    public void tearDown() {
        if (tracker != null) tracker.dispose();
    }

    // ====================================================================
    // §5.8 Group D transition table — all 6 cells.
    // ====================================================================

    @Test
    public void transitionTable_bGreaterThanM_noOpenInterval_setsMaxOnly() {
        tracker.pinnedRendition = 1_000_000L;
        tracker.onQoeContentStart();
        // M = 1_000_000, no open reduced interval

        tracker.onQoeRenditionChange("up", 2_000_000L);

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(2_000_000L, snap.get("maxRendition"));
        assertNull("no reduced interval should be open", snap.get("reducedSinceMs"));
        assertEquals(0L, snap.get("timeSwitchedDown"));
        assertEquals(1L, snap.get("switchUps"));
        assertEquals(0L, snap.get("switchDowns"));
    }

    @Test
    public void transitionTable_bGreaterThanM_withOpenInterval_closesAndAdvancesMax() {
        tracker.pinnedRendition = 5_000_000L;
        tracker.onQoeContentStart();
        long t0 = SystemClock.elapsedRealtime();

        tracker.onQoeRenditionChange("down", 1_000_000L); // opens reduced
        SystemClock.sleep(2_000);
        tracker.onQoeRenditionChange("up", 6_000_000L); // closes reduced AND advances max

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(6_000_000L, snap.get("maxRendition"));
        assertNull(snap.get("reducedSinceMs"));
        long elapsed = (Long) snap.get("timeSwitchedDown");
        assertTrue("expected ~2_000 ms accumulated, got " + elapsed,
                elapsed >= 2_000 && elapsed < 5_000);
    }

    @Test
    public void transitionTable_bEqualToM_noOpenInterval_isNoOp() {
        tracker.pinnedRendition = 3_000_000L;
        tracker.onQoeContentStart();
        Map<String, Object> before = tracker.qoeAccumulatorSnapshotForTest();

        // shift "none" → b == M, no open interval
        tracker.onQoeRenditionChange("none", 3_000_000L);

        Map<String, Object> after = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(before.get("maxRendition"), after.get("maxRendition"));
        assertEquals(before.get("timeSwitchedDown"), after.get("timeSwitchedDown"));
        assertNull(after.get("reducedSinceMs"));
    }

    @Test
    public void transitionTable_bEqualToM_withOpenInterval_closesIntervalOnly() {
        tracker.pinnedRendition = 5_000_000L;
        tracker.onQoeContentStart();

        tracker.onQoeRenditionChange("down", 2_000_000L); // open reduced
        SystemClock.sleep(1_000);
        tracker.onQoeRenditionChange("up", 5_000_000L); // b == M, close reduced (no max bump)

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(5_000_000L, snap.get("maxRendition"));
        assertNull(snap.get("reducedSinceMs"));
        long elapsed = (Long) snap.get("timeSwitchedDown");
        assertTrue("expected ~1_000 ms accumulated, got " + elapsed,
                elapsed >= 1_000 && elapsed < 4_000);
    }

    @Test
    public void transitionTable_bLessThanM_noOpenInterval_opensInterval() {
        tracker.pinnedRendition = 5_000_000L;
        tracker.onQoeContentStart();

        tracker.onQoeRenditionChange("down", 2_000_000L);

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(5_000_000L, snap.get("maxRendition"));
        assertNotNull("a reduced interval should now be open", snap.get("reducedSinceMs"));
        assertEquals(0L, snap.get("timeSwitchedDown"));
    }

    @Test
    public void transitionTable_bLessThanM_withOpenInterval_isNoOp() {
        tracker.pinnedRendition = 5_000_000L;
        tracker.onQoeContentStart();

        tracker.onQoeRenditionChange("down", 3_000_000L); // open at t0
        Object reducedT0 = tracker.qoeAccumulatorSnapshotForTest().get("reducedSinceMs");
        SystemClock.sleep(500);
        tracker.onQoeRenditionChange("down", 1_500_000L); // still below max, prev open

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals("interval start should not be reopened", reducedT0, snap.get("reducedSinceMs"));
        assertEquals("no time accumulated yet — interval still open", 0L, snap.get("timeSwitchedDown"));
        assertEquals("max stays at the all-time high", 5_000_000L, snap.get("maxRendition"));
    }

    // ====================================================================
    // §5.0 rule 5 — emitQoe() must NOT mutate intervals (heartbeat snapshot).
    // ====================================================================

    @Test
    public void emit_doesNotMutateOpenIntervals() {
        tracker.pinnedRendition = 5_000_000L;
        tracker.onQoeContentStart();
        tracker.onQoeRenditionChange("down", 1_000_000L); // open reduced
        tracker.onQoePause();                              // open pause

        Object reducedBefore = tracker.qoeAccumulatorSnapshotForTest().get("reducedSinceMs");
        Object pauseBefore   = tracker.qoeAccumulatorSnapshotForTest().get("pauseStartMs");

        SystemClock.sleep(750);
        Map<String, Object> kpis = tracker.qoeKpiAttributesForTest();
        long emittedReduced = (Long) kpis.get("totalTimeSwitchedDown");
        long emittedPause   = (Long) kpis.get("totalPauseTime");

        assertTrue("emit should reflect open reduced interval", emittedReduced >= 750);
        assertTrue("emit should reflect open pause interval", emittedPause >= 750);

        Map<String, Object> snapAfter = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals("reducedSinceMs must not move", reducedBefore, snapAfter.get("reducedSinceMs"));
        assertEquals("pauseStartMs must not move", pauseBefore, snapAfter.get("pauseStartMs"));
        assertEquals("timeSwitchedDown accumulator must not advance on emit",
                0L, snapAfter.get("timeSwitchedDown"));
        assertEquals("totalPauseTime accumulator must not advance on emit",
                0L, snapAfter.get("totalPauseTime"));
    }

    @Test
    public void emit_thenLaterRenditionChange_doesNotDoubleCount() {
        tracker.pinnedRendition = 5_000_000L;
        tracker.onQoeContentStart();
        tracker.onQoeRenditionChange("down", 1_000_000L); // open reduced

        SystemClock.sleep(500);
        tracker.qoeKpiAttributesForTest(); // mid-session emit
        SystemClock.sleep(500);
        tracker.onQoeRenditionChange("up", 5_000_000L); // close reduced — single accumulation

        long total = (Long) tracker.qoeAccumulatorSnapshotForTest().get("timeSwitchedDown");
        assertTrue("should accumulate ~1000ms total, got " + total,
                total >= 1_000 && total < 2_500);
    }

    // ====================================================================
    // §5.0 rule 6 — switched-down timer continues while paused.
    // ====================================================================

    @Test
    public void pauseDuringReducedInterval_doesNotPauseSwitchedDownTimer() {
        tracker.pinnedRendition = 5_000_000L;
        tracker.onQoeContentStart();
        tracker.onQoeRenditionChange("down", 1_000_000L); // open reduced

        SystemClock.sleep(300);
        tracker.onQoePause();
        SystemClock.sleep(700);   // 700ms paused
        tracker.onQoeResume();
        SystemClock.sleep(200);
        tracker.onQoeRenditionChange("up", 5_000_000L); // close reduced

        long switchedDown = (Long) tracker.qoeAccumulatorSnapshotForTest().get("timeSwitchedDown");
        long pauseTotal   = (Long) tracker.qoeAccumulatorSnapshotForTest().get("totalPauseTime");

        assertTrue("switched-down should span paused time too (~1200ms), got " + switchedDown,
                switchedDown >= 1_200 && switchedDown < 2_000);
        assertTrue("totalPauseTime should be ~700ms, got " + pauseTotal,
                pauseTotal >= 700 && pauseTotal < 1_500);
    }

    // ====================================================================
    // §5.6 / §7 invariant 4 — view end flushes intervals exactly once.
    // ====================================================================

    @Test
    public void viewEnd_flushesPauseAndReduced_exactlyOnce() {
        tracker.pinnedRendition = 5_000_000L;
        tracker.onQoeContentStart();
        tracker.onQoeRenditionChange("down", 1_000_000L);
        tracker.onQoePause();
        SystemClock.sleep(400);

        tracker.onQoeViewEnd();
        long firstReduced = (Long) tracker.qoeAccumulatorSnapshotForTest().get("timeSwitchedDown");
        long firstPause   = (Long) tracker.qoeAccumulatorSnapshotForTest().get("totalPauseTime");
        assertTrue("first flush captured the reduced interval", firstReduced >= 400);
        assertTrue("first flush captured the pause interval", firstPause >= 400);
        assertNull(tracker.qoeAccumulatorSnapshotForTest().get("reducedSinceMs"));
        assertNull(tracker.qoeAccumulatorSnapshotForTest().get("pauseStartMs"));

        SystemClock.sleep(400);
        tracker.onQoeViewEnd(); // second call should be a no-op

        assertEquals("second view-end must not double-count reduced",
                firstReduced, tracker.qoeAccumulatorSnapshotForTest().get("timeSwitchedDown"));
        assertEquals("second view-end must not double-count pause",
                firstPause, tracker.qoeAccumulatorSnapshotForTest().get("totalPauseTime"));
    }

    // ====================================================================
    // §7 invariant 1 — ad-scoped events are no-ops.
    // §7 invariant 2 — null/zero rendition never enters accumulators.
    // ====================================================================

    @Test
    public void adScope_renditionChangeIsNoOp() {
        tracker.getState().isAd = true;
        tracker.onQoeRenditionChange("up", 4_000_000L);
        tracker.onQoeRenditionChange("down", 1_000_000L);
        tracker.onQoeDownloadRateSample(1_000_000L);
        tracker.onQoePause();

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(0L, snap.get("switchUps"));
        assertEquals(0L, snap.get("switchDowns"));
        assertEquals(0L, snap.get("dlCount"));
        assertNull(snap.get("pauseStartMs"));
        assertEquals(0, snap.get("playedRenditionsSize"));
    }

    @Test
    public void nullOrZeroRendition_neverEntersAccumulators() {
        tracker.pinnedRendition = null;
        tracker.onQoeContentStart();
        tracker.onQoeRenditionChange("up", null);
        tracker.onQoeRenditionChange("up", 0L);

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(0, snap.get("playedRenditionsSize"));
        assertEquals(0L, snap.get("maxRendition"));
        assertEquals(0L, snap.get("switchUps"));
    }

    // ====================================================================
    // Group A — download-rate samples.
    // ====================================================================

    @Test
    public void downloadRate_sameValueRepeatedCountsEachSample() {
        for (int i = 0; i < 10; i++) tracker.onQoeDownloadRateSample(1_500_000L);
        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(10L, snap.get("dlCount"));
        assertEquals(15_000_000L, snap.get("dlSum"));
        assertEquals(1_500_000L, snap.get("dlMin"));
        assertEquals(1_500_000L, snap.get("dlMax"));
    }

    @Test
    public void downloadRate_alternatingSamples_avgMinMax() {
        tracker.onQoeDownloadRateSample(1_000_000L);
        tracker.onQoeDownloadRateSample(5_000_000L);
        tracker.onQoeDownloadRateSample(1_000_000L);
        tracker.onQoeDownloadRateSample(5_000_000L);

        Map<String, Object> kpis = tracker.qoeKpiAttributesForTest();
        assertEquals(3_000_000L, kpis.get("avgDownloadRate"));
        assertEquals(1_000_000L, kpis.get("minDownloadRate"));
        assertEquals(5_000_000L, kpis.get("maxDownloadRate"));
    }

    @Test
    public void downloadRate_omitsKeysWhenNoSampleArrived() {
        Map<String, Object> kpis = tracker.qoeKpiAttributesForTest();
        assertFalse("avgDownloadRate must be absent, not 0/null",
                kpis.containsKey("avgDownloadRate"));
        assertFalse(kpis.containsKey("minDownloadRate"));
        assertFalse(kpis.containsKey("maxDownloadRate"));
    }

    @Test
    public void downloadRate_rejectsNullAndNonPositive() {
        tracker.onQoeDownloadRateSample(null);
        tracker.onQoeDownloadRateSample(0L);
        tracker.onQoeDownloadRateSample(-42L);
        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(0L, snap.get("dlCount"));
    }

    // ====================================================================
    // Group F — distinct variants de-dups.
    // ====================================================================

    @Test
    public void variants_dedupRepeatedBitrate() {
        tracker.pinnedRendition = 1_000_000L;
        tracker.onQoeContentStart();
        tracker.onQoeRenditionChange("up", 2_000_000L);
        tracker.onQoeRenditionChange("down", 1_000_000L); // already in set
        tracker.onQoeRenditionChange("up", 2_000_000L);   // already in set

        long count = (Long) tracker.qoeKpiAttributesForTest().get("totalRenditions");
        assertEquals(2L, count);
    }

    // ====================================================================
    // Shift derivation — Android computes shift from previous rendition.
    // ====================================================================

    @Test
    public void computeQoeRenditionShift_returnsTriState() {
        assertEquals("up",   NRVideoTracker.computeQoeRenditionShift(1_000_000L, 2_000_000L));
        assertEquals("down", NRVideoTracker.computeQoeRenditionShift(2_000_000L, 1_000_000L));
        assertEquals("none", NRVideoTracker.computeQoeRenditionShift(1_000_000L, 1_000_000L));
        assertNull(NRVideoTracker.computeQoeRenditionShift(null, 2_000_000L));
        assertNull(NRVideoTracker.computeQoeRenditionShift(1_000_000L, null));
        assertNull(NRVideoTracker.computeQoeRenditionShift(1_000_000L, 0L));
    }

    // ====================================================================
    // Composite (bitrate, width, height) rendition identity — robust to DASH
    // streams whose Format.bitrate is uniform across resolutions.
    // ====================================================================

    // ====================================================================
    // Pixel-first rendition identity — robust to DASH streams whose
    // contentRenditionBitrate is uniform across resolutions. When width/height
    // are reported, the algorithm keys off pixels (W*H); otherwise it falls
    // back to bitrate. See renditionSignal().
    // Tests skip onQoeContentStart() because TestTracker doesn't override
    // getRenditionWidth/Height — its seed would key off bitrate-fallback and
    // muddy whole-state assertions about the rendition-change path.
    // ====================================================================

    @Test
    public void pixelSignal_resolutionFlipWithStuckBitrate_countsAsTwoVariants() {
        // Reproduces the production case: contentRenditionBitrate=932697 across
        // 720p → 360p → 720p, with the platform publishing shift up/down.
        tracker.onQoeRenditionChange("none", 932_697L, 1280L, 720L);
        tracker.onQoeRenditionChange("down", 932_697L, 640L, 360L);
        tracker.onQoeRenditionChange("up",   932_697L, 1280L, 720L);

        Map<String, Object> kpis = tracker.qoeKpiAttributesForTest();
        assertEquals("two distinct resolutions despite stuck bitrate",
                2L, kpis.get("totalRenditions"));
        assertEquals(1L, kpis.get("totalSwitchUps"));
        assertEquals(1L, kpis.get("totalSwitchDowns"));
    }

    @Test
    public void pixelSignal_sameResolutionDifferentBitrate_countsAsOneVariant() {
        // Pixel-only identity collapses same-resolution renditions. Documented
        // trade-off — switch counters still increment via published shift.
        tracker.onQoeRenditionChange("none", 1_000_000L, 1280L, 720L);
        tracker.onQoeRenditionChange("up",   2_000_000L, 1280L, 720L);

        long count = (Long) tracker.qoeKpiAttributesForTest().get("totalRenditions");
        assertEquals(1L, count);
        assertEquals(1L, tracker.qoeKpiAttributesForTest().get("totalSwitchUps"));
    }

    @Test
    public void pixelSignal_sameBitrateAndResolution_countsAsOneVariant() {
        tracker.onQoeRenditionChange("none", 1_500_000L, 1280L, 720L);
        tracker.onQoeRenditionChange("none", 1_500_000L, 1280L, 720L);
        tracker.onQoeRenditionChange("none", 1_500_000L, 1280L, 720L);

        long count = (Long) tracker.qoeKpiAttributesForTest().get("totalRenditions");
        assertEquals(1L, count);
    }

    @Test
    public void pixelSignal_maxAnchor_isHighestPixelCount() {
        // 360p (230_400 px) → 720p (921_600 px). Max anchor advances on pixels.
        tracker.onQoeRenditionChange("none", 5_000_000L, 640L, 360L);
        tracker.onQoeRenditionChange("up",   1_000_000L, 1280L, 720L);

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(1280L * 720L, snap.get("maxRendition"));
    }

    @Test
    public void pixelSignal_groupD_drivenByPixelsWhenBitrateIsStuck() {
        // Production scenario: 720p (max) → 360p (reduced) → 720p (recover).
        tracker.onQoeRenditionChange("none", 932_697L, 1280L, 720L);

        tracker.onQoeRenditionChange("down", 932_697L, 640L, 360L); // open reduced
        SystemClock.sleep(1_000);
        tracker.onQoeRenditionChange("up",   932_697L, 1280L, 720L); // close reduced

        long switchedDown = (Long) tracker.qoeAccumulatorSnapshotForTest().get("timeSwitchedDown");
        assertTrue("expected ~1_000 ms accumulated, got " + switchedDown,
                switchedDown >= 1_000 && switchedDown < 3_000);
    }

    // Production regression: when CONTENT_RENDITION_CHANGE is dispatched, the QoE hook
    // runs from inside super.getAttributes — which means derived-class additions to the
    // attribute map (including "shift") have NOT happened yet. The QoE hook must read
    // shift via the override hook (getRenditionShift), not via processedAttributes.
    @Test
    public void renditionChange_publishedShiftReadViaOverride_notViaMap() {
        tracker.pinnedShift = "down";

        // Simulate the exact map ExoPlayer's super.getAttributes hands us: rendition
        // info present, but "shift" NOT yet on the map (derived class adds it later).
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("contentRenditionBitrate", 932_697L);
        attrs.put("contentRenditionWidth",   854L);
        attrs.put("contentRenditionHeight",  480L);
        // Deliberately no "shift" key.

        tracker.trackBitrateFromProcessedAttributes("CONTENT_RENDITION_CHANGE", attrs);

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals("down published via override hook should increment switchDowns",
                1L, snap.get("switchDowns"));
        assertEquals(0L, snap.get("switchUps"));
        assertEquals(1, snap.get("playedRenditionsSize"));
    }

    @Test
    public void renditionChange_overrideShiftWins_evenWithStaleBitrateBaseline() {
        // Reproduces the exact failing prod data: contentRenditionBitrate stuck at 932697
        // across a 720p → 480p flip. Bitrate-derived fallback would say "none";
        // override-published shift "down" must win.
        tracker.pinnedRendition = 932_697L; // seeds qoePrevRenditionForShift
        tracker.onQoeContentStart();
        tracker.pinnedShift = "down";

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("contentRenditionBitrate", 932_697L);     // unchanged
        attrs.put("contentRenditionWidth",   854L);
        attrs.put("contentRenditionHeight",  480L);

        tracker.trackBitrateFromProcessedAttributes("CONTENT_RENDITION_CHANGE", attrs);

        Map<String, Object> snap = tracker.qoeAccumulatorSnapshotForTest();
        assertEquals(1L, snap.get("switchDowns"));
        assertEquals(0L, snap.get("switchUps"));
    }

    @Test
    public void renditionSignal_prefersPixelsWhenAvailable_elseBitrate() {
        assertEquals(1280L * 720L, NRVideoTracker.renditionSignal(1_000_000L, 1280L, 720L));
        assertEquals(1_000_000L,   NRVideoTracker.renditionSignal(1_000_000L, null, null));
        assertEquals(1_000_000L,   NRVideoTracker.renditionSignal(1_000_000L, 0L, 0L));
        assertEquals(1_000_000L,   NRVideoTracker.renditionSignal(1_000_000L, 1280L, null));
        assertEquals(0L,           NRVideoTracker.renditionSignal(null, null, null));
        assertEquals(0L,           NRVideoTracker.renditionSignal(0L, 0L, 0L));
        assertEquals(0L,           NRVideoTracker.renditionSignal(-1L, null, null));
    }

    // Regression: ExoPlayer publishes "shift" from VideoSize, which can flip
    // (720p → 360p → 720p) without contentRenditionBitrate changing. The published
    // value must win so Group B counters track real platform-observed switches.
    @Test
    public void selectQoeShift_prefersPublishedOverBitrateDerived() {
        // Published wins even when bitrate is unchanged.
        assertEquals("down", NRVideoTracker.selectQoeShift("down", 932_697L, 932_697L));
        assertEquals("up",   NRVideoTracker.selectQoeShift("up",   932_697L, 932_697L));
        assertEquals("none", NRVideoTracker.selectQoeShift("none", 932_697L, 932_697L));

        // Falls back to bitrate-derived when published is missing or not a recognized value.
        assertEquals("up",   NRVideoTracker.selectQoeShift(null, 1_000_000L, 2_000_000L));
        assertEquals("down", NRVideoTracker.selectQoeShift("",   2_000_000L, 1_000_000L));
        assertEquals("none", NRVideoTracker.selectQoeShift("garbage", 1_000_000L, 1_000_000L));
        assertEquals("up",   NRVideoTracker.selectQoeShift(42, 1_000_000L, 2_000_000L));
    }

    // ====================================================================
    // §7 invariant 10 — strict key omission, never null.
    // ====================================================================

    @Test
    public void emit_alwaysIncludesGroupBDFKeysWithZeroDefault() {
        Map<String, Object> kpis = tracker.qoeKpiAttributesForTest();

        assertTrue(kpis.containsKey("totalSwitchUps"));
        assertTrue(kpis.containsKey("totalSwitchDowns"));
        assertTrue(kpis.containsKey("totalTimeSwitchedDown"));
        assertTrue(kpis.containsKey("totalPauseTime"));
        assertTrue(kpis.containsKey("totalRenditions"));

        assertEquals(0L, kpis.get("totalSwitchUps"));
        assertEquals(0L, kpis.get("totalSwitchDowns"));
        assertEquals(0L, kpis.get("totalTimeSwitchedDown"));
        assertEquals(0L, kpis.get("totalPauseTime"));
        assertEquals(0L, kpis.get("totalRenditions"));
    }

    // ====================================================================
    // Integration scenario — three-rendition session low → high → low.
    // ====================================================================

    @Test
    public void integration_threeRenditionSession() {
        tracker.pinnedRendition = 1_000_000L;
        tracker.onQoeContentStart();
        // up
        tracker.onQoeRenditionChange("up", 5_000_000L);
        SystemClock.sleep(100);
        // down — opens reduced
        tracker.onQoeRenditionChange("down", 1_000_000L);
        SystemClock.sleep(2_000);
        // back up to max — closes reduced
        tracker.onQoeRenditionChange("up", 5_000_000L);

        tracker.onQoeViewEnd();
        Map<String, Object> kpis = tracker.qoeKpiAttributesForTest();

        assertEquals(2L, kpis.get("totalSwitchUps"));
        assertEquals(1L, kpis.get("totalSwitchDowns"));
        assertEquals(2L, kpis.get("totalRenditions"));
        long switchedDown = (Long) kpis.get("totalTimeSwitchedDown");
        assertTrue("expected ~2_000 ms switched-down, got " + switchedDown,
                switchedDown >= 2_000 && switchedDown < 4_000);
    }

    // ====================================================================
    // §5.0 rule 9 — exhaustive per-view reset (whole-state equality).
    // ====================================================================

    @Test
    public void resetForViewSession_zeroesAllNewKpiState() {
        // Drive into non-trivial state
        tracker.pinnedRendition = 5_000_000L;
        tracker.onQoeContentStart();
        tracker.onQoeRenditionChange("down", 2_000_000L);
        tracker.onQoeRenditionChange("up", 5_000_000L);
        tracker.onQoeDownloadRateSample(1_500_000L);
        tracker.onQoeDownloadRateSample(4_500_000L);
        tracker.onQoePause();
        SystemClock.sleep(200);
        tracker.onQoeResume();
        // Leave one open interval to make sure reset clears it too.
        tracker.onQoeRenditionChange("down", 1_000_000L);
        tracker.onQoePause();

        // Sanity: state is non-trivial before reset
        Map<String, Object> dirty = tracker.qoeAccumulatorSnapshotForTest();
        assertTrue((Long) dirty.get("dlCount") > 0);
        assertTrue((Long) dirty.get("switchDowns") > 0);
        assertNotNull(dirty.get("reducedSinceMs"));
        assertNotNull(dirty.get("pauseStartMs"));

        tracker.resetQoeMetrics();

        Map<String, Object> after = tracker.qoeAccumulatorSnapshotForTest();
        Map<String, Object> expected = freshSnapshot();
        assertEquals("whole-state equality after reset", expected, after);
    }

    private Map<String, Object> freshSnapshot() {
        Map<String, Object> e = new HashMap<>();
        e.put("dlSum", 0L);
        e.put("dlCount", 0L);
        e.put("dlMin", null);
        e.put("dlMax", 0L);
        e.put("switchUps", 0L);
        e.put("switchDowns", 0L);
        e.put("maxRendition", 0L);
        e.put("prevRenditionForShift", null);
        e.put("timeSwitchedDown", 0L);
        e.put("reducedSinceMs", null);
        e.put("totalPauseTime", 0L);
        e.put("pauseStartMs", null);
        e.put("playedRenditionsSize", 0);
        return e;
    }
}
