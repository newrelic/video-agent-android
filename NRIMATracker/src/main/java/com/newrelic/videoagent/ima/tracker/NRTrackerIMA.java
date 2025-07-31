package com.newrelic.videoagent.ima.tracker;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.ima.BuildConfig;
import static com.newrelic.videoagent.core.NRDef.*;

public class NRTrackerIMA extends NRVideoTracker implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    private String adPosition = null;
    private String creativeId = null;
    private Long quartile = null;
    private String title = null;
    private Long bitrate = null;
    private Long renditionHeight = null;
    private Long renditionWidth = null;
    private Long duration = null;

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        if (adErrorEvent == null) return;

        NRLog.d("AdErrorEvent = " + adErrorEvent);
        sendError(adErrorEvent.getError());
    }

    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (adEvent == null) return;

        if (adEvent.getType() != AdEvent.AdEventType.AD_PROGRESS) {
            NRLog.d("AdEvent = " + adEvent);
        }

        fillAdAttributes(adEvent.getAd());

        switch (adEvent.getType()) {
            case CONTENT_PAUSE_REQUESTED:
                sendAdBreakStart();
                break;
            case CONTENT_RESUME_REQUESTED:
                sendAdBreakEnd();
                break;
            case STARTED:
                quartile = 0L;
                sendRequest();
                sendStart();
                break;
            case COMPLETED:
            case SKIPPED:
                sendEnd();
                quartile = null;
                break;
            case TAPPED:
            case CLICKED:
                sendAdClick();
                break;
            case FIRST_QUARTILE:
                quartile = 1L;
                sendAdQuartile();
                break;
            case MIDPOINT:
                quartile = 2L;
                sendAdQuartile();
                break;
            case THIRD_QUARTILE:
                quartile = 3L;
                sendAdQuartile();
                break;
            case PAUSED:
                sendPause();
                break;
            case RESUMED:
                sendResume();
                break;
        }
    }

    private void fillAdAttributes(Ad ad) {
        if (ad == null) return;

        switch (ad.getAdPodInfo().getPodIndex()) {
            case 0:
                adPosition = "pre";
            break;
            case -1:
                adPosition = "post";
            break;
            default:
                adPosition = "mid";
            break;
        }
        creativeId = ad.getCreativeId();
        title = ad.getTitle();
        bitrate = (long)ad.getVastMediaBitrate();
        renditionHeight = (long)ad.getVastMediaHeight();
        renditionWidth = (long)ad.getVastMediaWidth();
        duration = Math.max((long)ad.getDuration(), 0L);
    }

    /**
     * Get player name.
     *
     * @return Attribute.
     */
    public String getPlayerName() {
        return "IMA";
    }

    /**
     * Get tracker name.
     *
     * @return Atribute.
     */
    public String getTrackerName() {
        return "IMATracker";
    }

    /**
     * Get tracker version.
     *
     * @return Attribute.
     */
    public String getTrackerVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * Get tracker src.
     *
     * @return Attribute.
     */
    public String getTrackerSrc() {
        return SRC;
    }

    /**
     * Get Ad Position.
     *
     * @return Attribute.
     */
    @Override
    public String getAdPosition() {
        return adPosition;
    }

    /**
     * Get Ad Creative ID
     *
     * @return Attribute.
     */
    @Override
    public String getAdCreativeId() {
        return creativeId;
    }

    /**
     * Get Ad Quartile.
     *
     * @return Attribute.
     */
    @Override
    public Long getAdQuartile() {
        return quartile;
    }

    /**
     * Get title.
     *
     * @return Attribute.
     */
    @Override
    public String getTitle() {
        return title;
    }

    /**
     * Get bitrate.
     *
     * @return Attribute.
     */
    @Override
    public Long getBitrate() {
        return bitrate;
    }

    /**
     * Get rendition height.
     *
     * @return Attribute.
     */
    @Override
    public Long getRenditionHeight() {
        return renditionHeight;
    }

    /**
     * Get rendition width.
     *
     * @return Attribute.
     */
    @Override
    public Long getRenditionWidth() {
        return renditionWidth;
    }

    /**
     * Get duration.
     *
     * @return Attribute.
     */
    @Override
    public Long getDuration() {
        return duration;
    }
}
