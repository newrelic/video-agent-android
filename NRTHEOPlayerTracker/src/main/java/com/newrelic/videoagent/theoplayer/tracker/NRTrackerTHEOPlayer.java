package com.newrelic.videoagent.theoplayer.tracker;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.theoplayer.BuildConfig;

import com.theoplayer.android.api.THEOplayerView;
import com.newrelic.videoagent.theoplayer.exception.TheoErrorHandler;
import com.theoplayer.android.api.error.THEOplayerException;
import com.theoplayer.android.api.event.EventListener;
import com.theoplayer.android.api.event.player.EndedEvent;
import com.theoplayer.android.api.event.player.ErrorEvent;
import com.theoplayer.android.api.event.player.PauseEvent;
import com.theoplayer.android.api.event.player.PlayEvent;
import com.theoplayer.android.api.event.player.PlayingEvent;
import com.theoplayer.android.api.event.player.PlayerEventTypes;
import com.theoplayer.android.api.event.player.SeekingEvent;
import com.theoplayer.android.api.event.player.SeekedEvent;
import com.theoplayer.android.api.event.player.SourceChangeEvent;
import com.theoplayer.android.api.event.player.DurationChangeEvent;
import com.theoplayer.android.api.event.player.WaitingEvent;
import com.theoplayer.android.api.event.player.ContentProtectionErrorEvent;
import com.theoplayer.android.api.event.track.mediatrack.video.ActiveQualityChangedEvent;
import com.theoplayer.android.api.event.track.mediatrack.video.VideoTrackEventTypes;
import com.theoplayer.android.api.event.track.mediatrack.video.list.AddTrackEvent;
import com.theoplayer.android.api.event.track.mediatrack.video.list.VideoTrackListEventTypes;
import com.theoplayer.android.api.THEOplayerGlobal;
import com.theoplayer.android.api.metrics.Metrics;
import com.theoplayer.android.api.player.Player;
import com.theoplayer.android.api.player.track.mediatrack.MediaTrack;
import com.theoplayer.android.api.player.track.mediatrack.quality.AudioQuality;
import com.theoplayer.android.api.player.track.mediatrack.quality.VideoQuality;

import java.util.HashMap;
import java.util.Map;

import static com.newrelic.videoagent.core.NRDef.*;

/**
 * New Relic Video tracker for THEOplayer (Dolby OptiView Player).
 *
 * <p>Usage:</p>
 * <pre>
 *   // Pass the THEOplayerView and PLAYER_TYPE_THEO — NRVideo instantiates the tracker internally.
 *   Integer trackerId = NRVideo.addPlayer(
 *       new NRVideoPlayerConfiguration("theo-player", theoPlayerView,
 *           NRVideoPlayerConfiguration.PLAYER_TYPE_THEO, null, null));
 *
 *   // Forward Activity lifecycle via the tracker retrieved from NRVideo
 *   {@literal @}Override protected void onResume()  { super.onResume();  if (theoPlayerView != null) theoPlayerView.onResume(); }
 *   {@literal @}Override protected void onPause()   { super.onPause();   if (theoPlayerView != null) theoPlayerView.onPause(); }
 *   {@literal @}Override protected void onDestroy() {
 *       super.onDestroy();
 *       NRTracker t = NewRelicVideoAgent.getInstance().getContentTracker(trackerId);
 *       if (t instanceof NRTrackerTHEOPlayer) ((NRTrackerTHEOPlayer) t).onDestroy();
 *       NRVideo.releaseTracker(trackerId);
 *   }
 * </pre>
 *
 * <p>THEOplayer must be licensed by the host app via AndroidManifest.xml or THEOplayerConfig.</p>
 */
public class NRTrackerTHEOPlayer extends NRVideoTracker {

    protected THEOplayerView theoPlayerView;
    protected Player player;

    // Per-rendition change direction ("up" | "down" | null)
    private String renditionChangeShift;
    private int lastRenditionWidth = 0;
    private int lastRenditionHeight = 0;

    // Tracks cumulative dropped frame count from Metrics API to compute delta per heartbeat
    private long lastDroppedFrames    = 0;
    private long lastDroppedFrameTime = 0;

