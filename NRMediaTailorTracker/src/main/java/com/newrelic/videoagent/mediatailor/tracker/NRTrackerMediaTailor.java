package com.newrelic.videoagent.mediatailor.tracker;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.hls.HlsManifest;
import androidx.media3.extractor.metadata.emsg.EventMessage;

import com.newrelic.videoagent.core.NRAdConfig;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.mediatailor.BuildConfig;
import com.newrelic.videoagent.mediatailor.MTConstants;
import com.newrelic.videoagent.mediatailor.detection.MTDashParser;
import com.newrelic.videoagent.mediatailor.detection.MTDetector;
import com.newrelic.videoagent.mediatailor.detection.MTHlsParser;
import com.newrelic.videoagent.mediatailor.model.MTAdBreak;
import com.newrelic.videoagent.mediatailor.model.MTAdPod;
import com.newrelic.videoagent.mediatailor.net.MTTrackingClient;
import com.newrelic.videoagent.mediatailor.net.MTTrackingResponse;
import com.newrelic.videoagent.mediatailor.schedule.MTAdScheduleMerger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AWS Elemental MediaTailor SSAI tracker for Media3 ExoPlayer.
 *
 * <p>Lives in the ad-tracker slot of an {@code NRTrackerPair}. Its
 * {@code state.isAd} flag is set to {@code true} by
 * {@code NewRelicVideoAgent.start(...)}, so every {@code sendStart/sendEnd/
 * sendAdBreakStart/...} call produces {@code VideoAdAction} events, and the
 * base class automatically pauses/resumes the linked content tracker at
 * break boundaries.</p>
 *
 * <p>Event production pipeline:</p>
 * <ol>
 *   <li>{@code onMediaItemTransition} / {@code onTimelineChanged} — detect
 *       MediaTailor URIs ({@link MTDetector#isMediaTailorUri}), pick parser
 *       (DASH / HLS) from the file extension, and derive the tracking URL
 *       either from the URI query or from {@code DashManifest.location}.</li>
 *   <li>Typed manifest parsing —
 *       {@link MTDashParser#parse(DashManifest)} or
 *       {@link MTHlsParser#parse(HlsManifest)} — builds an in-memory schedule
 *       of {@link MTAdBreak}s.</li>
 *   <li>Tracking API — {@link MTTrackingClient} fetches rich VAST metadata
 *       (titles, creative IDs, ad system, skip offsets, beacon URLs).
 *       {@link MTAdScheduleMerger} merges manifest + tracking data.</li>
 *   <li>250 ms playhead poll — compares the player's current position to the
 *       schedule, fires {@code AD_BREAK_START / AD_REQUEST / AD_START /
 *       AD_QUARTILE / AD_END / AD_BREAK_END} at the right transitions, and
 *       (optionally) fires VAST beacons client-side via {@link MTBeaconFirer}.</li>
 * </ol>
 *
 * <p>Public API surfaces for app integration:</p>
 * <ul>
 *   <li>{@link #setTrackingUrl(String)} — plug in a tracking URL obtained
 *       from an external session-init flow.</li>
 *   <li>{@link #notifyAdSkipped()} — call from the app's "Skip ad" button.</li>
 * </ul>
 */
@OptIn(markerClass = UnstableApi.class)
public class NRTrackerMediaTailor extends NRVideoTracker implements Player.Listener {

    private ExoPlayer player;

    // Set at construction time from NRAdConfig; null means no custom prefix.
    private final String segmentPrefix;
    // True when the customer explicitly passed NRAdConfig.mediaTailor() —
    // activation is unconditional (no URL substring check required).
    private final boolean explicitlyConfigured;

    private boolean activated;
    private String manifestType;
    private String streamType;
    private String mediaTailorEndpoint;
    private String trackingUrl;

    private final List<MTAdBreak> adSchedule = Collections.synchronizedList(new ArrayList<MTAdBreak>());
    private MTAdBreak currentAdBreak;
    private MTAdPod currentAdPod;
    private Long currentQuartile;

    private final AtomicBoolean isDisposed = new AtomicBoolean(false);
    private final AtomicBoolean hasAttemptedTrackingFetch = new AtomicBoolean(false);

    private Handler pollHandler;
    private Runnable pollRunnable;
    private MTTrackingClient trackingClient;
    private Thread trackingWorker;

    private int nonLinearAvailsCount = 0;

    /**
     * Primary constructor — called by {@link com.newrelic.videoagent.core.NRVideo#addPlayer}
     * when the customer passes {@link NRAdConfig#mediaTailor()}.
     *
     * <p>Reads {@code segmentPrefix} and {@code trackingUrl} from the config so
     * both are available before the first manifest parse.</p>
     */
    public NRTrackerMediaTailor(NRVideoConfiguration configuration, NRAdConfig adConfig) {
        super(configuration);
        this.segmentPrefix        = adConfig != null ? adConfig.segmentPrefix : null;
        this.explicitlyConfigured = adConfig != null;
        if (adConfig != null && adConfig.trackingUrl != null) {
            this.trackingUrl = adConfig.trackingUrl;
        }
        NRLog.d(MTConstants.LOG_CONFIG + " tracker created — " + adConfig
                + " explicitActivation=true"
                + (segmentPrefix != null ? " segmentPrefix='" + segmentPrefix + "'" : " segmentPrefix=none (aws-hostname + /tm/ active)"));
    }

    /** Fallback constructor used when no {@link NRAdConfig} is available. */
    public NRTrackerMediaTailor(NRVideoConfiguration configuration) {
        super(configuration);
        this.segmentPrefix        = null;
        this.explicitlyConfigured = false;
        NRLog.d(MTConstants.LOG_CONFIG + " tracker created — no NRAdConfig (URL auto-detect mode)");
    }

    @Deprecated
    public NRTrackerMediaTailor() {
        super();
        this.segmentPrefix        = null;
        this.explicitlyConfigured = false;
    }

    @Override
    public void setPlayer(Object player) {
        this.player = (ExoPlayer) player;
        registerListeners();
    }

    @Override
    public void registerListeners() {
        super.registerListeners();
        if (player != null) {
            player.addListener(this);
        }
    }

    @Override
    public void unregisterListeners() {
        super.unregisterListeners();
        if (player != null) {
            player.removeListener(this);
        }
    }

    @Override
    public void dispose() {
        if (!isDisposed.compareAndSet(false, true)) return;

        stopPolling();
        cancelTrackingFetch();
        unregisterListeners();

        synchronized (adSchedule) { adSchedule.clear(); }
        currentAdBreak = null;
        currentAdPod = null;
        player = null;

        super.dispose();
    }

    /**
     * Escape hatch for setting the MediaTailor tracking URL manually.
     *
     * <p>In the common case the tracker auto-derives the tracking URL from
     * the sessionized manifest URI that ExoPlayer loads (either the query
     * {@code aws.sessionId=…} on first playback, or the DASH MPD
     * {@code <Location>} element). Only call this if you're handing the
     * player a pre-sessionized URL without {@code aws.sessionId}, or if your
     * CDN/proxy strips the session id from the URI the player sees.</p>
     */
    public void setTrackingUrl(String trackingUrl) {
        this.trackingUrl = trackingUrl;
    }

    /**
     * App-invoked when the user taps skip on a skippable ad. Emits an
     * {@code AD_SKIP} New Relic event.
     */
    public void notifyAdSkipped() {
        if (isDisposed.get() || currentAdBreak == null) return;
        NRLog.d(MTConstants.LOG_EVENT + " AD_SKIP (user skipped)");
        sendVideoAdEvent("AD_SKIP");
    }

    // ── Player.Listener ───────────────────────────────────────────────────

    @Override
    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
        if (isDisposed.get()) return;
        Uri uri = mediaItem != null && mediaItem.localConfiguration != null
                ? mediaItem.localConfiguration.uri : null;
        detectSource(uri);
    }

    @Override
    public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
        if (isDisposed.get()) return;
        if (!activated && player != null && player.getCurrentMediaItem() != null) {
            MediaItem mi = player.getCurrentMediaItem();
            detectSource(mi.localConfiguration != null ? mi.localConfiguration.uri : null);
        }
        if (activated) {
            detectStreamTypeIfNeeded();
            reparseManifest();
        }
    }

    @Override
    public void onMetadata(@NonNull Metadata metadata) {
        if (isDisposed.get() || !activated) return;
        handleMetadata(metadata);
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (isDisposed.get() || !activated) return;
        if (playbackState == Player.STATE_READY) {
            detectStreamTypeIfNeeded();
            reparseManifest();
        }
        if (currentAdBreak == null) return;
        // During an ad break, translate buffering + seek state into AD_*
        // events rather than letting the content tracker emit CONTENT_*.
        if (playbackState == Player.STATE_BUFFERING) {
            NRLog.d(MTConstants.LOG_EVENT + " AD_BUFFER_START (inside ad break)");
            sendBufferStart();
        } else if (playbackState == Player.STATE_READY) {
            if (getState().isBuffering) {
                NRLog.d(MTConstants.LOG_EVENT + " AD_BUFFER_END (inside ad break)");
                sendBufferEnd();
            }
            if (getState().isSeeking) {
                NRLog.d(MTConstants.LOG_EVENT + " AD_SEEK_END (inside ad break)");
                sendSeekEnd();
            }
        }
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                        @NonNull Player.PositionInfo newPosition,
                                        int reason) {
        if (isDisposed.get() || !activated) return;
        if (currentAdBreak == null) return;
        if (reason == Player.DISCONTINUITY_REASON_SEEK && !getState().isSeeking) {
            NRLog.d(MTConstants.LOG_EVENT + " AD_SEEK_START (user seek during ad break)");
            sendSeekStart();
        }
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        if (isDisposed.get() || !activated) return;
        if (currentAdBreak == null) return;
        if (!playWhenReady) {
            NRLog.d(MTConstants.LOG_EVENT + " AD_PAUSE (user pause during ad break)");
            sendPause();
        } else {
            if (getState().isPaused) {
                NRLog.d(MTConstants.LOG_EVENT + " AD_RESUME (user resume during ad break)");
                sendResume();
            }
        }
    }

    // ── detection & activation ────────────────────────────────────────────

    private void detectSource(Uri uri) {
        // When the customer explicitly passed NRAdConfig.mediaTailor(), we trust
        // their intent and activate unconditionally — no URL substring check.
        // This is the only way to support custom CDN manifest domains that have
        // no "mediatailor" in the URL (mirrors VideoJS PR #106 explicit opt-in).
        if (!explicitlyConfigured && !MTDetector.isMediaTailorUri(uri)) {
            NRLog.d(MTConstants.LOG_DETECT + " not a MediaTailor URI and no explicit config"
                    + " — tracker stays inert. uri=" + (uri != null ? uri : "null"));
            if (activated) {
                deactivate();
            }
            return;
        }
        if (uri == null) {
            NRLog.d(MTConstants.LOG_DETECT + " uri is null — skipping activation");
            return;
        }
        if (activated && uri.toString().equals(mediaTailorEndpoint)) {
            return;
        }
        if (explicitlyConfigured && !MTDetector.isMediaTailorUri(uri)) {
            NRLog.d(MTConstants.LOG_DETECT + " activating on custom CDN domain (no 'mediatailor' in URL)"
                    + " — explicit NRAdConfig.mediaTailor() opt-in. uri=" + uri);
        }
        activate(uri);
    }

    private void activate(Uri uri) {
        activated           = true;
        mediaTailorEndpoint = uri.toString();
        manifestType        = MTDetector.manifestType(uri);

        if (trackingUrl == null) {
            trackingUrl = MTDetector.extractTrackingUrl(uri);
        }

        hasAttemptedTrackingFetch.set(false);
        synchronized (adSchedule) { adSchedule.clear(); }
        currentAdBreak = null;
        currentAdPod   = null;

        String detectionDesc = segmentPrefix != null
                ? "aws-hostname | /tm/ | custom='" + segmentPrefix + "'"
                : "aws-hostname | /tm/";
        NRLog.d(MTConstants.LOG_DETECT + " activated"
                + " format=" + manifestType
                + " detection=[" + detectionDesc + "]"
                + " trackingUrl=" + (trackingUrl != null ? trackingUrl : "pending")
                + " endpoint=" + mediaTailorEndpoint);

        startPolling();
    }

    private void deactivate() {
        activated = false;
        stopPolling();
        cancelTrackingFetch();
        synchronized (adSchedule) { adSchedule.clear(); }
        currentAdBreak = null;
        currentAdPod = null;
    }

    private void detectStreamTypeIfNeeded() {
        if (streamType != null || player == null) return;
        long duration = player.getDuration();
        // MediaTailor emits type="dynamic" MPDs even for VOD (live-style window),
        // so player.isCurrentMediaItemLive() returns true for SSAI VOD too. The
        // only reliable signal is whether the manifest declares a finite
        // mediaPresentationDuration — VOD does, live does not.
        boolean live = duration == C.TIME_UNSET || duration <= 0;
        streamType = live ? MTConstants.STREAM_TYPE_LIVE : MTConstants.STREAM_TYPE_VOD;
        NRLog.d(MTConstants.LOG_DETECT + " stream type: " + streamType
                + " (duration=" + duration + "ms, isCurrentMediaItemLive="
                + player.isCurrentMediaItemLive() + ")");
    }

    // ── manifest parsing ──────────────────────────────────────────────────

    private void reparseManifest() {
        if (player == null) return;
        Object manifest = player.getCurrentManifest();
        if (manifest instanceof DashManifest) {
            DashManifest dash = (DashManifest) manifest;
            // Implicit-session rescue: if our trackingUrl is still null because the
            // MediaItem URI didn't carry `sessionId=` (MediaTailor redirected to it
            // but ExoPlayer keeps the original request URI), the MPD's <Location>
            // element — exposed by Media3 as DashManifest.location — carries the
            // sessionized URL. Derive the tracking URL from it.
            if (trackingUrl == null && dash.location != null) {
                String derived = MTDetector.extractTrackingUrl(dash.location);
                if (derived != null) {
                    trackingUrl = derived;
                    NRLog.d(MTConstants.LOG_TRACK + " trackingUrl recovered from DASH <Location>: " + trackingUrl);
                }
            }
            List<MTAdBreak> parsed = MTDashParser.parse(dash, segmentPrefix);
            NRLog.d(MTConstants.LOG_PARSE_DASH + " manifest parsed: periods=" + dash.getPeriodCount()
                    + " adBreaks=" + parsed.size()
                    + " location=" + (dash.location != null ? dash.location : "null"));
            if (!parsed.isEmpty()) {
                applyNewBreaks(parsed);
            }
        } else if (manifest instanceof HlsManifest) {
            HlsManifest hls = (HlsManifest) manifest;
            int segCount = hls.mediaPlaylist != null && hls.mediaPlaylist.segments != null
                    ? hls.mediaPlaylist.segments.size() : 0;
            List<MTAdBreak> parsed = MTHlsParser.parse(hls, segmentPrefix);
            NRLog.d(MTConstants.LOG_PARSE_HLS + " manifest parsed: segments=" + segCount
                    + " adBreaks=" + parsed.size());
            if (!parsed.isEmpty()) {
                applyNewBreaks(parsed);
            }
        } else if (manifest != null) {
            NRLog.d(MTConstants.LOG_TAG + " getCurrentManifest is neither DASH nor HLS: "
                    + manifest.getClass().getName());
        } else {
            NRLog.d(MTConstants.LOG_TAG + " getCurrentManifest() returned null — not loaded yet");
        }
        maybeFetchTracking();
    }

    private void handleMetadata(Metadata metadata) {
        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);
            if (!(entry instanceof EventMessage)) continue;
            EventMessage em = (EventMessage) entry;
            if (em.schemeIdUri == null
                    || !em.schemeIdUri.toLowerCase().contains(MTConstants.SCTE35_SCHEME_MARKER)) {
                continue;
            }
            long positionMs = player != null ? player.getCurrentPosition() : 0L;
            long startMs = Math.max(positionMs, 0L);
            long durationMs = em.durationMs;
            NRLog.d(MTConstants.LOG_PARSE_DASH + " emsg SCTE-35: scheme=" + em.schemeIdUri
                    + " id=" + em.id + " startMs=" + startMs + " durationMs=" + durationMs);
            if (durationMs < MTConstants.MIN_AD_DURATION_MS) continue;
            String id = em.id != 0 ? "emsg-" + em.id : "emsg-" + startMs;
            MTAdBreak br = new MTAdBreak(id, startMs, durationMs);
            applyNewBreaks(Collections.singletonList(br));
        }
    }

    private void applyNewBreaks(List<MTAdBreak> incoming) {
        int before;
        int after;
        synchronized (adSchedule) {
            before = adSchedule.size();
            List<MTAdBreak> merged = MTAdScheduleMerger.mergeSchedule(
                    new ArrayList<>(adSchedule), incoming);
            adSchedule.clear();
            adSchedule.addAll(merged);
            after = adSchedule.size();
            StringBuilder sb = new StringBuilder();
            sb.append(MTConstants.LOG_TAG).append(" schedule (").append(after).append(" breaks, was ").append(before).append("): ");
            for (int i = 0; i < adSchedule.size(); i++) {
                MTAdBreak b = adSchedule.get(i);
                if (i > 0) sb.append(", ");
                sb.append("[").append(b.startTimeMs).append("ms +").append(b.durationMs).append("ms");
                if (!b.pods.isEmpty()) sb.append(" pods=").append(b.pods.size());
                if (b.confirmedByTracking) sb.append(" ✓tracking");
                sb.append("]");
            }
            NRLog.d(sb.toString());
        }
        maybeFetchTracking();
    }

    // ── tracking API ──────────────────────────────────────────────────────

    private void maybeFetchTracking() {
        if (trackingUrl == null) return;
        // VOD: fetch once after the schedule is built. Live: fetch on every
        // manifest refresh (onTimelineChanged drives reparseManifest), but
        // skip if a fetch is already in flight to avoid piling up connections.
        if (MTConstants.STREAM_TYPE_VOD.equals(streamType)) {
            if (!hasAttemptedTrackingFetch.compareAndSet(false, true)) return;
        } else if (trackingWorker != null && trackingWorker.isAlive()) {
            return;
        }
        startTrackingFetch();
    }

    private void startTrackingFetch() {
        cancelTrackingFetch();
        trackingClient = new MTTrackingClient();
        final String url = trackingUrl;
        NRLog.d(MTConstants.LOG_TRACK + " fetching: " + url);
        trackingWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                MTTrackingResponse resp = trackingClient.fetch(url);
                if (isDisposed.get()) return;
                if (resp == null) {
                    NRLog.w(MTConstants.LOG_TRACK + " fetch returned null (failed or cancelled)");
                    return;
                }
                applyTrackingResponse(resp);
            }
        }, "NR-MT-tracking");
        trackingWorker.setDaemon(true);
        trackingWorker.start();
    }

    private void cancelTrackingFetch() {
        if (trackingClient != null) {
            trackingClient.cancel();
            trackingClient = null;
        }
        if (trackingWorker != null) {
            trackingWorker.interrupt();
            trackingWorker = null;
        }
    }

    private void applyTrackingResponse(final MTTrackingResponse resp) {
        NRLog.d(MTConstants.LOG_TRACK + " response: " + resp.avails.size() + " avail(s), "
                + resp.nonLinearAvails.size() + " non-linear avail(s)");
        Handler main = pollHandler;
        if (main == null) main = new Handler(Looper.getMainLooper());
        main.post(new Runnable() {
            @Override
            public void run() {
                if (isDisposed.get()) return;
                nonLinearAvailsCount = resp.nonLinearAvails.size();
                synchronized (adSchedule) {
                    List<MTAdBreak> enriched = MTAdScheduleMerger.enrichWithTracking(
                            new ArrayList<>(adSchedule), resp);
                    adSchedule.clear();
                    adSchedule.addAll(enriched);
                    NRLog.d(MTConstants.LOG_TRACK + " schedule enriched: " + adSchedule.size()
                            + " breaks, confirmed=" + countConfirmed(adSchedule));
                }
            }
        });
    }

    private static int countConfirmed(List<MTAdBreak> list) {
        int n = 0;
        for (MTAdBreak b : list) if (b.confirmedByTracking) n++;
        return n;
    }

    // ── playhead poll loop ────────────────────────────────────────────────

    private void startPolling() {
        if (pollHandler != null) return;
        pollHandler = new Handler(Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isDisposed.get()) return;
                try {
                    tick();
                } finally {
                    if (!isDisposed.get() && pollHandler != null) {
                        pollHandler.postDelayed(this, MTConstants.PLAYHEAD_POLL_INTERVAL_MS);
                    }
                }
            }
        };
        pollHandler.postDelayed(pollRunnable, MTConstants.PLAYHEAD_POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        if (pollHandler != null) {
            pollHandler.removeCallbacksAndMessages(null);
            pollHandler = null;
        }
        pollRunnable = null;
    }

    private void tick() {
        if (player == null) return;
        long position = player.getCurrentPosition();

        MTAdBreak active;
        synchronized (adSchedule) {
            active = findActiveBreak(position);
        }

        if (active != null) {
            handleInsideBreak(active, position);
        } else if (currentAdBreak != null) {
            handleExitingBreak();
        }
    }

    private MTAdBreak findActiveBreak(long positionMs) {
        for (MTAdBreak br : adSchedule) {
            if (br.contains(positionMs)) return br;
        }
        return null;
    }

    private void handleInsideBreak(MTAdBreak active, long position) {
        if (!active.hasFiredStart) {
            currentAdBreak = active;
            active.adPosition = computeAdPosition(active);
            NRLog.d(MTConstants.LOG_EVENT + " AD_BREAK_START startMs=" + active.startTimeMs + " pos=" + active.adPosition);
            sendAdBreakStart();
            active.hasFiredStart = true;
        }

        if (!active.pods.isEmpty()) {
            MTAdPod pod = active.findActivePod(position);
            if (pod != null && pod != currentAdPod) {
                if (currentAdPod != null) {
                    NRLog.d(MTConstants.LOG_EVENT + " AD_END (pod transition)");
                    sendEnd();
                }
                currentAdPod = pod;
                NRLog.d(MTConstants.LOG_EVENT + " AD_START (new pod)");
                sendRequest();
                sendStart();
                pod.hasFiredStart = true;
            }
            if (pod != null) {
                trackQuartiles(pod, position - pod.startTimeMs);
            }
        } else if (!active.hasFiredAdStart) {
            NRLog.d(MTConstants.LOG_EVENT + " AD_START (no pods)");
            sendRequest();
            sendStart();
            active.hasFiredAdStart = true;
        }

        if (active.pods.isEmpty()) {
            trackQuartiles(active, position - active.startTimeMs);
        }
    }

    private void handleExitingBreak() {
        if (currentAdPod != null) {
            NRLog.d(MTConstants.LOG_EVENT + " AD_END (final pod)");
            sendEnd();
            currentAdPod = null;
        } else if (currentAdBreak != null && currentAdBreak.hasFiredAdStart) {
            NRLog.d(MTConstants.LOG_EVENT + " AD_END (no-pods break)");
            sendEnd();
        }
        if (currentAdBreak != null && !currentAdBreak.hasFiredEnd) {
            NRLog.d(MTConstants.LOG_EVENT + " AD_BREAK_END");
            sendAdBreakEnd();
            currentAdBreak.hasFiredEnd = true;
        }
        currentAdBreak = null;
        currentQuartile = null;
    }

    private void trackQuartiles(Object target, long progressMs) {
        long durationMs;
        boolean q1, q2, q3;
        if (target instanceof MTAdPod) {
            MTAdPod p = (MTAdPod) target;
            durationMs = p.durationMs; q1 = p.hasFiredQ1; q2 = p.hasFiredQ2; q3 = p.hasFiredQ3;
        } else {
            MTAdBreak b = (MTAdBreak) target;
            durationMs = b.durationMs; q1 = b.hasFiredQ1; q2 = b.hasFiredQ2; q3 = b.hasFiredQ3;
        }
        if (durationMs <= 0) return;

        double progress = (double) progressMs / (double) durationMs;
        if (progress >= MTConstants.QUARTILE_Q1 && !q1) fireQuartile(target, 1L);
        if (progress >= MTConstants.QUARTILE_Q2 && !getFlag(target, 2)) fireQuartile(target, 2L);
        if (progress >= MTConstants.QUARTILE_Q3 && !getFlag(target, 3)) fireQuartile(target, 3L);
    }

    private boolean getFlag(Object target, int n) {
        if (target instanceof MTAdPod) {
            MTAdPod p = (MTAdPod) target;
            return n == 1 ? p.hasFiredQ1 : n == 2 ? p.hasFiredQ2 : p.hasFiredQ3;
        }
        MTAdBreak b = (MTAdBreak) target;
        return n == 1 ? b.hasFiredQ1 : n == 2 ? b.hasFiredQ2 : b.hasFiredQ3;
    }

    private void fireQuartile(Object target, long quartile) {
        currentQuartile = quartile;
        NRLog.d(MTConstants.LOG_EVENT + " AD_QUARTILE " + (quartile * 25) + "%");
        sendAdQuartile();
        if (target instanceof MTAdPod) {
            MTAdPod p = (MTAdPod) target;
            if (quartile == 1L) p.hasFiredQ1 = true;
            else if (quartile == 2L) p.hasFiredQ2 = true;
            else p.hasFiredQ3 = true;
        } else {
            MTAdBreak b = (MTAdBreak) target;
            if (quartile == 1L) b.hasFiredQ1 = true;
            else if (quartile == 2L) b.hasFiredQ2 = true;
            else b.hasFiredQ3 = true;
        }
    }

    private String computeAdPosition(MTAdBreak br) {
        if (MTConstants.STREAM_TYPE_LIVE.equals(streamType)) return null;
        int idx = -1;
        synchronized (adSchedule) {
            for (int i = 0; i < adSchedule.size(); i++) {
                if (adSchedule.get(i) == br) { idx = i; break; }
            }
            if (idx == 0) return MTConstants.AD_POSITION_PRE;
            if (idx == adSchedule.size() - 1) return MTConstants.AD_POSITION_POST;
            return MTConstants.AD_POSITION_MID;
        }
    }

    // ── attribute getters ─────────────────────────────────────────────────

    @Override public String getTrackerName() { return MTConstants.TRACKER_NAME; }
    @Override public String getTrackerVersion() { return BuildConfig.VERSION_NAME; }
    // Mirror NRTrackerExoPlayer so ad events carry the same player identity as
    // the linked content tracker (Media3 library tag + version).
    @Override public String getPlayerName() { return MediaLibraryInfo.TAG; }
    @Override public String getPlayerVersion() { return MediaLibraryInfo.VERSION; }
    @Override public String getAdPartner() { return MTConstants.AD_PARTNER; }

    @Override
    public String getAdPosition() {
        return currentAdBreak != null ? currentAdBreak.adPosition : null;
    }

    @Override
    public Long getAdQuartile() {
        return currentQuartile;
    }

    @Override
    public String getTitle() {
        if (currentAdPod != null && currentAdPod.title != null) return currentAdPod.title;
        if (currentAdBreak != null && currentAdBreak.title != null) return currentAdBreak.title;
        return currentAdBreak != null ? currentAdBreak.id : null;
    }

    @Override
    public String getAdCreativeId() {
        // VAST <Creative id="…"> — distinct from adId per the VAST spec.
        if (currentAdPod != null && currentAdPod.creativeId != null) return currentAdPod.creativeId;
        return currentAdBreak != null ? currentAdBreak.creativeId : null;
    }

    @Override
    public String getVideoId() {
        // VAST <Ad id="…"> — MediaTailor's ad.adId from the tracking response.
        if (currentAdPod != null && currentAdPod.adId != null) return currentAdPod.adId;
        if (currentAdBreak != null && currentAdBreak.adId != null) return currentAdBreak.adId;
        return currentAdBreak != null ? currentAdBreak.id : null;
    }

    @Override
    public Long getDuration() {
        if (currentAdPod != null) return currentAdPod.durationMs;
        if (currentAdBreak != null) return currentAdBreak.durationMs;
        return null;
    }

    @Override
    public String getSrc() {
        if (trackingUrl != null) return trackingUrl;
        return mediaTailorEndpoint;
    }

    @Override
    public Long getPlayhead() {
        if (player == null || currentAdBreak == null) return 0L;
        return Math.max(player.getCurrentPosition() - currentAdBreak.startTimeMs, 0L);
    }

    /**
     * Injects MediaTailor-specific ad metadata pulled from the tracking API
     * that isn't covered by {@link NRVideoTracker}'s fixed attribute set.
     *
     * <p>Scope — these attributes appear only on events emitted by this
     * tracker instance (identifiable as {@code trackerName="NRMTracker"}) AND
     * only while {@link #currentAdBreak} is non-null. They are not written to:</p>
     * <ul>
     *   <li>{@code VideoAction} / {@code VideoErrorAction} from
     *       {@link com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer}</li>
     *   <li>{@code VideoAdAction} from {@code NRTrackerIMA}</li>
     *   <li>{@code QOE_AGGREGATE} (generated by the content tracker)</li>
     *   <li>This tracker's {@code TRACKER_READY} (gated by the
     *       {@code currentAdBreak} check)</li>
     * </ul>
     *
     * <p>Attributes added: {@code adSystem}, {@code vastAdId},
     * {@code creativeSequence}, {@code skipOffset}, {@code adProgramDateTime},
     * {@code availProgramDateTime}, {@code isBumper},
     * {@code nonLinearAvailsCount}.</p>
     */
    @Override
    public java.util.Map<String, Object> getAttributes(String action, java.util.Map<String, Object> attributes) {
        java.util.Map<String, Object> attr = super.getAttributes(action, attributes);
        if (currentAdBreak == null) return attr;

        String adSystem = pick(currentAdPod != null ? currentAdPod.adSystem : null, currentAdBreak.adSystem);
        String vastAdId = pick(currentAdPod != null ? currentAdPod.vastAdId : null, currentAdBreak.vastAdId);
        String creativeSequence = pick(
                currentAdPod != null ? currentAdPod.creativeSequence : null,
                currentAdBreak.creativeSequence);
        String skipOffset = pick(currentAdPod != null ? currentAdPod.skipOffset : null, currentAdBreak.skipOffset);
        String adProgramDateTime = currentAdPod != null ? currentAdPod.adProgramDateTime : null;
        String availProgramDateTime = currentAdBreak.availProgramDateTime;

        if (adSystem != null) attr.put("adSystem", adSystem);
        if (vastAdId != null) attr.put("vastAdId", vastAdId);
        if (creativeSequence != null) attr.put("creativeSequence", creativeSequence);
        if (skipOffset != null) attr.put("skipOffset", skipOffset);
        if (adProgramDateTime != null) attr.put("adProgramDateTime", adProgramDateTime);
        if (availProgramDateTime != null) attr.put("availProgramDateTime", availProgramDateTime);
        if (currentAdPod != null && currentAdPod.isBumper) {
            attr.put("isBumper", Boolean.TRUE);
        } else if (currentAdBreak != null) {
            boolean breakIsBumper = false;
            for (MTAdPod p : currentAdBreak.pods) {
                if (p.isBumper) { breakIsBumper = true; break; }
            }
            if (breakIsBumper) attr.put("isBumper", Boolean.TRUE);
        }
        if (nonLinearAvailsCount > 0) attr.put("nonLinearAvailsCount", nonLinearAvailsCount);
        return attr;
    }

    private static String pick(String first, String second) {
        return first != null ? first : second;
    }
}
