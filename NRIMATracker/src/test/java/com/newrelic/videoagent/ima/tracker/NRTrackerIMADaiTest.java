package com.newrelic.videoagent.ima.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdProgressInfo;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.newrelic.videoagent.core.NRAdConfig;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for the IMA DAI path of {@link NRTrackerIMA}. Events are captured in
 * {@code preSend} (returning false, so nothing reaches the harvester); the content tracker
 * is linked the same way {@code NewRelicVideoAgent.start} links it.
 */
@RunWith(RobolectricTestRunner.class)
public class NRTrackerIMADaiTest {

    /** Ad tracker that records every action and its final attributes. */
    static class RecordingIMA extends NRTrackerIMA {
        final List<String> actions = new ArrayList<>();
        final List<Map<String, Object>> attrs = new ArrayList<>();

        RecordingIMA(NRAdConfig cfg) {
            super((NRVideoConfiguration) null, cfg);
        }

        @Override
        public boolean preSend(String action, Map<String, Object> attributes) {
            super.preSend(action, attributes);
            actions.add(action);
            attrs.add(new HashMap<>(attributes));
            return false; // do not record to the real harvester
        }

        Map<String, Object> last(String action) {
            for (int i = actions.size() - 1; i >= 0; i--) {
                if (actions.get(i).equals(action)) return attrs.get(i);
            }
            return null;
        }

        int count(String action) {
            int n = 0;
            for (String a : actions) if (a.equals(action)) n++;
            return n;
        }
    }

    private RecordingIMA daiTracker;
    private RecordingIMA csaiTracker;

    @Before
    public void setUp() {
        daiTracker = link(NRAdConfig.dai());
        csaiTracker = link(NRAdConfig.csai());
    }

    @After
    public void tearDown() {
        for (RecordingIMA t : Arrays.asList(daiTracker, csaiTracker)) {
            ((NRVideoTracker) t.linkedTracker).dispose();
            t.dispose();
        }
    }

    private static RecordingIMA link(NRAdConfig cfg) {
        RecordingIMA ad = new RecordingIMA(cfg);
        NRVideoTracker content = new NRVideoTracker();
        ad.getState().isAd = true;
        content.linkedTracker = ad;
        ad.linkedTracker = content;
        return ad;
    }

    private static Ad ad(int podIndex) {
        Ad ad = mock(Ad.class);
        AdPodInfo pod = mock(AdPodInfo.class);
        when(pod.getPodIndex()).thenReturn(podIndex);
        when(ad.getAdPodInfo()).thenReturn(pod);
        when(ad.getCreativeId()).thenReturn("creative-1");
        when(ad.getTitle()).thenReturn("Ad title");
        when(ad.getDuration()).thenReturn(15.0);
        return ad;
    }

    private static AdEvent event(AdEventType type, Ad ad) {
        AdEvent e = mock(AdEvent.class);
        when(e.getType()).thenReturn(type);
        when(e.getAd()).thenReturn(ad);
        return e;
    }

    private static AdErrorEvent errorEvent(AdError.AdErrorType type, int code, String msg) {
        AdErrorEvent e = mock(AdErrorEvent.class);
        when(e.getError()).thenReturn(new AdError(type, code, msg));
        return e;
    }

    // ---- config / creation ----

    @Test
    public void daiConfigIsDaiAndCsaiIsNot() {
        assertEquals(NRAdConfig.Type.DAI, NRAdConfig.dai().type);
        assertTrue(daiTracker.isDai());
        assertFalse(csaiTracker.isDai());
        assertEquals("NRAdConfig{dai}", NRAdConfig.dai().toString());
        assertFalse(NRAdConfig.dai().equals(NRAdConfig.csai()));
    }

    // ---- lifecycle ----

