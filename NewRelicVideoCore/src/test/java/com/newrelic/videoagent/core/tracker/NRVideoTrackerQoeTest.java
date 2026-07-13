package com.newrelic.videoagent.core.tracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.newrelic.videoagent.core.NRVideoConfiguration;

import static com.newrelic.videoagent.core.NRDef.*;
import static org.junit.Assert.*;

/**
 * Unit tests for the QoE attributes added in qoeAggregateVersion 1.1.0:
 *   - avgDownloadRate / minDownloadRate / maxDownloadRate
 *   - totalSwitchUps / totalSwitchDowns
 *   - totalPauseTime (banks timeSincePaused; excludes ad-break pauses)
 *   - totalRenditions
 *
 * Strategy:
 *   - A test subclass overrides the player-data getters so getAttributes() populates
 *     real rendition / download values (the base getters return null).
 *   - Download-rate and rendition capture are driven through the real getAttributes()
 *     path. Switch and pause metrics are driven through the real event pipeline
 *     (sendVideoEvent / sendPause / sendResume) so the NRTracker.processQoeEvents()
 *     wiring is exercised too.
 *   - KPI values are read back from the extracted NRQoEAggregator via getQoeAggregator().
 *
 * Time-based assertions use small real sleeps with generous tolerance (the production
 * code uses System.currentTimeMillis()); this mirrors the existing playtime tests.
 */
@RunWith(RobolectricTestRunner.class)
public class NRVideoTrackerQoeTest {

    /** Lower bound applied to duration assertions to absorb scheduling jitter. */
    private static final long SLEEP_MS = 60L;
    private static final long MIN_ELAPSED = 25L;

    private QoeTestTracker tracker;

    @Before
    public void setUp() {
        tracker = new QoeTestTracker();
    }

