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
import java.util.Map;

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
    private static final String AD_PARTNER_DAI = "google-dai";
    private String adPosition = null;
    private String creativeId = null;
    private Long quartile = null;
    private String title = null;
    private Long bitrate = null;
    private Long renditionHeight = null;
    private Long renditionWidth = null;
    private Long duration = null;
    /** IMA's own id of the current ad; null before the first ad of a break or when IMA gives none. */
    private String adId = null;
    /** 1 when the current ad ended via SKIPPED, reset to 0 at every ad start. */
    private long adSkipped = 0L;

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
        // A break that opens with no ad yet must not report the previous ad's id (a Live DAI break
        // starts from period events, which skip the reset above). Not when a break is already open.
        if ((type == AdEvent.AdEventType.AD_BREAK_STARTED || type == AdEvent.AdEventType.AD_PERIOD_STARTED)
                && !getState().isAdBreak) {
            adId = null;
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
                endOpenAd();
                sendAdBreakEnd();
                break;
            case STARTED:
                quartile = 0L;
                adSkipped = 0L;
                sendRequest();
                sendStart();
                break;
            case COMPLETED:
            case SKIPPED:
                adSkipped = type == AdEvent.AdEventType.SKIPPED ? 1L : 0L;
                sendEnd();
                quartile = null;
                break;
            case TAPPED:
            case CLICKED:
                // Dropped when no ad was started (e.g. a live DAI join mid-ad): no AD_START, no adId.
                if (getState().isStarted) {
                    sendAdClick();
                }
                break;
            case FIRST_QUARTILE:
                // Dropped when no ad was started (e.g. a live DAI join mid-ad): no AD_START, no adId.
                if (getState().isStarted) {
                    quartile = 1L;
                    sendAdQuartile();
                }
                break;
            case MIDPOINT:
                // Dropped when no ad was started (e.g. a live DAI join mid-ad): no AD_START, no adId.
                if (getState().isStarted) {
                    quartile = 2L;
                    sendAdQuartile();
                }
                break;
            case THIRD_QUARTILE:
                // Dropped when no ad was started (e.g. a live DAI join mid-ad): no AD_START, no adId.
                if (getState().isStarted) {
                    quartile = 3L;
                    sendAdQuartile();
                }
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

    /**
     * An ad can be cut short (error, stream ended it) and the break ends with no COMPLETED or
     * SKIPPED. Close it first, so the ad does not stay open and keep sending heartbeats.
     */
    private void endOpenAd() {
        if (getState().isStarted) {
            sendEnd();
            quartile = null;
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
        adId = null;
        adSkipped = 0L;
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
        // Each ad takes its own id, so a missing one never keeps the previous ad's id.
        String id = ad.getAdId();
        adId = (id != null && !id.isEmpty()) ? id : null;
        // IMA reports seconds as a double; adDuration is in ms (DATAMODEL.md).
        duration = Math.max(Math.round(ad.getDuration() * 1000), 0L);
    }

    /**
     * Ad id: IMA's own id for the ad. Null when there is no ad yet (AD_BREAK_START) or IMA gives
     * none: the core's value would hash the empty source and repeat for every ad.
     */
    @Override
    public String getVideoId() {
        if (getState().isAd) {
            return adId;
        }
        return super.getVideoId();
    }

    @Override
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr = super.getAttributes(action, attributes);
        if (getState().isAd) {
            attr.put("adSkipped", adSkipped);
        }
        return attr;
    }

    /**
     * Ad source. For DAI the ad is part of the stitched stream the player plays, so it is that
     * stream's master playlist URL, as on web and iOS. Until the player has loaded the manifest
     * it is the content tracker's source (the Media3 request URI). Not set for client-side ads.
     */
    @Override
    public String getSrc() {
        if (!isDai || !(linkedTracker instanceof NRVideoTracker)) return null;
        String stream = stitchedStreamUrl();
        return stream != null ? stream : ((NRVideoTracker) linkedTracker).getSrc();
    }

    /**
     * Master playlist URL of the stream being played, from the player's current HLS manifest.
     * Reached by reflection: this module depends on neither the ExoPlayer tracker nor the HLS
     * module. Null when the player is not ExoPlayer, has no manifest yet, or anything differs.
     */
    private String stitchedStreamUrl() {
        try {
            Object player = linkedTracker.getClass().getMethod("getPlayer").invoke(linkedTracker);
            if (player == null) return null;
            Object manifest = player.getClass().getMethod("getCurrentManifest").invoke(player);
            if (manifest == null) return null;
            Object playlist = manifest.getClass().getField("multivariantPlaylist").get(manifest);
            if (playlist == null) return null;
            Object baseUri = playlist.getClass().getField("baseUri").get(playlist);
            return baseUri instanceof String && !((String) baseUri).isEmpty() ? (String) baseUri : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Ad partner: Google DAI. Not set for client-side ads. */
    @Override
    public String getAdPartner() {
        return isDai ? AD_PARTNER_DAI : null;
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
        // IMA cannot report a bitrate for stitched (DAI) ads: VAST media bitrate is 0.
        // Use the playing content rendition's bitrate when known. Not getBitrate(): on ExoPlayer that is
        // the measured segment download throughput, not the rendition being played.
        if (isDai && (bitrate == null || bitrate <= 0) && linkedTracker instanceof NRVideoTracker) {
            Long contentBitrate = ((NRVideoTracker) linkedTracker).getRenditionBitrate();
            if (contentBitrate != null && contentBitrate > 0) {
                return contentBitrate;
            }
        }
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
