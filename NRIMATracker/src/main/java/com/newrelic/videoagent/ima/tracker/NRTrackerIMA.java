package com.newrelic.videoagent.ima.tracker;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdProgressInfo;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.newrelic.videoagent.core.NRAdConfig;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.ima.BuildConfig;

import java.lang.reflect.Method;
import java.util.regex.Pattern;
import java.util.Map;

import static com.newrelic.videoagent.core.NRDef.*;

public class NRTrackerIMA extends NRVideoTracker implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    /**
     * Create a new IMA tracker with configuration.
     */
    public NRTrackerIMA(NRVideoConfiguration configuration) {
        super(configuration);
    }

    /**
     * Create a new IMA tracker for the given ad configuration. Created by
     * {@code NRVideo.addPlayer}: {@link NRAdConfig#dai()} puts the tracker in DAI mode,
     * anything else keeps the CSAI behaviour.
     */
    public NRTrackerIMA(NRVideoConfiguration configuration, NRAdConfig adConfig) {
        super(configuration);
        this.dai = adConfig != null && adConfig.type == NRAdConfig.Type.DAI;
    }

    /**
     * Create a new IMA tracker (deprecated - use constructor with configuration).
     * @deprecated Use NRTrackerIMA(NRVideoConfiguration) constructor instead
     */
    @Deprecated
    public NRTrackerIMA() {
        super();
    }

    private String adPosition = null;
    private String creativeId = null;
    private Long quartile = null;
    private String title = null;
    private Long bitrate = null;
    private Long renditionHeight = null;
    private Long renditionWidth = null;
    private Long duration = null;
    private String imaAdId = null;

    /** Value of the {@code adStreamType} attribute for IMA DAI events. */
    public static final String AD_STREAM_TYPE_DAI = "DAI";

    // DAI state. Plain strong references: unlike the iOS delegate, IMA Android hands us
    // objects that stay valid after the callback returns, but we cache the fields anyway.
    private volatile boolean dai = false;
    private volatile StreamManager streamManager = null;
    private volatile boolean skipped = false;
    private volatile String lastErrorType = null;

    /**
     * True when the tracker reports IMA DAI ads (set by {@link NRAdConfig#dai()}, by
     * {@link #registerStreamManager}, or by the first {@link #handleStreamAdEvent} call).
     */
    public boolean isDai() {
        return dai;
    }

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        if (adErrorEvent == null) return;

        NRLog.d("AdErrorEvent = " + adErrorEvent);
        AdError adError = adErrorEvent.getError();
        lastErrorType = (dai && adError != null && adError.getErrorType() != null)
                ? adError.getErrorType().name() : null;
        if (adError != null) {
            String message = adError.getMessage();
            sendError(adError.getErrorCodeNumber(), dai ? redactUrlQueries(message) : message);
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

        fillAdAttributes(adEvent.getAd());

        if (adEvent.getType() == null) return;
        switch (adEvent.getType()) {
            case AD_BREAK_STARTED:
                // DAI has no CONTENT_PAUSE/RESUME_REQUESTED; the break boundaries are these
                // events. CSAI behaviour is unchanged. sendAdBreakStart/End are idempotent
                // (state guarded), so an app that also calls them manually cannot double up.
                if (dai) {
                    // AD_BREAK_STARTED carries no Ad, so without this the break start would
                    // report the previous break's position, creative and title.
                    clearAdAttributes();
                    sendAdBreakStart();
                }
                break;
            case AD_BREAK_ENDED:
                if (dai) sendAdBreakEnd();
                break;
            case CONTENT_PAUSE_REQUESTED:
                sendAdBreakStart();
                break;
            case CONTENT_RESUME_REQUESTED:
                sendAdBreakEnd();
                break;
            case STARTED:
                if (dai) {
                    // A new ad started while the previous one never reported COMPLETED or
                    // SKIPPED (e.g. seek over a midroll): close it so the new ad gets
                    // its own AD_REQUEST/AD_START.
                    if (getState().isRequested) sendEnd();
                }
                skipped = false;
                quartile = 0L;
                sendRequest();
                sendStart();
                break;
            case SKIPPED:
                skipped = true;
                sendEnd();
                quartile = null;
                break;
            case COMPLETED:
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

    private static final Pattern URL_QUERY = Pattern.compile("(https?://[^\\s?\"'<>]+)\\?[^\\s\"'<>]*");

    /**
     * IMA DAI errors quote the stream request URL, whose query carries the advertising id,
     * correlator and session ids. Keeps scheme, host and path and drops the query.
     */
    static String redactUrlQueries(String message) {
        if (message == null) return null;
        return URL_QUERY.matcher(message).replaceAll("$1?<redacted>");
    }

    /**
     * DAI: IMA's own ad id. The core derives {@code adId} from the media src, which a
     * stitched ad does not have, so every ad would otherwise share one hash.
     */
    @Override
    public String getVideoId() {
        String id = imaAdId;
        return (dai && id != null && !id.isEmpty()) ? id : super.getVideoId();
    }

    private void clearAdAttributes() {
        imaAdId = null;
        adPosition = null;
        creativeId = null;
        title = null;
        bitrate = null;
        renditionHeight = null;
        renditionWidth = null;
        duration = null;
        quartile = null;
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
        if (dai) {
            imaAdId = ad.getAdId();
            // Stitched ads carry no VAST media info on some streams; report absence
            // rather than 0 / -1.
            int br = ad.getVastMediaBitrate();
            int h = ad.getVastMediaHeight();
            int w = ad.getVastMediaWidth();
            bitrate = br > 0 ? (long) br * 1000 : null;
            renditionHeight = h > 0 ? (long) h : null;
            renditionWidth = w > 0 ? (long) w : null;
        } else {
            bitrate = (long)ad.getVastMediaBitrate() * 1000;
            renditionHeight = (long)ad.getVastMediaHeight();
            renditionWidth = (long)ad.getVastMediaWidth();
        }
        duration = Math.max((long)ad.getDuration(), 0L);
    }

    /**
     * Ad-relative playhead in ms. DAI only: the content player's playhead is the stitched
     * stream, so the ad position comes from the StreamManager. CSAI keeps returning null.
     */
    @Override
    public Long getPlayhead() {
        StreamManager sm = streamManager;
        if (!dai || sm == null) return null;
        try {
            AdProgressInfo info = sm.getAdProgressInfo();
            if (info == null) return null;
            return Math.max((long) (info.getCurrentTime() * 1000.0), 0L);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Adds the DAI-only attributes just before the event is recorded: {@code adStreamType}
     * on every ad and ad error event, {@code adSkipped} on ad events and {@code adErrorType}
     * on errors. CSAI events are left untouched.
     */
    @Override
    public boolean preSend(String action, Map<String, Object> attributes) {
        if (attributes != null && action != null && action.startsWith("AD_") && !AD_ERROR.equals(action)) {
            // CSAI and DAI both report adSkipped (iOS parity); reset on every STARTED.
            attributes.put("adSkipped", skipped ? 1L : 0L);
        }
        if (dai && attributes != null) {
            if (action != null && action.startsWith("AD_")) {
                attributes.put("adStreamType", AD_STREAM_TYPE_DAI);
            }
            if (AD_ERROR.equals(action) && lastErrorType != null) {
                attributes.put("adErrorType", lastErrorType);
                attributes.put("errorName", errorNameFor(lastErrorType));
            }
        }
        return super.preSend(action, attributes);
    }

    /** Same names as the iOS tracker: IMA_AD_LOADING_FAILED / IMA_AD_PLAYING_FAILED / IMA_AD_UNKNOWN_ERROR. */
    static String errorNameFor(String adErrorType) {
        if ("LOAD".equals(adErrorType)) return "IMA_AD_LOADING_FAILED";
        if ("PLAY".equals(adErrorType)) return "IMA_AD_PLAYING_FAILED";
        return "IMA_AD_UNKNOWN_ERROR";
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

    // Allow forwarding from user-defined listeners

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

    // ---- IMA DAI (StreamManager) entry points ----

    /**
     * Forward an {@link AdEvent} received from a DAI {@link StreamManager}. Switches the
     * tracker to DAI mode, so it works even if the player was added with
     * {@code NRAdConfig.csai()}.
     */
    public void handleStreamAdEvent(AdEvent adEvent) {
        dai = true;
        onAdEvent(adEvent);
    }

    /** Same as {@link #handleStreamAdEvent(AdEvent)}, also remembering the manager for ad progress. */
    public void handleStreamAdEvent(AdEvent adEvent, StreamManager manager) {
        if (manager != null) streamManager = manager;
        handleStreamAdEvent(adEvent);
    }

    /** Forward an {@link AdErrorEvent} from a DAI stream request or {@link StreamManager}. */
    public void handleStreamAdError(AdErrorEvent adErrorEvent) {
        dai = true;
        onAdError(adErrorEvent);
    }

    /**
     * Attach this tracker to a DAI {@link StreamManager} (from {@code AdsManagerLoadedEvent
     * .getStreamManager()}). Registers the event and error listeners and uses the manager
     * for the ad-relative playhead. Call {@link #unregisterStreamManager} (or release the
     * tracker) when the stream is torn down.
     */
    public void registerStreamManager(StreamManager manager) {
        if (manager == null) return;
        dai = true;
        streamManager = manager;
        manager.addAdEventListener(this);
        manager.addAdErrorListener(this);
    }

    /** Remove the listeners added by {@link #registerStreamManager}. */
    public void unregisterStreamManager(StreamManager manager) {
        if (manager == null) return;
        manager.removeAdEventListener(this);
        manager.removeAdErrorListener(this);
        if (streamManager == manager) streamManager = null;
    }
}