    @After
    public void tearDown() {
        if (tracker != null) {
            tracker.dispose();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Test tracker exposing settable values for the data getters QoE reads. */
    static class QoeTestTracker extends NRVideoTracker {
        Long renditionWidth;
        Long renditionHeight;
        Long renditionBitrate;
        Long networkDownloadBitrate;

        QoeTestTracker() { super(); }
        QoeTestTracker(NRVideoConfiguration config) { super(config); }

        @Override public Long getRenditionWidth()        { return renditionWidth; }
        @Override public Long getRenditionHeight()       { return renditionHeight; }
        @Override public Long getRenditionBitrate()      { return renditionBitrate; }
        @Override public Long getNetworkDownloadBitrate(){ return networkDownloadBitrate; }
    }

    /**
     * Phase 1: KPIs now come from the extracted aggregator (via the package-private accessor).
     * Real-time playtime is supplied by the tracker in production; tests that don't exercise
     * playtime pass 0. Returns null before a CONTENT_REQUEST (the aggregator's gate), so tests
     * that read KPIs must establish a session (startContent() or requestOnly()) first.
     */
    private Map<String, Object> kpis() {
        return tracker.getQoeAggregator().generateAggregateAttributes(0L);
    }

    /** Open the aggregator gate without starting playback (feeds CONTENT_REQUEST). */
    private void requestOnly() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
    }

    private static long lng(Map<String, Object> m, String key) {
        Object v = m.get(key);
        assertNotNull("KPI '" + key + "' should be present", v);
        return ((Number) v).longValue();
    }

    private void startContent() {
        tracker.setPlayer(new Object());
        tracker.sendRequest();
        tracker.sendStart();
    }

    /** Drive one content event through the real pipeline so the onQoeEvent feed hook runs. */
    private void emitContentEvent() {
        tracker.sendVideoEvent(CONTENT_HEARTBEAT, null);
    }

    private void sendShift(String shift) {
        Map<String, Object> attrs = new HashMap<>();
        if (shift != null) {
            attrs.put("shift", shift);
        }
        tracker.sendVideoEvent(CONTENT_RENDITION_CHANGE, attrs);
    }

    /**
     * Drive a CONTENT_RENDITION_CHANGE carrying a rendition bitrate; requires the tracker to be
     * started so getAttributes() populates contentRenditionBitrate. Used to drive switch counts.
     */
    private void renditionChange(String shift, long bitrate) {
        tracker.renditionBitrate = bitrate;
        Map<String, Object> attrs = new HashMap<>();
        if (shift != null) {
            attrs.put("shift", shift);
        }
        tracker.sendVideoEvent(CONTENT_RENDITION_CHANGE, attrs);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    // =========================================================================
    // Defaults — counters/time present at zero, download omitted with no sample
    // =========================================================================

    @Test
    public void defaults_countersZero_downloadOmitted() {
        requestOnly();
        Map<String, Object> k = kpis();

        assertEquals(0L, lng(k, "totalSwitchUps"));
        assertEquals(0L, lng(k, "totalSwitchDowns"));
        assertEquals(0L, lng(k, "totalPauseTime"));
        assertEquals(0L, lng(k, "totalRenditions"));

        assertFalse("avgDownloadRate omitted with no sample", k.containsKey("avgDownloadRate"));
        assertFalse("minDownloadRate omitted with no sample", k.containsKey("minDownloadRate"));
        assertFalse("maxDownloadRate omitted with no sample", k.containsKey("maxDownloadRate"));
    }

    // =========================================================================
    // Download rate: avg / min / max
    // =========================================================================

    @Test
    public void downloadRate_avgMinMax() {
        startContent();

        tracker.networkDownloadBitrate = 1000L; emitContentEvent();
        tracker.networkDownloadBitrate = 3000L; emitContentEvent();
        tracker.networkDownloadBitrate = 2000L; emitContentEvent();

        Map<String, Object> k = kpis();
        assertEquals("avg = (1000+3000+2000)/3", 2000L, lng(k, "avgDownloadRate"));
        assertEquals(1000L, lng(k, "minDownloadRate"));
        assertEquals(3000L, lng(k, "maxDownloadRate"));
    }

    @Test
    public void downloadRate_avgRoundsHalfUp() {
        startContent();

        tracker.networkDownloadBitrate = 1000L; emitContentEvent();
        tracker.networkDownloadBitrate = 1001L; emitContentEvent();

        // 2001 / 2 = 1000.5 -> Math.round -> 1001
        assertEquals(1001L, lng(kpis(), "avgDownloadRate"));
    }

    @Test
    public void downloadRate_ignoresNonPositiveSamples() {
        startContent();

        tracker.networkDownloadBitrate = 0L;    emitContentEvent();   // skipped (<= 0)
        tracker.networkDownloadBitrate = -5L;   emitContentEvent();   // skipped (<= 0)
        tracker.networkDownloadBitrate = 4000L; emitContentEvent();   // counted

        Map<String, Object> k = kpis();
        assertEquals("only the positive sample counts", 4000L, lng(k, "avgDownloadRate"));
        assertEquals(4000L, lng(k, "minDownloadRate"));
        assertEquals(4000L, lng(k, "maxDownloadRate"));
    }

    @Test
    public void downloadRate_omittedWhenStartedButNoSample() {
        startContent();
        // networkDownloadBitrate stays null -> no samples
        emitContentEvent();

        Map<String, Object> k = kpis();
        assertFalse(k.containsKey("avgDownloadRate"));
        assertFalse(k.containsKey("minDownloadRate"));
        assertFalse(k.containsKey("maxDownloadRate"));
    }

    // =========================================================================
    // Rendition switch up / down counts
    // =========================================================================

    @Test
    public void switches_countUpsAndDowns() {
        requestOnly();
        sendShift("down");
        sendShift("down");
        sendShift("up");
        sendShift("up");

        Map<String, Object> k = kpis();
        assertEquals(2L, lng(k, "totalSwitchUps"));
        assertEquals(2L, lng(k, "totalSwitchDowns"));
    }

    @Test
    public void switches_missingOrUnknownShiftIgnored() {
        requestOnly();
        sendShift(null);          // no "shift" attribute
        sendShift("sideways");    // unrecognised value

        Map<String, Object> k = kpis();
        assertEquals(0L, lng(k, "totalSwitchUps"));
        assertEquals(0L, lng(k, "totalSwitchDowns"));
    }

    // =========================================================================
    // totalPauseTime
    // =========================================================================

    @Test
    public void pauseTime_banksOnResume() {
        startContent();

        tracker.sendPause();
        sleep(SLEEP_MS);
        tracker.sendResume();

        long t = lng(kpis(), "totalPauseTime");
        assertTrue("pause time banked (was " + t + ")", t >= MIN_ELAPSED);
    }

    @Test
    public void pauseTime_accumulatesAcrossMultiplePauses() {
        startContent();

        tracker.sendPause(); sleep(SLEEP_MS); tracker.sendResume();
        long afterFirst = lng(kpis(), "totalPauseTime");

        tracker.sendPause(); sleep(SLEEP_MS); tracker.sendResume();
        long afterSecond = lng(kpis(), "totalPauseTime");

        assertTrue(afterFirst >= MIN_ELAPSED);
        assertTrue("second pause adds on top (" + afterFirst + " -> " + afterSecond + ")",
                afterSecond >= afterFirst + MIN_ELAPSED);
    }

    @Test
    public void pauseTime_openPauseSnapshotIsNonMutating() {
        startContent();

        tracker.sendPause();        // open pause, never resumed
        sleep(SLEEP_MS);

        long first = lng(kpis(), "totalPauseTime");
        assertTrue("open pause reflected at emit (was " + first + ")", first >= MIN_ELAPSED);

        sleep(SLEEP_MS);
        long second = lng(kpis(), "totalPauseTime");
        assertTrue("open pause keeps growing (" + first + " -> " + second + ")", second > first);
    }

    // =========================================================================
    // totalRenditions (distinct width*height)
    // =========================================================================

    @Test
    public void renditions_countsDistinctResolutions() {
        startContent();

        tracker.renditionWidth = 1920L; tracker.renditionHeight = 1080L; emitContentEvent();
        tracker.renditionWidth = 1280L; tracker.renditionHeight = 720L;  emitContentEvent();
        tracker.renditionWidth = 1920L; tracker.renditionHeight = 1080L; emitContentEvent(); // duplicate

        assertEquals("two distinct resolutions", 2L, lng(kpis(), "totalRenditions"));
    }

    @Test
    public void renditions_ignoresNullOrZeroDimensions() {
        startContent();

        tracker.renditionWidth = null;  tracker.renditionHeight = 1080L; emitContentEvent(); // skipped
        tracker.renditionWidth = 1280L; tracker.renditionHeight = 0L;    emitContentEvent(); // skipped
        tracker.renditionWidth = 1280L; tracker.renditionHeight = 720L;  emitContentEvent(); // counted

        assertEquals(1L, lng(kpis(), "totalRenditions"));
    }

    // =========================================================================
    // Per-session harvest cadence (qoeCycleCount), independent of global harvest number
    // =========================================================================

    /**
     * The periodic-QoE gate must count this session's own polls (reset at CONTENT_REQUEST),
     * NOT the global harvest number passed in. We use multiplier=2 and pass a constant global
     * cycle (2) that the old global-parity formula {@code (harvestCycleNumber-1)%2} maps to
     * "skip" forever — so if the gate still used it, nothing would ever emit.
     */
    @Test
    public void cadence_gatesOnPerSessionPolls_notGlobalHarvestNumber() {
        NRVideoConfiguration config = new NRVideoConfiguration.Builder("token")
                .withQoeAggregateIntervalMultiplier(2)
                .build();
        QoeTestTracker t = new QoeTestTracker(config);
        t.setPlayer(new Object());
        t.sendRequest();   // CONTENT_REQUEST -> qoeCycleCount reset to 0, gate opened
        t.sendStart();

        final int GLOBAL = 2;                       // old formula: (2-1)%2==1 -> would never send
        final List<Map<String, Object>> batch = new ArrayList<>();

        // Session poll 1 -> emits (first poll always fires, regardless of global cycle).
        t.networkDownloadBitrate = 1000L; t.sendVideoEvent(CONTENT_HEARTBEAT, null);
        assertNotNull("session poll 1 must emit regardless of global harvest number",
                t.generateQoeIfNeeded(batch, GLOBAL));

        // Session poll 2 -> skipped by multiplier=2 (KPI still changed, so only cadence gates it).
        t.networkDownloadBitrate = 2000L; t.sendVideoEvent(CONTENT_HEARTBEAT, null);
        assertNull("session poll 2 skipped by multiplier",
                t.generateQoeIfNeeded(batch, GLOBAL));

        // Session poll 3 -> emits again.
        t.networkDownloadBitrate = 3000L; t.sendVideoEvent(CONTENT_HEARTBEAT, null);
        assertNotNull("session poll 3 emits",
                t.generateQoeIfNeeded(batch, GLOBAL));

        t.dispose();
    }

    // =========================================================================
    // Staleness: emitted totalPlaytime is fresh (extrapolated), not the cached snapshot
    // =========================================================================

    /**
     * The QOE envelope copies cached last-event attributes, which include a stale totalPlaytime.
     * iOS parity: the emitted totalPlaytime must be the fresh, extrapolated value
     * (computeRealtimePlaytimeMs = cached + elapsed-while-playing), not the snapshot.
     *
     * At CONTENT_START the cached totalPlaytime is 0; after playing for a while the emitted value
     * must reflect the elapsed top-up. Under the old (stale-wins) behavior this would emit 0.
     */
    @Test
    public void staleness_totalPlaytimeIsFreshNotCachedSnapshot() {
        NRVideoConfiguration config = new NRVideoConfiguration.Builder("token")
                .withQoeAggregateIntervalMultiplier(1)   // every poll qualifies
                .build();
        QoeTestTracker t = new QoeTestTracker(config);
        t.setPlayer(new Object());
        t.sendRequest();
        t.sendStart();                 // caches totalPlaytime=0, arms playtime top-up (isPlaying + timestamp)

        sleep(SLEEP_MS);               // time passes while playing -> fresh playtime > cached snapshot (0)

        Map<String, Object> qoe = t.generateQoeIfNeeded(new ArrayList<>(), 1);
        assertNotNull(qoe);
        long emitted = lng(qoe, "totalPlaytime");
        assertTrue("emitted totalPlaytime must be the fresh extrapolated value, not the stale cached "
                + "snapshot (was " + emitted + ")", emitted >= MIN_ELAPSED);

        t.dispose();
    }

    // =========================================================================
    // Error basis: startup vs playback decided by hasContentStarted (iOS parity),
    // not totalPlaytime > 0
    // =========================================================================

    @Test
    public void errorBasis_beforeContentStartIsStartupError() {
        requestOnly();                              // CONTENT_REQUEST only — content not started
        tracker.sendError(new Exception("boom"));

        Map<String, Object> k = kpis();
        assertTrue((Boolean) k.get("hadStartupError"));
        assertFalse((Boolean) k.get("hadPlaybackError"));
    }

    /**
     * The discriminating case for the fix: after CONTENT_START but before any playtime has
     * accrued (totalPlaytime == 0). New basis (hasContentStarted) => playback error. The old
     * basis (totalPlaytime > 0) would have misclassified this as a startup error.
     */
    @Test
    public void errorBasis_afterStartIsPlaybackError_evenWithZeroPlaytime() {
        startContent();                             // hasContentStarted = true, totalPlaytime still 0
        tracker.sendError(new Exception("boom"));

        Map<String, Object> k = kpis();
        assertFalse((Boolean) k.get("hadStartupError"));
        assertTrue((Boolean) k.get("hadPlaybackError"));
    }

    // =========================================================================
    // Reset per view (sendEnd -> aggregator.reset())
    // =========================================================================

    @Test
    public void reset_clearsAllQoeStateOnNewView() {
        startContent();

        // Accumulate every metric.
        tracker.networkDownloadBitrate = 5000L; emitContentEvent();
        tracker.renditionWidth = 1920L; tracker.renditionHeight = 1080L; emitContentEvent();
        renditionChange("up",   6_000_000L);   // peak = 6M, switchUps=1
        renditionChange("down", 4_000_000L);   // below peak -> open interval, switchDowns=1
        tracker.sendPause(); sleep(SLEEP_MS); tracker.sendResume();
        sleep(SLEEP_MS);

        // Sanity: state accumulated.
        Map<String, Object> before = kpis();
        assertEquals(1L, lng(before, "totalSwitchUps"));
        assertEquals(1L, lng(before, "totalSwitchDowns"));
        assertTrue(lng(before, "totalPauseTime") > 0);
        assertTrue(lng(before, "totalRenditions") >= 1);
        assertTrue(before.containsKey("avgDownloadRate"));

        // End the view -> aggregator.reset() (closes the gate too).
        tracker.sendEnd();
        assertNull("gate closed after view end", kpis());

        // A fresh view (new CONTENT_REQUEST) starts clean. Clear the player-data getters so the
        // new session carries no rendition/download samples yet.
        tracker.renditionWidth = null;
        tracker.renditionHeight = null;
        tracker.renditionBitrate = null;
        tracker.networkDownloadBitrate = null;
        tracker.sendRequest();
        Map<String, Object> after = kpis();
        assertEquals(0L, lng(after, "totalSwitchUps"));
        assertEquals(0L, lng(after, "totalSwitchDowns"));
        assertEquals(0L, lng(after, "totalPauseTime"));
        assertEquals(0L, lng(after, "totalRenditions"));
        assertFalse("download rate cleared", after.containsKey("avgDownloadRate"));
    }
}
