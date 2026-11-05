package com.newrelic.videoagent.ima.tracker;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.newrelic.videoagent.core.NRAdConfig;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.ima.BuildConfig;

import java.lang.reflect.Method;

import static com.newrelic.videoagent.core.NRDef.*;

/**
 * Tracker for Google IMA, for client-side ads (CSAI) and Dynamic Ad Insertion (DAI).
 * It only observes: the app keeps its own IMA listeners and the tracker is added next
 * to them (or called from them via {@link #handleAdEvent} / {@link #handleAdError}).
 */
public class NRTrackerIMA extends NRVideoTracker implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    /**
     * Create a new IMA tracker from the player's ad configuration.
     * {@link NRAdConfig#imaDai()} selects Dynamic Ad Insertion.
     */
    public NRTrackerIMA(NRVideoConfiguration configuration, NRAdConfig adConfig) {
        super(configuration);
        this.isDai = adConfig != null && adConfig.type == NRAdConfig.Type.IMA_DAI;
    }

    /**
     * Create a new IMA tracker with configuration.
     */
    public NRTrackerIMA(NRVideoConfiguration configuration) {
        super(configuration);
    }

    /**
     * Create a new IMA tracker (deprecated - use constructor with configuration).
     * @deprecated Use NRTrackerIMA(NRVideoConfiguration) constructor instead
     */
    @Deprecated
    public NRTrackerIMA() {
        super();
    }

    /** True for Dynamic Ad Insertion: breaks come from the stream's own ad break events. */
    private boolean isDai = false;
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
        AdError adError = adErrorEvent.getError();
        if (adError != null) {
            // sendError substitutes a placeholder for a null/empty message.
            sendError(adError.getErrorCodeNumber(), adError.getMessage());
        } else {
            sendError(null, "Unknown IMA error");
        }
    }

    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (adEvent == null) return;

        if (adEvent.getType() != AdEvent.AdEventType.AD_PROGRESS) {
            NRLog.d("AdEvent = " + adEvent);
        }

        AdEvent.AdEventType type = adEvent.getType();
        if (type == null) return;

        // DAI: a new break starts clean, so it does not report the previous break's ad.
        if (isDai && type == AdEvent.AdEventType.AD_BREAK_STARTED) {
            clearAdAttributes();
        }
        fillAdAttributes(adEvent.getAd());

        // Break start/end are idempotent in the core, so overlapping signals
        // (e.g. AD_BREAK_* and AD_PERIOD_*) do not produce duplicates.
        switch (type) {
            case CONTENT_PAUSE_REQUESTED:
            case AD_BREAK_STARTED:
            case AD_PERIOD_STARTED:
                sendAdBreakStart();
                break;
            case CONTENT_RESUME_REQUESTED:
            case AD_BREAK_ENDED:
            case AD_PERIOD_ENDED:
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
            // Known IMA events that are not part of the ad lifecycle we report today.
            case AD_BREAK_READY:
            case AD_BREAK_FETCH_ERROR:
            case AD_BUFFERING:
            case AD_PROGRESS:
            case ALL_ADS_COMPLETED:
            case CUEPOINTS_CHANGED:
            case ICON_FALLBACK_IMAGE_CLOSED:
            case ICON_TAPPED:
            case LOADED:
            case LOG:
            case SKIPPABLE_STATE_CHANGED:
            default:
                break;
        }
    }

    private void clearAdAttributes() {
        adPosition = null;
        creativeId = null;
        title = null;
        bitrate = null;
        renditionHeight = null;
        renditionWidth = null;
        duration = null;
    }

    private void fillAdAttributes(Ad ad) {
        if (ad == null) return;

        // DAI ads can come without pod info.
        if (ad.getAdPodInfo() != null) {
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
        }
        creativeId = ad.getCreativeId();
        title = ad.getTitle();
        bitrate = (long)ad.getVastMediaBitrate() * 1000;
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

    /**
     * Register this tracker as an AdEventListener and AdErrorListener with any IMA SDK object that supports it.
     */
    public void registerListeners(Object imaSdkObject) {
        if (imaSdkObject == null) return;
        // Skip ExoPlayer's ImaAdsLoader, which does not support these methods
        if (imaSdkObject.getClass().getName().contains("ImaAdsLoader")) return;
        try {
            Method addAdEventListener = imaSdkObject.getClass().getMethod("addAdEventListener", AdEvent.AdEventListener.class);
            addAdEventListener.invoke(imaSdkObject, this);
            Method addAdErrorListener = imaSdkObject.getClass().getMethod("addAdErrorListener", AdErrorEvent.AdErrorListener.class);
            addAdErrorListener.invoke(imaSdkObject, this);
        } catch (Exception e) {
            NRLog.d("NRTrackerIMA: Failed to register ad listeners: " + e);
        }
    }

    /**
     * Unregister this tracker as an AdEventListener and AdErrorListener from any IMA SDK object that supports it.
     */
    public void unregisterListeners(Object imaSdkObject) {
        if (imaSdkObject == null) return;
        if (imaSdkObject.getClass().getName().contains("ImaAdsLoader")) return;
        try {
            Method removeAdEventListener = imaSdkObject.getClass().getMethod("removeAdEventListener", AdEvent.AdEventListener.class);
            removeAdEventListener.invoke(imaSdkObject, this);
            Method removeAdErrorListener = imaSdkObject.getClass().getMethod("removeAdErrorListener", AdErrorEvent.AdErrorListener.class);
            removeAdErrorListener.invoke(imaSdkObject, this);
        } catch (Exception e) {
            NRLog.d("NRTrackerIMA: Failed to unregister ad listeners: " + e);
        }
    }

    // Allow for
    // warding from user-defined listeners

    public void handleAdEvent(AdEvent adEvent) {
        onAdEvent(adEvent);
    }
    public void handleAdError(AdErrorEvent adErrorEvent) {
        onAdError(adErrorEvent);
    }

    // Provide accessors for listeners so user can register them easily
    public AdEvent.AdEventListener getAdEventListener() {
        return this;
    }
    public AdErrorEvent.AdErrorListener getAdErrorListener() {
        return this;
    }
}