    @Test
    public void breakStartDoesNotCarryPreviousBreaksAdAttributes() {
        Ad pre = ad(0);
        daiTracker.handleStreamAdEvent(event(AdEventType.AD_BREAK_STARTED, null));
        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, pre));
        daiTracker.handleStreamAdEvent(event(AdEventType.COMPLETED, pre));
        daiTracker.handleStreamAdEvent(event(AdEventType.AD_BREAK_ENDED, null));
        daiTracker.handleStreamAdEvent(event(AdEventType.AD_BREAK_STARTED, null));

        Map<String, Object> breakStart = daiTracker.last("AD_BREAK_START");
        assertNull(daiTracker.getAdPosition());
        assertNull(daiTracker.getAdCreativeId());
        assertNull(daiTracker.getTitle());
        assertEquals("DAI", breakStart.get("adStreamType"));
    }

    @Test
    public void fullLifecycleEmitsSameVocabularyAsCsai() {
        Ad a = ad(0);
        daiTracker.handleStreamAdEvent(event(AdEventType.AD_BREAK_STARTED, null));
        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, a));
        daiTracker.handleStreamAdEvent(event(AdEventType.FIRST_QUARTILE, a));
        daiTracker.handleStreamAdEvent(event(AdEventType.MIDPOINT, a));
        daiTracker.handleStreamAdEvent(event(AdEventType.THIRD_QUARTILE, a));
        daiTracker.handleStreamAdEvent(event(AdEventType.COMPLETED, a));
        daiTracker.handleStreamAdEvent(event(AdEventType.AD_BREAK_ENDED, null));

        List<String> wanted = Arrays.asList("AD_BREAK_START", "AD_REQUEST", "AD_START",
                "AD_QUARTILE", "AD_END", "AD_BREAK_END");
        List<String> seen = new ArrayList<>();
        for (String s : daiTracker.actions) {
            if (wanted.contains(s)) seen.add(s);
        }
        assertEquals(Arrays.asList("AD_BREAK_START", "AD_REQUEST", "AD_START",
                "AD_QUARTILE", "AD_QUARTILE", "AD_QUARTILE", "AD_END", "AD_BREAK_END"), seen);

        Map<String, Object> start = daiTracker.last("AD_START");
        assertEquals("DAI", start.get("adStreamType"));
        assertEquals("pre", start.get("adPosition"));
        assertEquals("creative-1", start.get("adCreativeId"));
        assertEquals("Ad title", start.get("adTitle"));
        assertEquals(15L, ((Number) start.get("adDuration")).longValue());
        assertEquals(0L, start.get("adSkipped"));
    }

    @Test
    public void quartilesAreReportedInOrder() {
        Ad a = ad(1);
        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, a));
        assertEquals(0L, daiTracker.last("AD_START").get("adQuartile"));
        daiTracker.handleStreamAdEvent(event(AdEventType.FIRST_QUARTILE, a));
        assertEquals(1L, daiTracker.last("AD_QUARTILE").get("adQuartile"));
        daiTracker.handleStreamAdEvent(event(AdEventType.MIDPOINT, a));
        assertEquals(2L, daiTracker.last("AD_QUARTILE").get("adQuartile"));
        daiTracker.handleStreamAdEvent(event(AdEventType.THIRD_QUARTILE, a));
        assertEquals(3L, daiTracker.last("AD_QUARTILE").get("adQuartile"));
        assertEquals("mid", daiTracker.last("AD_QUARTILE").get("adPosition"));
    }

    @Test
    public void postrollPodIndexMapsToPost() {
        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, ad(-1)));
        assertEquals("post", daiTracker.last("AD_START").get("adPosition"));
    }

    // ---- skipped ----

    @Test
    public void skippedSetsFlagOnEndAndIsResetOnNextAd() {
        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, ad(1)));
        daiTracker.handleStreamAdEvent(event(AdEventType.SKIPPED, ad(1)));
        assertEquals(1, daiTracker.count("AD_END"));
        assertEquals(1L, daiTracker.last("AD_END").get("adSkipped"));

        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, ad(1)));
        assertEquals(0L, daiTracker.last("AD_START").get("adSkipped"));
        daiTracker.handleStreamAdEvent(event(AdEventType.COMPLETED, ad(1)));
        assertEquals(0L, daiTracker.last("AD_END").get("adSkipped"));
        assertEquals(2, daiTracker.count("AD_END"));
    }

    // ---- click ----

    @Test
    public void clickAndTapEmitAdClick() {
        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, ad(0)));
        daiTracker.handleStreamAdEvent(event(AdEventType.CLICKED, ad(0)));
        daiTracker.handleStreamAdEvent(event(AdEventType.TAPPED, ad(0)));
        assertEquals(2, daiTracker.count("AD_CLICK"));
    }

    // ---- ad break guard ----

    @Test
    public void duplicateBreakEventsAreIgnored() {
        daiTracker.handleStreamAdEvent(event(AdEventType.AD_BREAK_STARTED, null));
        daiTracker.handleStreamAdEvent(event(AdEventType.AD_BREAK_STARTED, null));
        daiTracker.sendAdBreakStart(); // app also calling it manually
        assertEquals(1, daiTracker.count("AD_BREAK_START"));
        daiTracker.handleStreamAdEvent(event(AdEventType.AD_BREAK_ENDED, null));
        daiTracker.handleStreamAdEvent(event(AdEventType.AD_BREAK_ENDED, null));
        assertEquals(1, daiTracker.count("AD_BREAK_END"));
    }

    @Test
    public void abandonedAdIsClosedBeforeNextAdStarts() {
        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, ad(1)));
        // seek over the midroll: no COMPLETED/SKIPPED for the first ad
        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, ad(1)));
        assertEquals(2, daiTracker.count("AD_START"));
        assertEquals(2, daiTracker.count("AD_REQUEST"));
        assertEquals(1, daiTracker.count("AD_END"));
    }

    // ---- errors ----

    @Test
    public void errorKeepsCodeMessageAndAddsStreamTypeAndErrorType() {
        daiTracker.handleStreamAdError(
                errorEvent(AdError.AdErrorType.LOAD, 1005, "Stream request failed"));
        Map<String, Object> err = daiTracker.last("AD_ERROR");
        assertEquals(1005, err.get("errorCode"));
        assertEquals("Stream request failed", err.get("errorMessage"));
        assertEquals("DAI", err.get("adStreamType"));
        assertEquals("LOAD", err.get("adErrorType"));
    }

    @Test
    public void nullErrorStillReportsUnknown() {
        daiTracker.handleStreamAdError(mock(AdErrorEvent.class));
        assertEquals("Unknown IMA error", daiTracker.last("AD_ERROR").get("errorMessage"));
        assertEquals("DAI", daiTracker.last("AD_ERROR").get("adStreamType"));
    }

    // ---- state retained after the event object is gone ----

    @Test
    public void adFieldsSurviveAfterEventIsDropped() {
        AdEvent started = event(AdEventType.STARTED, ad(0));
        daiTracker.handleStreamAdEvent(started);
        started = null;
        System.gc();
        assertEquals("Ad title", daiTracker.getTitle());
        assertEquals("creative-1", daiTracker.getAdCreativeId());
        assertEquals("pre", daiTracker.getAdPosition());
        assertEquals(Long.valueOf(15L), daiTracker.getDuration());
    }

    // ---- stream manager ----

    @Test
    public void registerStreamManagerAttachesListenersAndEnablesDai() {
        StreamManager sm = mock(StreamManager.class);
        assertFalse(csaiTracker.isDai());
        csaiTracker.registerStreamManager(sm);
        assertTrue(csaiTracker.isDai());
        verify(sm).addAdEventListener(csaiTracker);
        verify(sm).addAdErrorListener(csaiTracker);
        csaiTracker.unregisterStreamManager(sm);
        verify(sm).removeAdEventListener(csaiTracker);
        verify(sm).removeAdErrorListener(csaiTracker);
    }

    @Test
    public void playheadComesFromStreamManagerAdProgressInDaiOnly() {
        StreamManager sm = mock(StreamManager.class);
        AdProgressInfo info = mock(AdProgressInfo.class);
        when(info.getCurrentTime()).thenReturn(3.5);
        when(sm.getAdProgressInfo()).thenReturn(info);

        daiTracker.handleStreamAdEvent(event(AdEventType.STARTED, ad(0)), sm);
        assertEquals(Long.valueOf(3500L), daiTracker.getPlayhead());
        assertEquals(3500L, daiTracker.last("AD_START").get("adPlayhead"));

        assertNull(csaiTracker.getPlayhead());
    }

    // ---- CSAI unchanged ----

    @Test
    public void csaiPathHasNoDaiAttributesAndIgnoresAdBreakEvents() {
        Ad a = ad(0);
        csaiTracker.onAdEvent(event(AdEventType.AD_BREAK_STARTED, null));
        assertEquals(0, csaiTracker.count("AD_BREAK_START"));
        csaiTracker.onAdEvent(event(AdEventType.CONTENT_PAUSE_REQUESTED, null));
        csaiTracker.onAdEvent(event(AdEventType.STARTED, a));
        csaiTracker.onAdEvent(event(AdEventType.SKIPPED, a));
        csaiTracker.onAdEvent(event(AdEventType.CONTENT_RESUME_REQUESTED, null));
        csaiTracker.onAdError(errorEvent(AdError.AdErrorType.LOAD, 1009, "boom"));

        assertEquals(1, csaiTracker.count("AD_BREAK_START"));
        assertEquals(1, csaiTracker.count("AD_END"));
        assertEquals(1, csaiTracker.count("AD_BREAK_END"));
        for (Map<String, Object> m : csaiTracker.attrs) {
            assertFalse(m.containsKey("adStreamType"));
            assertFalse(m.containsKey("adErrorType"));
        }
        assertEquals(1009, csaiTracker.last("AD_ERROR").get("errorCode"));
    }

    @Test
    public void csaiSkippedSetsFlagOnEndAndIsResetOnNextAd() {
        csaiTracker.onAdEvent(event(AdEventType.STARTED, ad(0)));
        csaiTracker.onAdEvent(event(AdEventType.SKIPPED, ad(0)));
        assertEquals(1L, csaiTracker.last("AD_END").get("adSkipped"));

        csaiTracker.onAdEvent(event(AdEventType.STARTED, ad(0)));
        assertEquals(0L, csaiTracker.last("AD_START").get("adSkipped"));
        csaiTracker.onAdEvent(event(AdEventType.COMPLETED, ad(0)));
        assertEquals(0L, csaiTracker.last("AD_END").get("adSkipped"));
    }
}
