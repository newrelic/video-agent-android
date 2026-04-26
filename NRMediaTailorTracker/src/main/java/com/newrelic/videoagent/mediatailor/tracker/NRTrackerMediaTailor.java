package com.newrelic.videoagent.mediatailor.tracker;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsManifest;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.extractor.metadata.emsg.EventMessage;

import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.mediatailor.utils.ManifestParser;
import com.newrelic.videoagent.mediatailor.utils.MediaTailorConstants;
import com.newrelic.videoagent.mediatailor.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AWS MediaTailor Ad Tracker for ExoPlayer
 *
 * Tracks ads from AWS MediaTailor SSAI streams (HLS/DASH).
 *
 * Detection pipeline:
 * 1. ExoPlayer manifest hook (primary, zero extra HTTP) via player.getCurrentManifest()
 *    - HLS: VHS-style detection using segment URL + discontinuity sequence
 *    - LIVE: event-driven via onTimelineChanged() instead of polling
 * 2. DASH SCTE-35 emsg via onMetadata() callback
 * 3. HTTP manifest fetch as fallback
 * 4. Tracking API enrichment with tolerance-based deduplication
 */
public class NRTrackerMediaTailor extends NRVideoTracker implements Player.Listener {

    private static final String TAG = "NRMediaTailorTracker";
    private static final String MT_SEGMENT_PATTERN = MediaTailorConstants.MT_SEGMENT_PATTERN;

    private static final String STREAM_TYPE_VOD  = MediaTailorConstants.STREAM_TYPE_VOD;
    private static final String STREAM_TYPE_LIVE = MediaTailorConstants.STREAM_TYPE_LIVE;
    private static final String MANIFEST_TYPE_HLS  = MediaTailorConstants.MANIFEST_TYPE_HLS;
    private static final String MANIFEST_TYPE_DASH = MediaTailorConstants.MANIFEST_TYPE_DASH;
    private static final int  TRACKING_TIMEOUT_MS  = MediaTailorConstants.DEFAULT_TRACKING_API_TIMEOUT_MS;
    private static final long TIME_UPDATE_INTERVAL_MS = MediaTailorConstants.DEFAULT_TIME_UPDATE_INTERVAL_MS;

