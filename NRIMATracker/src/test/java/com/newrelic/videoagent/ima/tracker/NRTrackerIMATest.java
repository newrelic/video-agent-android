package com.newrelic.videoagent.ima.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.newrelic.videoagent.core.NRAdConfig;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drives {@link NRTrackerIMA} with fake IMA events and checks the events the core
 * state machine emits (action names), for client-side ads and Dynamic Ad Insertion.
 */
@RunWith(RobolectricTestRunner.class)
public class NRTrackerIMATest {

    /** Records the emitted ad events instead of sending them. */
    static class RecordingTracker extends NRTrackerIMA {
        final List<String> actions = new ArrayList<>();

        RecordingTracker(NRAdConfig adConfig) {
            super(new NRVideoConfiguration.Builder("TOKEN").build(), adConfig);
        }

        @Override
        public void sendVideoAdEvent(String action, Map<String, Object> attributes) {
            actions.add(action);
        }

        @Override
        public void sendVideoErrorEvent(String action, Map<String, Object> attributes) {
            actions.add(action);
        }
    }

    private RecordingTracker tracker;
    private NRVideoTracker content;

    private void init(NRAdConfig config) {
        tracker = new RecordingTracker(config);
        tracker.getState().isAd = true;
        content = new NRVideoTracker();
        tracker.linkedTracker = content;
    }

    @Before
    public void setUp() {
        init(NRAdConfig.csai());
    }

    @After
    public void tearDown() {
        tracker.dispose();
        content.dispose();
    }

    private static AdEvent event(AdEvent.AdEventType type, Ad ad) {
        AdEvent e = mock(AdEvent.class);
        when(e.getType()).thenReturn(type);
        when(e.getAd()).thenReturn(ad);
        return e;
    }

    private static Ad ad(int podIndex) {
        Ad ad = mock(Ad.class);
        AdPodInfo pod = mock(AdPodInfo.class);
        when(pod.getPodIndex()).thenReturn(podIndex);
        when(ad.getAdPodInfo()).thenReturn(pod);
        when(ad.getTitle()).thenReturn("title");
        when(ad.getCreativeId()).thenReturn("creative");
        return ad;
    }

    private void send(AdEvent.AdEventType type) {
        tracker.onAdEvent(event(type, null));
    }

    // ---- Client-side ads ----

    @Test
    public void csaiBreakWrapsTheAd() {
        send(AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED);
        tracker.onAdEvent(event(AdEvent.AdEventType.STARTED, ad(0)));
        send(AdEvent.AdEventType.FIRST_QUARTILE);
        send(AdEvent.AdEventType.MIDPOINT);
        send(AdEvent.AdEventType.THIRD_QUARTILE);
        send(AdEvent.AdEventType.COMPLETED);
        send(AdEvent.AdEventType.CONTENT_RESUME_REQUESTED);
        assertEquals("[AD_BREAK_START, AD_REQUEST, AD_START, AD_QUARTILE, AD_QUARTILE, AD_QUARTILE, AD_END, AD_BREAK_END]",
                tracker.actions.toString());
    }

    @Test
    public void csaiPauseResumeClickAndSkip() {
        tracker.onAdEvent(event(AdEvent.AdEventType.STARTED, ad(1)));
        send(AdEvent.AdEventType.PAUSED);
        send(AdEvent.AdEventType.RESUMED);
        send(AdEvent.AdEventType.CLICKED);
        send(AdEvent.AdEventType.TAPPED);
        send(AdEvent.AdEventType.SKIPPED);
        assertEquals("[AD_REQUEST, AD_START, AD_PAUSE, AD_RESUME, AD_CLICK, AD_CLICK, AD_END]",
                tracker.actions.toString());
    }

    @Test
    public void adPositionFollowsPodIndex() {
        tracker.onAdEvent(event(AdEvent.AdEventType.LOADED, ad(0)));
        assertEquals("pre", tracker.getAdPosition());
        tracker.onAdEvent(event(AdEvent.AdEventType.LOADED, ad(-1)));
        assertEquals("post", tracker.getAdPosition());
        tracker.onAdEvent(event(AdEvent.AdEventType.LOADED, ad(2)));
        assertEquals("mid", tracker.getAdPosition());
    }

    @Test
    public void nonLifecycleEventsAreIgnored() {
        for (AdEvent.AdEventType type : new AdEvent.AdEventType[]{
                AdEvent.AdEventType.LOADED, AdEvent.AdEventType.LOG, AdEvent.AdEventType.AD_PROGRESS,
                AdEvent.AdEventType.CUEPOINTS_CHANGED, AdEvent.AdEventType.ALL_ADS_COMPLETED,
                AdEvent.AdEventType.AD_BUFFERING, AdEvent.AdEventType.SKIPPABLE_STATE_CHANGED}) {
            send(type);
        }
        assertTrue(tracker.actions.isEmpty());
    }

    // ---- Dynamic Ad Insertion ----

    @Test
    public void daiBreakComesFromTheStreamEvents() {
        init(NRAdConfig.imaDai());
        send(AdEvent.AdEventType.AD_BREAK_STARTED);
        tracker.onAdEvent(event(AdEvent.AdEventType.STARTED, ad(0)));
        send(AdEvent.AdEventType.COMPLETED);
        send(AdEvent.AdEventType.AD_BREAK_ENDED);
        assertEquals("[AD_BREAK_START, AD_REQUEST, AD_START, AD_END, AD_BREAK_END]",
                tracker.actions.toString());
    }

    @Test
    public void daiOverlappingBreakSignalsDoNotDuplicate() {
        init(NRAdConfig.imaDai());
        send(AdEvent.AdEventType.AD_BREAK_STARTED);
        send(AdEvent.AdEventType.AD_PERIOD_STARTED);
        send(AdEvent.AdEventType.AD_PERIOD_ENDED);
        send(AdEvent.AdEventType.AD_BREAK_ENDED);
        assertEquals("[AD_BREAK_START, AD_BREAK_END]", tracker.actions.toString());
    }

    @Test
    public void daiNewBreakDoesNotReportThePreviousAd() {
        init(NRAdConfig.imaDai());
        tracker.onAdEvent(event(AdEvent.AdEventType.STARTED, ad(0)));
        assertEquals("title", tracker.getTitle());
        send(AdEvent.AdEventType.AD_BREAK_STARTED);
        assertEquals(null, tracker.getTitle());
        assertEquals(null, tracker.getAdPosition());
    }

    @Test
    public void eventWithoutAdOrPodInfoDoesNotCrash() {
        init(NRAdConfig.imaDai());
        Ad noPod = mock(Ad.class);
        tracker.onAdEvent(event(AdEvent.AdEventType.STARTED, noPod));
        send(AdEvent.AdEventType.COMPLETED);
        assertEquals("[AD_REQUEST, AD_START, AD_END]", tracker.actions.toString());
    }

    // ---- Errors ----

    @Test
    public void errorWithoutMessageDoesNotCrash() {
        AdError error = new AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.INTERNAL_ERROR, null);
        AdErrorEvent errorEvent = mock(AdErrorEvent.class);
        when(errorEvent.getError()).thenReturn(error);
        tracker.onAdError(errorEvent);
        assertEquals("[AD_ERROR]", tracker.actions.toString());
    }
}
