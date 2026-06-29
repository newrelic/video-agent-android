package com.newrelic.videoagent.core.qoe;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.newrelic.videoagent.core.NRDef.*;
import static org.junit.Assert.*;

/**
 * Isolation unit tests for {@link NRQoEAggregator}. The aggregator is pure Java (no Android,
 * player, or harvest dependencies), so it can be driven directly with hand-built attribute maps.
 *
 * Time-independent KPIs are asserted exactly; time-based KPIs (pause, switched-down, averageBitrate)
 * use the existing sleep + tolerance approach since the production code reads System clock directly.
 */
public class NRQoEAggregatorTest {

    private static final long SLEEP_MS = 60L;
    private static final long MIN_ELAPSED = 25L;

    private NRQoEAggregator agg;

    @Before
    public void setUp() {
        agg = new NRQoEAggregator();
    }

    // ---- helpers -----------------------------------------------------------

    private static Map<String, Object> attrs(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private void feed(String action, Map<String, Object> a) {
        agg.processAction(action, a, true, false);
    }

    private Map<String, Object> kpis() {
        return agg.generateAggregateAttributes(0L);
    }

    private static long lng(Map<String, Object> m, String key) {
        Object v = m.get(key);
        assertNotNull("KPI '" + key + "' should be present", v);
        return ((Number) v).longValue();
    }

    private void request() {
        feed(CONTENT_REQUEST, attrs());
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    // ---- gate --------------------------------------------------------------

    @Test
    public void returnsNullBeforeContentRequest() {
        assertNull("no KPIs before CONTENT_REQUEST", kpis());
    }

    @Test
    public void returnsMapAfterContentRequest() {
        request();
        Map<String, Object> k = kpis();
        assertNotNull(k);
        assertEquals(0L, lng(k, "totalSwitchUps"));
        assertEquals(0L, lng(k, "totalSwitchDowns"));
        assertEquals(0L, lng(k, "totalRenditions"));
        assertFalse(k.containsKey("avgDownloadRate"));
    }

    // ---- download rate -----------------------------------------------------

    @Test
    public void downloadRate_avgMinMax() {
        request();
        feed(CONTENT_HEARTBEAT, attrs("contentNetworkDownloadBitrate", 1000L));
        feed(CONTENT_HEARTBEAT, attrs("contentNetworkDownloadBitrate", 3000L));
        feed(CONTENT_HEARTBEAT, attrs("contentNetworkDownloadBitrate", 2000L));

        Map<String, Object> k = kpis();
        assertEquals(2000L, lng(k, "avgDownloadRate"));
        assertEquals(1000L, lng(k, "minDownloadRate"));
        assertEquals(3000L, lng(k, "maxDownloadRate"));
    }

    @Test
    public void downloadRate_ignoresNonPositive() {
        request();
        feed(CONTENT_HEARTBEAT, attrs("contentNetworkDownloadBitrate", 0L));
        feed(CONTENT_HEARTBEAT, attrs("contentNetworkDownloadBitrate", -5L));
        feed(CONTENT_HEARTBEAT, attrs("contentNetworkDownloadBitrate", 4000L));

        Map<String, Object> k = kpis();
        assertEquals(4000L, lng(k, "avgDownloadRate"));
    }

    // ---- peak bitrate ------------------------------------------------------

    @Test
    public void peakBitrate_tracksMax() {
        request();
        feed(CONTENT_HEARTBEAT, attrs("contentBitrate", 1000L));
        feed(CONTENT_HEARTBEAT, attrs("contentBitrate", 4000L));
        feed(CONTENT_HEARTBEAT, attrs("contentBitrate", 2000L));
        assertEquals(4000L, lng(kpis(), "peakBitrate"));
    }

    // ---- switch counts -----------------------------------------------------

    @Test
    public void switches_countUpsAndDowns() {
        request();
        feed(CONTENT_RENDITION_CHANGE, attrs("shift", "down"));
        feed(CONTENT_RENDITION_CHANGE, attrs("shift", "down"));
        feed(CONTENT_RENDITION_CHANGE, attrs("shift", "up"));

        Map<String, Object> k = kpis();
        assertEquals(1L, lng(k, "totalSwitchUps"));
        assertEquals(2L, lng(k, "totalSwitchDowns"));
    }

    // ---- distinct renditions ----------------------------------------------

    @Test
    public void renditions_countsDistinct() {
        request();
        feed(CONTENT_HEARTBEAT, attrs("contentRenditionWidth", 1920L, "contentRenditionHeight", 1080L));
        feed(CONTENT_HEARTBEAT, attrs("contentRenditionWidth", 1280L, "contentRenditionHeight", 720L));
        feed(CONTENT_HEARTBEAT, attrs("contentRenditionWidth", 1920L, "contentRenditionHeight", 1080L)); // dup
        assertEquals(2L, lng(kpis(), "totalRenditions"));
    }

    // ---- startup time (two exclusions + clamp) -----------------------------

    @Test
    public void startupTime_subtractsAdAndPause() {
        request();
        agg.addStartupPauseTime(500L);          // before start -> counted
        agg.setStartupAdTime(1000L);
        feed(CONTENT_START, attrs("timeSinceRequested", 5000L));
        // 5000 - 1000(ad) - 500(pause) = 3500
        assertEquals(3500L, lng(kpis(), "startupTime"));
    }

    @Test
    public void startupTime_clampedToZero() {
        request();
        agg.setStartupAdTime(9000L);
        feed(CONTENT_START, attrs("timeSinceRequested", 5000L));
        assertEquals(0L, lng(kpis(), "startupTime"));
    }

    @Test
    public void startupPauseTime_ignoredAfterStart() {
        request();
        agg.setStartupAdTime(0L);
        feed(CONTENT_START, attrs("timeSinceRequested", 5000L));
        agg.addStartupPauseTime(2000L);         // after start -> ignored
        assertEquals(5000L, lng(kpis(), "startupTime"));
    }

    // ---- rebuffering (skip-first) -----------------------------------------

    @Test
    public void rebuffering_skipsFirstBuffer() {
        request();
        feed(CONTENT_START, attrs("timeSinceRequested", 1000L));
        feed(CONTENT_BUFFER_END, attrs("timeSinceBufferBegin", 2000L)); // initial -> skipped
        feed(CONTENT_BUFFER_END, attrs("timeSinceBufferBegin", 3000L)); // counted
        feed(CONTENT_BUFFER_END, attrs("timeSinceBufferBegin", 1000L)); // counted
        assertEquals(4000L, lng(kpis(), "totalRebufferingTime"));
    }

    @Test
    public void rebufferingRatio_usesSuppliedPlaytime() {
        request();
        feed(CONTENT_START, attrs("timeSinceRequested", 0L));
        feed(CONTENT_BUFFER_END, attrs("timeSinceBufferBegin", 0L));    // skipped
        feed(CONTENT_BUFFER_END, attrs("timeSinceBufferBegin", 3000L)); // counted
        Map<String, Object> k = agg.generateAggregateAttributes(30000L);
        assertEquals(10.0, ((Number) k.get("rebufferingRatio")).doubleValue(), 0.0001);
        assertEquals(30000L, lng(k, "totalPlaytime"));
    }

    // ---- error flags -------------------------------------------------------

    @Test
    public void errorFlags() {
        request();
        Map<String, Object> k = kpis();
        assertFalse((Boolean) k.get("hadStartupError"));
        assertFalse((Boolean) k.get("hadPlaybackError"));

        agg.recordStartupError();
        agg.recordPlaybackError();
        k = kpis();
        assertTrue((Boolean) k.get("hadStartupError"));
        assertTrue((Boolean) k.get("hadPlaybackError"));
    }

    // ---- pause time (time-based, tolerance) --------------------------------

    @Test
    public void pauseTime_banksOnResume() {
        request();
        feed(CONTENT_START, attrs("timeSinceRequested", 0L));
        feed(CONTENT_PAUSE, attrs());
        sleep(SLEEP_MS);
        feed(CONTENT_RESUME, attrs());
        assertTrue(lng(kpis(), "totalPauseTime") >= MIN_ELAPSED);
    }

    @Test
    public void pauseTime_openSnapshotIsNonMutating() {
        request();
        feed(CONTENT_START, attrs("timeSinceRequested", 0L));
        feed(CONTENT_PAUSE, attrs());           // open, never resumed
        sleep(SLEEP_MS);
        long first = lng(kpis(), "totalPauseTime");
        sleep(SLEEP_MS);
        long second = lng(kpis(), "totalPauseTime");
        assertTrue("open pause keeps growing (" + first + " -> " + second + ")", second > first);
    }

    // ---- reset -------------------------------------------------------------

    @Test
    public void reset_clearsStateAndGate() {
        request();
        feed(CONTENT_RENDITION_CHANGE, attrs("shift", "up"));
        feed(CONTENT_HEARTBEAT, attrs("contentNetworkDownloadBitrate", 5000L));
        assertTrue(lng(kpis(), "totalSwitchUps") >= 1);

        agg.reset();
        assertNull("gate closed again after reset", kpis());

        request();
        Map<String, Object> k = kpis();
        assertEquals(0L, lng(k, "totalSwitchUps"));
        assertFalse(k.containsKey("avgDownloadRate"));
    }
}