    // Individual listener references — stored so they can be removed precisely
    private EventListener<SourceChangeEvent>          onSourceChange;
    private EventListener<PlayingEvent>               onPlaying;
    private EventListener<PlayEvent>                  onPlay;
    private EventListener<PauseEvent>                 onPause;
    private EventListener<WaitingEvent>               onWaiting;
    private EventListener<SeekingEvent>               onSeeking;
    private EventListener<SeekedEvent>                onSeeked;
    private EventListener<EndedEvent>                 onEnded;
    private EventListener<ErrorEvent>                 onError;
    private EventListener<ContentProtectionErrorEvent> onContentProtectionError;
    private EventListener<DurationChangeEvent>        onDurationChange;
    private EventListener<AddTrackEvent> onVideoTrackChange;
    private EventListener<ActiveQualityChangedEvent> onActiveQualityChanged;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * No-arg constructor — use when the player will be attached via {@link #setPlayer(Object)}.
     * Matches the pattern of NRTrackerExoPlayer for consistency and testability.
     */
    public NRTrackerTHEOPlayer() {
        super();
    }

    /**
     * Create a tracker with configuration only; call {@link #setPlayer(Object)} later.
     */
    public NRTrackerTHEOPlayer(NRVideoConfiguration configuration) {
        super(configuration);
    }

    /**
     * Create a tracker and attach it to a {@link THEOplayerView} immediately.
     */
    public NRTrackerTHEOPlayer(NRVideoConfiguration configuration, THEOplayerView theoPlayerView) {
        super(configuration);
        setPlayer(theoPlayerView);
    }

    // -------------------------------------------------------------------------
    // Player attachment
    // -------------------------------------------------------------------------

    /**
     * Attach the tracker to a {@link THEOplayerView}.
     *
     * @param player A {@link THEOplayerView} instance.
     */
    @Override
    public void setPlayer(Object player) {
        if (!(player instanceof THEOplayerView)) {
            throw new IllegalArgumentException(
                "[NRTrackerTHEOPlayer] Expected a THEOplayerView but received: " +
                (player == null ? "null" : player.getClass().getName()));
        }
        if (this.theoPlayerView != null) {
            unregisterListeners();
        }
        this.theoPlayerView = (THEOplayerView) player;
        this.player = this.theoPlayerView.getPlayer();
        registerListeners();
        super.setPlayer(player);
    }

    // -------------------------------------------------------------------------
    // Lifecycle pass-throughs
    // -------------------------------------------------------------------------

    /** Forward Activity#onResume to THEOplayerView. */
    public void onResume() {
        if (theoPlayerView != null) {
            theoPlayerView.onResume();
        }
    }

    /** Forward Activity#onPause to THEOplayerView. */
    public void onPause() {
        if (theoPlayerView != null) {
            theoPlayerView.onPause();
        }
    }

    /**
     * Forward Activity#onDestroy to THEOplayerView and clean up listeners.
     * Call {@code NRVideo.releaseTracker(trackerId)} after this.
     */
    public void onDestroy() {
        THEOplayerView view = theoPlayerView;
        unregisterListeners();  // nulls theoPlayerView — capture before calling
        if (view != null) {
            view.onDestroy();
        }
    }

    // -------------------------------------------------------------------------
    // Event registration
    // -------------------------------------------------------------------------

    @Override
    public void registerListeners() {
        if (player == null) return;

        NRLog.d("NRTrackerTHEOPlayer: registerListeners");

        onSourceChange           = event -> handleSourceChange();
        onPlaying                = event -> handlePlaying();
        onPlay                   = event -> handlePlay();
        onPause                  = event -> handlePause();
        onWaiting                = event -> handleWaiting(player.isSeeking());
        onSeeking                = event -> handleSeeking();
        onSeeked                 = event -> handleSeeked();
        onEnded                  = event -> handleEnded();
        onError                  = event -> handleError(event.getErrorObject());
        onContentProtectionError = event -> handleError(event.getErrorObject());
        onDurationChange         = event -> NRLog.d("THEOplayer: DURATIONCHANGE duration=" + player.getDuration());

        // ADDTRACK fires once per newly added track — wire only that track, not the
        // full list. This is O(N) total vs. O(N²) if TRACKLISTCHANGE were used.
        onVideoTrackChange = event -> {
            MediaTrack track = event.getTrack();
            if (track != null) {
                NRLog.d("THEOplayer: video track ADDED — wiring quality listener");
                track.addEventListener(VideoTrackEventTypes.ACTIVEQUALITYCHANGEDEVENT, onActiveQualityChanged);
            }
        };

        onActiveQualityChanged = event -> {
            VideoQuality quality = getActiveVideoQuality();
            if (quality != null) {
                handleActiveQualityChanged(quality.getWidth(), quality.getHeight());
            }
        };

        player.addEventListener(PlayerEventTypes.SOURCECHANGE,           onSourceChange);
        player.addEventListener(PlayerEventTypes.PLAYING,                onPlaying);
        player.addEventListener(PlayerEventTypes.PLAY,                   onPlay);
        player.addEventListener(PlayerEventTypes.PAUSE,                  onPause);
        player.addEventListener(PlayerEventTypes.WAITING,                onWaiting);
        player.addEventListener(PlayerEventTypes.SEEKING,                onSeeking);
        player.addEventListener(PlayerEventTypes.SEEKED,                 onSeeked);
        player.addEventListener(PlayerEventTypes.ENDED,                  onEnded);
        player.addEventListener(PlayerEventTypes.ERROR,                  onError);
        player.addEventListener(PlayerEventTypes.CONTENTPROTECTIONERROR, onContentProtectionError);
        player.addEventListener(PlayerEventTypes.DURATIONCHANGE,         onDurationChange);
        player.getVideoTracks().addEventListener(VideoTrackListEventTypes.ADDTRACK, onVideoTrackChange);
    }

    // -------------------------------------------------------------------------
    // Named event handlers — package-private so tests drive them directly
    // without needing a real THEOplayerView or mocked player events.
    // Mirrors iOS NRTrackerTHEOplayer.swift's handleXxx() overload pattern.
    // -------------------------------------------------------------------------

    void handleSourceChange() {
        NRLog.d("THEOplayer: SOURCECHANGE");
        if (getState().isRequested) {
            sendEnd();
        }
        renditionChangeShift = null;
        lastRenditionWidth   = 0;
        lastRenditionHeight  = 0;
        // Metrics.getDroppedVideoFrames() is cumulative — snapshot so the first heartbeat
        // delta only counts frames dropped after this source change, not all prior ones.
        Metrics metrics = (player != null) ? player.getMetrics() : null;
        lastDroppedFrames    = (metrics != null) ? metrics.getDroppedVideoFrames() : 0;
        lastDroppedFrameTime = android.os.SystemClock.elapsedRealtime();
        sendRequest();
    }

    void handlePlaying() {
        NRLog.d("THEOplayer: PLAYING");
        if (!getState().isStarted) {
            // THEOplayer fires PLAYING while isBuffering is still true —
            // close the initial buffer first so CONTENT_BUFFER_END precedes CONTENT_START.
            if (getState().isBuffering) {
                sendBufferEnd();
            }
            sendStart();
        } else if (getState().isBuffering) {
            sendBufferEnd();
        } else if (getState().isSeeking) {
            // Seek resolved without explicit SEEKED (defensive)
            sendSeekEnd();
        }
    }

    void handlePlay() {
        NRLog.d("THEOplayer: PLAY");
        if (getState().isPaused) {
            sendResume();
        }
    }

    void handlePause() {
        NRLog.d("THEOplayer: PAUSE");
        sendPause();
    }

    /**
     * Handles WAITING. Extracted as a named overload — mirrors iOS handleWaiting(isSeeking:) —
     * so tests can exercise the seeking guard without a mocked Player.
     *
     * THEOplayer fires WAITING for both rebuffers and seeks. Without the guard every
     * user seek emits a spurious CONTENT_BUFFER_START to NRDB.
     */
    void handleWaiting(boolean isSeeking) {
        NRLog.d("THEOplayer: WAITING (isSeeking=" + isSeeking + ")");
        if (isSeeking) return;
        sendBufferStart();
    }

    void handleSeeking() {
        NRLog.d("THEOplayer: SEEKING");
        sendSeekStart();
    }

    void handleSeeked() {
        NRLog.d("THEOplayer: SEEKED");
        if (getState().isBuffering) {
            sendBufferEnd();
        }
        sendSeekEnd();
    }

    void handleEnded() {
        NRLog.d("THEOplayer: ENDED");
        sendEnd();
    }

    void handleError(THEOplayerException err) {
        NRLog.d("THEOplayer: ERROR - " + (err != null ? err.getMessage() : "unknown"));
        TheoErrorHandler handler = new TheoErrorHandler(err);
        sendError(handler.getErrorCode(), handler.getErrorMessage());
    }

    void handleActiveQualityChanged(int newWidth, int newHeight) {
        NRLog.d("THEOplayer: ACTIVE QUALITY CHANGED");
        long newArea  = (long) newWidth * newHeight;
        long lastArea = (long) lastRenditionWidth * lastRenditionHeight;
        if (lastArea != 0 && newArea != lastArea) {
            renditionChangeShift = (newArea > lastArea) ? "up" : "down";
            sendRenditionChange();
        }
        lastRenditionWidth  = newWidth;
        lastRenditionHeight = newHeight;
    }

    @Override
    public void unregisterListeners() {
        if (player == null) return;

        NRLog.d("NRTrackerTHEOPlayer: unregisterListeners");

        player.removeEventListener(PlayerEventTypes.SOURCECHANGE,           onSourceChange);
        player.removeEventListener(PlayerEventTypes.PLAYING,                onPlaying);
        player.removeEventListener(PlayerEventTypes.PLAY,                   onPlay);
        player.removeEventListener(PlayerEventTypes.PAUSE,                  onPause);
        player.removeEventListener(PlayerEventTypes.WAITING,                onWaiting);
        player.removeEventListener(PlayerEventTypes.SEEKING,                onSeeking);
        player.removeEventListener(PlayerEventTypes.SEEKED,                 onSeeked);
        player.removeEventListener(PlayerEventTypes.ENDED,                  onEnded);
        player.removeEventListener(PlayerEventTypes.ERROR,                  onError);
        player.removeEventListener(PlayerEventTypes.CONTENTPROTECTIONERROR, onContentProtectionError);
        player.removeEventListener(PlayerEventTypes.DURATIONCHANGE,         onDurationChange);
        player.getVideoTracks().removeEventListener(VideoTrackListEventTypes.ADDTRACK, onVideoTrackChange);
        for (int i = 0; i < player.getVideoTracks().length(); i++) {
            MediaTrack track = player.getVideoTracks().getItem(i);
            if (track != null) {
                track.removeEventListener(VideoTrackEventTypes.ACTIVEQUALITYCHANGEDEVENT, onActiveQualityChanged);
            }
        }

        player               = null;
        theoPlayerView       = null;
        renditionChangeShift = null;
        lastRenditionWidth   = 0;
        lastRenditionHeight  = 0;
        lastDroppedFrames    = 0;
        lastDroppedFrameTime = 0;
    }

    // -------------------------------------------------------------------------
    // Attribute getters
    // -------------------------------------------------------------------------

    @Override
    public String getPlayerName() {
        return "THEOplayer";
    }

    @Override
    public String getPlayerVersion() {
        // THEOplayerGlobal.getVersion() is a static method — no Context needed.
        // Returns the SDK version string e.g. "11.8.1"
        // Available since THEOplayer v3.5.0.
        // Ref: THEOplayerGlobal API — https://optiview.dolby.com/docs/theoplayer/v11/api-reference/android/com/theoplayer/android/api/THEOplayerGlobal.html
        return com.theoplayer.android.api.THEOplayerGlobal.getVersion();
    }

    @Override
    public String getTrackerName() {
        return "THEOplayerTracker";
    }

    @Override
    public String getTrackerVersion() {
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public String getTrackerSrc() {
        return SRC;
    }

    /**
     * Current stream URL.
     */
    @Override
    public String getSrc() {
        if (player == null) return null;
        return player.getSrc();
    }

    /**
     * Content duration in milliseconds.
     * Returns null for live streams (infinite duration) or before metadata is loaded.
     */
    @Override
    public Long getDuration() {
        if (player == null) return null;
        double durationSec = player.getDuration();
        if (Double.isNaN(durationSec) || Double.isInfinite(durationSec) || durationSec < 0) {
            return null;
        }
        return (long) (durationSec * 1000);
    }

    /**
     * Current playhead position in milliseconds.
     */
    @Override
    public Long getPlayhead() {
        if (player == null) return null;
        return (long) (player.getCurrentTime() * 1000);
    }

    /**
     * Playback rate (e.g. 1.0 = normal speed).
     */
    public Double getPlayrate() {
        if (player == null) return null;
        return player.getPlaybackRate();
    }

    /**
     * True when the player is muted.
     */
    @Override
    public Boolean getIsMuted() {
        if (player == null) return null;
        return player.isMuted();
    }

    /**
     * True when the stream is live (THEOplayer reports Infinity for live duration).
     */
    @Override
    public Boolean getIsLive() {
        if (player == null) return null;
        return Double.isInfinite(player.getDuration());
    }

    /**
     * Current rendition width in pixels (from the active video track quality).
     */
    @Override
    public Long getRenditionWidth() {
        VideoQuality q = getActiveVideoQuality();
        if (q == null) return null;
        int w = q.getWidth();
        return (w > 0) ? (long) w : null;
    }

    /**
     * Current rendition height in pixels (from the active video track quality).
     */
    @Override
    public Long getRenditionHeight() {
        VideoQuality q = getActiveVideoQuality();
        if (q == null) return null;
        int h = q.getHeight();
        return (h > 0) ? (long) h : null;
    }

    /**
     * Rendition target bitrate in bps (from the active video track quality).
     */
    @Override
    public Long getRenditionBitrate() {
        VideoQuality q = getActiveVideoQuality();
        if (q == null) return null;
        long bw = q.getBandwidth();
        return (bw > 0) ? bw : null;
    }

    /**
     * Observed (actual) bitrate — uses Metrics.getCurrentBandwidthEstimate() which reflects
     * real measured network throughput, distinct from the manifest-advertised rendition bitrate.
     * This allows NR dashboards to compare actual vs. manifest bitrate to detect network degradation.
     */
    @Override
    public Long getBitrate() {
        Long measured = getNetworkDownloadBitrate();
        return (measured != null) ? measured : getRenditionBitrate();
    }

    @Override
    public Long getActualBitrate() {
        Long measured = getNetworkDownloadBitrate();
        return (measured != null) ? measured : getRenditionBitrate();
    }

    @Override
    public Long getManifestBitrate() {
        return getRenditionBitrate();
    }

    /**
     * Segment download bitrate — not exposed by THEOplayer SDK v11.
     */
    @Override
    public Long getSegmentDownloadBitrate() {
        return null;
    }

    /**
     * Network download bitrate from THEOplayer Metrics API.
     * Returns the current estimated bandwidth in bps.
     * Ref: Metrics.getCurrentBandwidthEstimate() — https://optiview.dolby.com/docs/theoplayer/v11/api-reference/android/com/theoplayer/android/api/metrics/Metrics.html
     */
    @Override
    public Long getNetworkDownloadBitrate() {
        if (player == null) return null;
        Metrics metrics = player.getMetrics();
        if (metrics == null) return null;
        double bps = metrics.getCurrentBandwidthEstimate();
        return (bps > 0) ? (long) bps : null;
    }

    /**
     * Frame rate from the active video track quality.
     */
    @Override
    public Double getFps() {
        VideoQuality q = getActiveVideoQuality();
        if (q == null) return null;
        double fps = q.getFrameRate();
        return (fps > 0) ? fps : null;
    }

    /**
     * Content title — read from SourceDescription metadata if provided by the app,
     * otherwise falls back to the last path segment of the stream URL.
     */
    @Override
    public String getTitle() {
        if (player == null) return null;
        // Try metadata["title"] set on SourceDescription
        if (player.getSource() != null && player.getSource().getMetadata() != null) {
            Object title = player.getSource().getMetadata().get("title");
            if (title instanceof String && !((String) title).isEmpty()) {
                return (String) title;
            }
        }
        // Fallback: last path segment of the src URL.
        // Use Uri.parse() so query strings, fragments, and encoded characters are
        // handled correctly — matches ExoPlayer's Uri.getLastPathSegment() approach.
        String src = player.getSrc();
        if (src != null && !src.isEmpty()) {
            String segment = android.net.Uri.parse(src).getLastPathSegment();
            if (segment != null && !segment.isEmpty()) return segment;
        }
        return null;
    }

    /**
     * Content language from the active (enabled) audio track.
     * Returns an ISO639/2 language code e.g. "fra" for French.
     * Ref: Track.getLanguage() — https://optiview.dolby.com/docs/theoplayer/v11/api-reference/android/com/theoplayer/android/api/player/track/Track.html
     */
    @Override
    public String getLanguage() {
        if (player == null) return null;
        for (MediaTrack<AudioQuality> track : player.getAudioTracks()) {
            if (track.isEnabled()) {
                String lang = track.getLanguage();
                return (lang != null && !lang.isEmpty()) ? lang : null;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Heartbeat — checks for dropped frames each cycle
    // -------------------------------------------------------------------------

    /**
     * Override sendHeartbeat to also emit CONTENT_DROPPED_FRAMES when THEOplayer's
     * Metrics API reports new dropped frames since the last heartbeat.
     *
     * THEOplayer exposes a cumulative counter (not a per-callback delta like ExoPlayer).
     * We store the last observed value and compute the delta each heartbeat.
     *
     * Ref: Metrics.getDroppedVideoFrames() — available since THEOplayer v2.46.0
     */
    @Override
    public void sendHeartbeat() {
        super.sendHeartbeat();
        checkDroppedFrames();
    }

    private void checkDroppedFrames() {
        if (player == null || !getState().isStarted) return;
        Metrics metrics = player.getMetrics();
        if (metrics == null) return;
        long now     = android.os.SystemClock.elapsedRealtime();
        long current = metrics.getDroppedVideoFrames();
        long delta   = current - lastDroppedFrames;
        // THEOplayer Metrics API exposes only a cumulative counter — no per-event callback or
        // elapsed duration (unlike ExoPlayer's onDroppedVideoFrames(count, elapsedMs)).
        // elapsedMs approximates the window; revisit if a future SDK version adds a callback.
        long elapsedMs = now - lastDroppedFrameTime;
        if (delta > 0) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("lostFrames", (int) delta);
            attrs.put("lostFramesDuration", (int) elapsedMs);
            attrs.put("eventCount", 1);
            sendVideoEvent("CONTENT_DROPPED_FRAMES", attrs);
        }
        lastDroppedFrames    = current;
        lastDroppedFrameTime = now;
    }

    // -------------------------------------------------------------------------
    // Extra attributes — rendition name from Quality.getName()
    // -------------------------------------------------------------------------

    /**
     * Adds contentRenditionName to every event that includes rendition attributes.
     * Ref: Quality.getName() — "The name of the quality as defined in the manifest."
     */
    @Override
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr = super.getAttributes(action, attributes);

        attr.put("contentPlayrate", getPlayrate());

        if (action.equals(CONTENT_RENDITION_CHANGE)) {
            attr.put("shift", renditionChangeShift);
        }

        VideoQuality q = getActiveVideoQuality();
        if (q != null && q.getName() != null && !q.getName().isEmpty()) {
            attr.put("contentRenditionName", q.getName());
        }
        return attr;
    }

    // -------------------------------------------------------------------------
    // Rendition change helpers
    // -------------------------------------------------------------------------

    /**
     * Return the direction of the last rendition change ("up" or "down"), or null.
     */
    public String getRenditionChangeShift() {
        return renditionChangeShift;
    }

    /**
     * Returns the active video quality from the first enabled video track, or null.
     */
    private VideoQuality getActiveVideoQuality() {
        if (player == null) return null;
        for (MediaTrack<VideoQuality> track : player.getVideoTracks()) {
            if (track.isEnabled()) {
                VideoQuality q = track.getActiveQuality();
                if (q != null) return q;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Overridden send — attach renditionChangeShift on rendition events
    // -------------------------------------------------------------------------

    /**
     * Obtain the THEOplayerView held by this tracker.
     */
    public THEOplayerView getTHEOPlayerView() {
        return theoPlayerView;
    }

    /**
     * Obtain the underlying THEOplayer Player interface.
     */
    public Player getPlayer() {
        return player;
    }
}