    // Disposal guard — set first in dispose(), checked at top of all async callbacks
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);

    protected ExoPlayer player;

    // Stream properties
    private String streamType;
    private String manifestType;
    private String mediaTailorEndpoint;
    private String trackingUrl;
    private String sessionId;

    // Ad tracking state
    private List<AdBreak> adSchedule = new ArrayList<>();
    private AdBreak currentAdBreak;
    private AdPod currentAdPod;

    // Configuration
    private boolean enableManifestParsing = MediaTailorConstants.DEFAULT_ENABLE_MANIFEST_PARSING;
    private boolean enableTrackingAPI     = MediaTailorConstants.DEFAULT_ENABLE_TRACKING_API;

    // Handlers and runnables
    private Handler  handler;
    private Runnable pollTrackingRunnable;
    private Runnable timeUpdateRunnable;

    // Live poll interval — updated dynamically from HLS targetDurationUs
    private long livePollIntervalMs = MediaTailorConstants.DEFAULT_LIVE_TRACKING_POLL_INTERVAL_MS;

    // -------------------------------------------------------------------------
    // Static factory helper
    // -------------------------------------------------------------------------

    /**
     * Returns true if the player's current media item is a MediaTailor URL.
     * Called via reflection by NRVideo.addPlayer() to auto-detect SSAI streams.
     */
    public static boolean isUsing(ExoPlayer player) {
        if (player == null || player.getCurrentMediaItem() == null
                || player.getCurrentMediaItem().localConfiguration == null) {
            return false;
        }
        String uri = player.getCurrentMediaItem().localConfiguration.uri.toString();
        return uri.contains(MediaTailorConstants.MT_URL_PATTERN);
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public NRTrackerMediaTailor() {
        handler = new Handler(Looper.getMainLooper());
    }

    public NRTrackerMediaTailor(ExoPlayer player) {
        this();
        setPlayer(player);
    }

    // -------------------------------------------------------------------------
    // NRVideoTracker lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void setPlayer(Object player) {
        Log.d(TAG, "setPlayer() called");
        this.player = (ExoPlayer) player;

        if (this.player.getCurrentMediaItem() != null
                && this.player.getCurrentMediaItem().localConfiguration != null) {
            this.mediaTailorEndpoint = this.player.getCurrentMediaItem().localConfiguration.uri.toString();
            this.manifestType  = detectManifestType(this.mediaTailorEndpoint);
            this.trackingUrl   = extractTrackingUrl(this.mediaTailorEndpoint);
            this.sessionId     = NetworkUtils.extractSessionId(this.mediaTailorEndpoint);

            Log.d(TAG, "MediaTailor tracker initialized — manifestType=" + manifestType
                    + " sessionId=" + sessionId + " trackingUrl=" + trackingUrl);

            registerListeners();

            // Handle case where player is already READY before setPlayer() was called
            if (this.player.getPlaybackState() == Player.STATE_READY && streamType == null) {
                long dur = this.player.getDuration();
                streamType = (dur == androidx.media3.common.C.TIME_UNSET)
                        ? STREAM_TYPE_LIVE : STREAM_TYPE_VOD;
                Log.d(TAG, "Player already READY, streamType=" + streamType);
                initializeTracking();
            }
        } else {
            Log.w(TAG, "No current media item when setPlayer() called");
        }

        super.setPlayer(player);
    }

    @Override
    public void registerListeners() {
        if (player != null) {
            player.addListener(this);
            startTimeUpdateMonitoring();
            Log.d(TAG, "Listeners registered");
        }
    }

    @Override
    public void unregisterListeners() {
        if (player != null) {
            player.removeListener(this);
            stopTimeUpdateMonitoring();
            stopTrackingPolling();
        }
    }

    // -------------------------------------------------------------------------
    // Player.Listener callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (isDisposed.get()) return;
        Log.d(TAG, "onPlaybackStateChanged: " + playbackState);

        if (playbackState == Player.STATE_READY && streamType == null) {
            long dur = player.getDuration();
            streamType = (dur == androidx.media3.common.C.TIME_UNSET)
                    ? STREAM_TYPE_LIVE : STREAM_TYPE_VOD;
            Log.d(TAG, "Stream type detected: " + streamType);
            initializeTracking();
        } else if (playbackState == Player.STATE_ENDED && currentAdBreak != null) {
            exitAdBreak();
        }
    }

    /**
     * Fires on every LIVE HLS manifest refresh — replaces HTTP polling for manifest parsing.
     */
    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
        if (isDisposed.get()) return;
        if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
            Log.d(TAG, "Timeline update (LIVE refresh) — re-parsing ExoPlayer manifest");
            tryExoPlayerManifestParse();
        }
    }

    /**
     * Fires for in-band metadata events — handles DASH SCTE-35 emsg boxes.
     */
    @Override
    public void onMetadata(Metadata metadata) {
        if (isDisposed.get()) return;
        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);
            if (entry instanceof EventMessage) {
                handleScte35Emsg((EventMessage) entry);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tracking initialization
    // -------------------------------------------------------------------------

    private void initializeTracking() {
        Log.d(TAG, "Initializing " + manifestType + " " + streamType + " tracking");

        // Primary: ExoPlayer manifest hook (zero extra HTTP)
        tryExoPlayerManifestParse();

        if (trackingUrl != null && enableTrackingAPI) {
            fetchTrackingMetadata();
            if (STREAM_TYPE_LIVE.equals(streamType)) {
                startTrackingPolling();
            }
        }
    }

    // -------------------------------------------------------------------------
    // ExoPlayer manifest hook (primary HLS detection path)
    // -------------------------------------------------------------------------

    /**
     * Primary ad detection path — reads the already-parsed manifest from ExoPlayer's
     * internal pipeline via player.getCurrentManifest(). Falls back to HTTP fetch
     * if ExoPlayer manifest is unavailable or yields no breaks.
     */
    private void tryExoPlayerManifestParse() {
        if (!enableManifestParsing || player == null) return;

        Object manifest = player.getCurrentManifest();

        if (manifest instanceof HlsManifest) {
            HlsMediaPlaylist playlist = ((HlsManifest) manifest).mediaPlaylist;
            if (playlist != null && !playlist.segments.isEmpty()) {
                Log.d(TAG, "Parsing HLS via ExoPlayer manifest (segments=" + playlist.segments.size() + ")");
                List<AdBreak> detected = detectAdsFromExoPlayerHls(playlist);
                if (!detected.isEmpty()) {
                    adSchedule = ManifestParser.mergeAdSchedules(adSchedule, detected);
                    Log.d(TAG, "ExoPlayer HLS: " + detected.size()
                            + " break(s) detected, schedule=" + adSchedule.size());
                    return; // Success — skip HTTP fallback
                }
            }
        }

        // Fallback: HTTP manifest fetch for HLS
        if (MANIFEST_TYPE_HLS.equals(manifestType)) {
            parseManifestFromHttp();
        }
        // DASH ad detection is event-driven via onMetadata() / handleScte35Emsg()
    }

    /**
     * VHS-style HLS ad detection using segment URL pattern + discontinuity sequence.
     * Detects MediaTailor-stitched ad segments directly without requiring CUE markers.
     * Equivalent to VideoJS's VHS discontinuity detection.
     *
     * Also updates livePollIntervalMs from the playlist's targetDurationUs.
     */
    private List<AdBreak> detectAdsFromExoPlayerHls(HlsMediaPlaylist playlist) {
        List<AdBreak> adBreaks = new ArrayList<>();
        if (playlist == null || playlist.segments.isEmpty()) return adBreaks;

        // Update live poll interval dynamically from HLS target duration
        if (playlist.targetDurationUs > 0) {
            livePollIntervalMs = playlist.targetDurationUs / 1000L;
        }

        AdBreak currentBreak = null;
        AdPod   currentPod   = null;
        int     prevDiscSeq  = -1;

        for (HlsMediaPlaylist.Segment seg : playlist.segments) {
            double startSec    = seg.relativeStartTimeUs / 1_000_000.0;
            double durationSec = seg.durationUs / 1_000_000.0;
            boolean isMt      = seg.url != null && seg.url.contains(MT_SEGMENT_PATTERN);
            boolean discontin = prevDiscSeq != -1
                    && seg.relativeDiscontinuitySequence != prevDiscSeq;

            if (isMt) {
                if (currentBreak == null) {
                    currentBreak = new AdBreak();
                    currentBreak.id = "avail-exo-" + startSec;
                    currentBreak.startTime = startSec;
                    currentBreak.source = MediaTailorConstants.AD_SOURCE_VHS_DISCONTINUITY;
                }

                if (currentPod == null || discontin) {
                    if (currentPod != null) {
                        currentPod.endTime = startSec;
                        currentBreak.pods.add(currentPod);
                    }
                    currentPod = new AdPod();
                    currentPod.startTime = startSec;
                    currentPod.duration = 0;
                }

                currentPod.duration += durationSec;
                currentBreak.endTime = startSec + durationSec;

            } else {
                if (currentBreak != null) {
                    if (currentPod != null) {
                        currentPod.endTime = currentBreak.endTime;
                        currentBreak.pods.add(currentPod);
                        currentPod = null;
                    }
                    currentBreak.duration = currentBreak.endTime - currentBreak.startTime;
                    if (currentBreak.duration >= MediaTailorConstants.MIN_AD_DURATION) {
                        adBreaks.add(currentBreak);
                    }
                    currentBreak = null;
                }
            }

            prevDiscSeq = seg.relativeDiscontinuitySequence;
        }

        // Handle ad break that extends to the end of the segment list (common in LIVE)
        if (currentBreak != null) {
            if (currentPod != null) {
                currentPod.endTime = currentBreak.endTime;
                currentBreak.pods.add(currentPod);
            }
            currentBreak.duration = currentBreak.endTime - currentBreak.startTime;
            if (currentBreak.duration >= MediaTailorConstants.MIN_AD_DURATION) {
                adBreaks.add(currentBreak);
            }
        }

        return adBreaks;
    }

    /**
     * Handle DASH SCTE-35 EventMessage (emsg) delivered via onMetadata().
     */
    private void handleScte35Emsg(EventMessage emsg) {
        if (emsg.schemeIdUri == null) return;
        String scheme = emsg.schemeIdUri.toLowerCase();
        if (!scheme.contains("scte35") && !scheme.contains("scte-35")) return;

        double startSec    = emsg.presentationTimeUs / 1_000_000.0;
        double durationSec = emsg.durationMs / 1000.0;

        Log.d(TAG, String.format("DASH SCTE-35 emsg: start=%.2fs dur=%.2fs scheme=%s",
                startSec, durationSec, emsg.schemeIdUri));

        if (durationSec < MediaTailorConstants.MIN_AD_DURATION) return;

        AdBreak adBreak = new AdBreak();
        adBreak.id = "avail-emsg-" + startSec;
        adBreak.startTime = startSec;
        adBreak.duration = durationSec;
        adBreak.endTime = startSec + durationSec;
        adBreak.source = MediaTailorConstants.AD_SOURCE_DASH_EMSG;

        List<AdBreak> emsgBreaks = new ArrayList<>();
        emsgBreaks.add(adBreak);
        adSchedule = ManifestParser.mergeAdSchedules(adSchedule, emsgBreaks);
        Log.d(TAG, "DASH emsg merged, schedule=" + adSchedule.size());
    }

    // -------------------------------------------------------------------------
    // HTTP manifest fallback
    // -------------------------------------------------------------------------

    /**
     * HTTP manifest fetch — fallback when ExoPlayer manifest hook yields no ad breaks.
     * Handles master → variant resolution transparently.
     */
    private void parseManifestFromHttp() {
        if (!enableManifestParsing || mediaTailorEndpoint == null) return;

        new Thread(() -> {
            if (isDisposed.get()) return;
            try {
                Log.d(TAG, "HTTP fallback: fetching " + mediaTailorEndpoint);
                String text = NetworkUtils.fetchText(mediaTailorEndpoint, TRACKING_TIMEOUT_MS);
                if (text == null) return;

                String target = text;
                if (ManifestParser.isMasterPlaylist(text)) {
                    String variantUrl = ManifestParser.extractFirstVariantUrl(text, mediaTailorEndpoint);
                    if (variantUrl != null) {
                        target = NetworkUtils.fetchText(variantUrl, TRACKING_TIMEOUT_MS);
                    }
                }

                if (target != null) {
                    List<AdBreak> httpBreaks = ManifestParser.parseHLSManifest(target);
                    if (!httpBreaks.isEmpty()) {
                        adSchedule = ManifestParser.mergeAdSchedules(adSchedule, httpBreaks);
                        Log.d(TAG, "HTTP manifest: " + httpBreaks.size() + " break(s)");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "HTTP manifest fallback error", e);
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Tracking API
    // -------------------------------------------------------------------------

    private void fetchTrackingMetadata() {
        if (trackingUrl == null) return;

        new Thread(() -> {
            if (isDisposed.get()) return;
            try {
                Log.d(TAG, "Fetching tracking API: " + trackingUrl);
                String response = NetworkUtils.fetchText(trackingUrl, TRACKING_TIMEOUT_MS);
                if (response != null) {
                    processTrackingResponse(response);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching tracking metadata", e);
            }
        }).start();
    }

    /**
     * Parse Tracking API avails and merge into ad schedule using deduplication.
     * Fixes the original bug where avails were blindly appended without dedup.
     */
    private void processTrackingResponse(String jsonData) {
        if (isDisposed.get()) return;
        try {
            JSONObject data = new JSONObject(jsonData);
            if (!data.has("avails")) return;

            JSONArray avails = data.getJSONArray("avails");
            Log.d(TAG, "Tracking API: " + avails.length() + " avail(s)");

            List<AdBreak> apiBreaks = new ArrayList<>();

            for (int i = 0; i < avails.length(); i++) {
                JSONObject avail = avails.getJSONObject(i);
                double startTime = avail.optDouble("startTimeInSeconds", 0);
                double duration  = avail.optDouble("durationInSeconds", 0);

                AdBreak adBreak = new AdBreak();
                adBreak.id = avail.optString("availId", "avail-" + i);
                adBreak.startTime = startTime;
                adBreak.duration  = duration;
                adBreak.endTime   = startTime + duration;
                adBreak.source    = MediaTailorConstants.AD_SOURCE_TRACKING_API;

                if (avail.has("ads")) {
                    JSONArray ads = avail.getJSONArray("ads");
                    for (int j = 0; j < ads.length(); j++) {
                        JSONObject ad = ads.getJSONObject(j);
                        AdPod pod = new AdPod();
                        pod.title      = ad.optString("adTitle", "");
                        pod.creativeId = ad.optString("creativeId", "");
                        pod.startTime  = ad.optDouble("startTimeInSeconds", 0);
                        pod.duration   = ad.optDouble("durationInSeconds", 0);
                        pod.endTime    = pod.startTime + pod.duration;
                        adBreak.pods.add(pod);
                    }
                }

                apiBreaks.add(adBreak);
            }

            // Use mergeAdSchedules() to deduplicate against manifest-detected breaks
            adSchedule = ManifestParser.mergeAdSchedules(adSchedule, apiBreaks);
            Log.d(TAG, "Tracking API merged, schedule=" + adSchedule.size() + " break(s)");

        } catch (Exception e) {
            Log.e(TAG, "Error processing tracking response", e);
        }
    }

    private void startTrackingPolling() {
        if (pollTrackingRunnable != null) return;
        pollTrackingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isDisposed.get()) return;
                fetchTrackingMetadata();
                handler.postDelayed(this, livePollIntervalMs);
            }
        };
        handler.postDelayed(pollTrackingRunnable, livePollIntervalMs);
        Log.d(TAG, "Tracking poll started (interval=" + livePollIntervalMs + "ms)");
    }

    private void stopTrackingPolling() {
        if (pollTrackingRunnable != null) {
            handler.removeCallbacks(pollTrackingRunnable);
            pollTrackingRunnable = null;
        }
    }

    // -------------------------------------------------------------------------
    // Time update monitoring (ad enter/exit/quartile detection)
    // -------------------------------------------------------------------------

    private void startTimeUpdateMonitoring() {
        if (timeUpdateRunnable != null) return;
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isDisposed.get()) {
                    if (player != null && player.isPlaying()) {
                        onTimeUpdate();
                    }
                    handler.postDelayed(this, TIME_UPDATE_INTERVAL_MS);
                }
            }
        };
        handler.postDelayed(timeUpdateRunnable, TIME_UPDATE_INTERVAL_MS);
    }

    private void stopTimeUpdateMonitoring() {
        if (timeUpdateRunnable != null) {
            handler.removeCallbacks(timeUpdateRunnable);
            timeUpdateRunnable = null;
        }
    }

    /**
     * Core ad detection loop — checks player position against ad schedule.
     * Equivalent to the VideoJS timeupdate handler in mt.js.
     */
    private void onTimeUpdate() {
        double currentTime = player.getCurrentPosition() / 1000.0;

        // Safety: exit ad break before CONTENT_END fires for VOD
        if (currentAdBreak != null && STREAM_TYPE_VOD.equals(streamType)) {
            long dur = player.getDuration();
            if (dur != androidx.media3.common.C.TIME_UNSET
                    && player.getCurrentPosition() >= dur - 100) {
                Log.d(TAG, "Near end of VOD — pre-emptively exiting ad break");
                exitAdBreak();
                return;
            }
        }

        AdBreak activeBreak = findActiveAdBreak(currentTime);

        if (activeBreak != null) {
            if (!activeBreak.hasFiredStart) {
                enterAdBreak(activeBreak);
            }
            if (currentAdBreak != null) {
                checkPodTransition(activeBreak, currentTime);
            }
        } else if (currentAdBreak != null) {
            exitAdBreak();
        }
    }

    private AdBreak findActiveAdBreak(double currentTime) {
        for (AdBreak adBreak : adSchedule) {
            if (currentTime >= adBreak.startTime && currentTime < adBreak.endTime) {
                return adBreak;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Ad break lifecycle
    // -------------------------------------------------------------------------

    private void enterAdBreak(AdBreak adBreak) {
        currentAdBreak = adBreak;
        getState().isAd = true;
        adBreak.adPosition = determineAdPosition(adSchedule.indexOf(adBreak), adSchedule.size());
        Log.d(TAG, "AD_BREAK_START id=" + adBreak.id + " pos=" + adBreak.adPosition);
        sendAdBreakStart();
        adBreak.hasFiredStart = true;
    }

    private void exitAdBreak() {
        if (currentAdPod != null) {
            sendEnd();
            currentAdPod = null;
        }
        Log.d(TAG, "AD_BREAK_END id=" + currentAdBreak.id);
        sendAdBreakEnd();
        currentAdBreak.hasFiredEnd = true;
        currentAdBreak = null;
        getState().isAd = false;
    }

    private void checkPodTransition(AdBreak adBreak, double currentTime) {
        if (adBreak.pods.isEmpty()) {
            // No pod metadata — treat entire break as a single ad
            if (!adBreak.hasFiredAdStart) {
                sendRequest();
                sendStart();
                adBreak.hasFiredAdStart = true;
            }
            trackQuartiles(adBreak, currentTime - adBreak.startTime);
            return;
        }

        AdPod activePod = null;
        for (AdPod pod : adBreak.pods) {
            if (currentTime >= pod.startTime && currentTime < pod.endTime) {
                activePod = pod;
                break;
            }
        }

        if (activePod != null && currentAdPod != activePod) {
            if (currentAdPod != null) {
                sendEnd();
            }
            currentAdPod = activePod;
            sendRequest();
            sendStart();
            activePod.hasFiredStart = true;
            Log.d(TAG, "AD_START pod=" + activePod.title);
        }

        if (currentAdPod != null) {
            trackQuartiles(currentAdPod, currentTime - currentAdPod.startTime);
        }
    }

    private void trackQuartiles(Object adObject, double progress) {
        double duration;
        boolean firedQ1, firedQ2, firedQ3;

        if (adObject instanceof AdPod) {
            AdPod pod = (AdPod) adObject;
            duration = pod.duration;
            firedQ1  = pod.hasFiredQ1;
            firedQ2  = pod.hasFiredQ2;
            firedQ3  = pod.hasFiredQ3;
        } else {
            AdBreak ab = (AdBreak) adObject;
            duration = ab.duration;
            firedQ1  = ab.hasFiredQ1;
            firedQ2  = ab.hasFiredQ2;
            firedQ3  = ab.hasFiredQ3;
        }

        if (duration <= 0) return;

        if (!firedQ1 && progress >= duration * MediaTailorConstants.QUARTILE_Q1) {
            sendAdQuartile();
            if (adObject instanceof AdPod) ((AdPod) adObject).hasFiredQ1 = true;
            else ((AdBreak) adObject).hasFiredQ1 = true;
        }
        if (!firedQ2 && progress >= duration * MediaTailorConstants.QUARTILE_Q2) {
            sendAdQuartile();
            if (adObject instanceof AdPod) ((AdPod) adObject).hasFiredQ2 = true;
            else ((AdBreak) adObject).hasFiredQ2 = true;
        }
        if (!firedQ3 && progress >= duration * MediaTailorConstants.QUARTILE_Q3) {
            sendAdQuartile();
            if (adObject instanceof AdPod) ((AdPod) adObject).hasFiredQ3 = true;
            else ((AdBreak) adObject).hasFiredQ3 = true;
        }
    }

    private String determineAdPosition(int index, int total) {
        if (index == 0) return "pre-roll";
        if (index == total - 1 && STREAM_TYPE_VOD.equals(streamType)) return "post-roll";
        return "mid-roll";
    }

    // -------------------------------------------------------------------------
    // URL utilities
    // -------------------------------------------------------------------------

    private String detectManifestType(String url) {
        if (url.contains(".m3u8")) return MANIFEST_TYPE_HLS;
        if (url.contains(".mpd") || url.contains("/dash")) return MANIFEST_TYPE_DASH;
        return MANIFEST_TYPE_HLS;
    }

    private String extractTrackingUrl(String url) {
        String sid = NetworkUtils.extractSessionId(url);
        return (sid != null) ? NetworkUtils.buildTrackingUrl(url, sid) : null;
    }

    // -------------------------------------------------------------------------
    // NRVideoTracker overrides
    // -------------------------------------------------------------------------

    @Override
    public String getTrackerName() {
        return "mediatailor";
    }

    @Override
    public String getTrackerVersion() {
        return "1.0.0";
    }

    @Override
    public String getPlayerName() {
        return "ExoPlayer";
    }

    @Override
    public String getPlayerVersion() {
        return androidx.media3.common.MediaLibraryInfo.VERSION;
    }

    @Override
    public String getTitle() {
        if (currentAdPod != null && currentAdPod.title != null) return currentAdPod.title;
        if (currentAdBreak != null && currentAdBreak.id != null) return currentAdBreak.id;
        return null;
    }

    @Override
    public String getSrc() {
        return mediaTailorEndpoint;
    }

    @Override
    public Long getDuration() {
        if (currentAdPod != null)   return (long) (currentAdPod.duration * 1000);
        if (currentAdBreak != null) return (long) (currentAdBreak.duration * 1000);
        return null;
    }

    @Override
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr = super.getAttributes(action, attributes);
        if (sessionId != null) attr.put("sessionId", sessionId);
        if (currentAdBreak != null && currentAdBreak.adPosition != null) {
            attr.put("adPosition", currentAdBreak.adPosition);
        }
        attr.put("adPartner", MediaTailorConstants.AD_PARTNER);
        if (streamType != null) attr.put("streamType", streamType);
        return attr;
    }

    /**
     * Clean up all resources. Sets isDisposed flag first to stop all async callbacks.
     */
    public void dispose() {
        isDisposed.set(true);
        Log.d(TAG, "Disposing NRTrackerMediaTailor");
        stopTimeUpdateMonitoring();
        stopTrackingPolling();
        unregisterListeners();
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /**
     * Represents an ad break (MediaTailor avail).
     */
    public static class AdBreak {
        public String id;
        public double startTime;
        public double duration;
        public double endTime;
        public String source;
        public String adPosition;
        public boolean hasFiredStart  = false;
        public boolean hasFiredEnd    = false;
        public boolean hasFiredAdStart = false;
        public boolean hasFiredQ1     = false;
        public boolean hasFiredQ2     = false;
        public boolean hasFiredQ3     = false;
        public List<AdPod> pods = new ArrayList<>();
    }

    /**
     * Represents an individual ad within a break.
     */
    public static class AdPod {
        public String title;
        public String creativeId;
        public double startTime;
        public double duration;
        public double endTime;
        public boolean hasFiredStart = false;
        public boolean hasFiredQ1   = false;
        public boolean hasFiredQ2   = false;
        public boolean hasFiredQ3   = false;
    }
}
