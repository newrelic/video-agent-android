package com.newrelic.videoagent.theoplayer.tracker;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.theoplayer.BuildConfig;

import com.theoplayer.android.api.THEOplayerView;
import com.newrelic.videoagent.theoplayer.exception.TheoErrorHandler;
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
import com.theoplayer.android.api.event.track.mediatrack.video.list.VideoTrackListEventTypes;
import com.theoplayer.android.api.THEOplayerGlobal;
import com.theoplayer.android.api.metrics.Metrics;
import com.theoplayer.android.api.player.Player;
import com.theoplayer.android.api.player.track.mediatrack.MediaTrack;
import com.theoplayer.android.api.player.track.mediatrack.quality.AudioQuality;
import com.theoplayer.android.api.player.track.mediatrack.quality.VideoQuality;

import java.util.HashMap;
import java.util.Map;

import static com.newrelic.videoagent.core.NRDef.SRC;

/**
 * New Relic Video tracker for THEOplayer (Dolby OptiView Player).
 *
 * <p>Usage:</p>
 * <pre>
 *   NRTrackerTHEOPlayer tracker = new NRTrackerTHEOPlayer(config, theoPlayerView);
 *   Integer trackerId = NRVideo.addPlayer(
 *       new NRVideoPlayerConfiguration("theo-player", tracker, null, null));
 *
 *   // Forward Activity lifecycle
 *   {@literal @}Override protected void onResume()  { super.onResume();  tracker.onResume(); }
 *   {@literal @}Override protected void onPause()   { super.onPause();   tracker.onPause(); }
 *   {@literal @}Override protected void onDestroy() { super.onDestroy(); tracker.onDestroy();
 *                                                     NRVideo.releaseTracker(trackerId); }
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
    private long lastDroppedFrames = 0;

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
    private EventListener<com.theoplayer.android.api.event.track.mediatrack.video.list.TrackListChangeEvent> onVideoTrackChange;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

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
        unregisterListeners();
        if (theoPlayerView != null) {
            theoPlayerView.onDestroy();
        }
    }

    // -------------------------------------------------------------------------
    // Event registration
    // -------------------------------------------------------------------------

    @Override
    public void registerListeners() {
        if (player == null) return;

        NRLog.d("NRTrackerTHEOPlayer: registerListeners");

        onSourceChange = event -> {
            NRLog.d("THEOplayer: SOURCECHANGE");
            // End any active session before starting a new one (playlist / source swap)
            if (getState().isRequested) {
                sendEnd();
            }
            renditionChangeShift = null;
            lastRenditionWidth = 0;
            lastRenditionHeight = 0;
            lastDroppedFrames = 0;   // reset cumulative counter on new source
            sendRequest();
        };

        onPlaying = event -> {
            NRLog.d("THEOplayer: PLAYING");
            if (!getState().isStarted) {
                // First frame after source change
                sendStart();
            } else if (getState().isBuffering) {
                // Rebuffer resolved
                sendBufferEnd();
            } else if (getState().isSeeking) {
                // Seek resolved without explicit SEEKED (defensive)
                sendSeekEnd();
            }
        };

        onPlay = event -> {
            NRLog.d("THEOplayer: PLAY");
            // Resume from user-initiated pause
            if (getState().isPaused) {
                sendResume();
            }
        };

        onPause = event -> {
            NRLog.d("THEOplayer: PAUSE");
            sendPause();
        };

        onWaiting = event -> {
            NRLog.d("THEOplayer: WAITING (isSeeking=" + player.isSeeking() + ")");
            // THEOplayer fires WAITING during seeks too; guard to avoid a spurious
            // CONTENT_BUFFER_START event that would have no matching CONTENT_BUFFER_END.
            if (!player.isSeeking()) {
                sendBufferStart();
            }
        };

        onSeeking = event -> {
            NRLog.d("THEOplayer: SEEKING");
            sendSeekStart();
        };

        onSeeked = event -> {
            NRLog.d("THEOplayer: SEEKED");
            // Close any open buffer that was triggered by the seek
            if (getState().isBuffering) {
                sendBufferEnd();
            }
            sendSeekEnd();
        };

        onEnded = event -> {
            NRLog.d("THEOplayer: ENDED");
            sendEnd();
        };

        onError = event -> {
            NRLog.d("THEOplayer: ERROR - " + event.getErrorObject().getMessage());
            TheoErrorHandler handler = new TheoErrorHandler(0, event.getErrorObject().getMessage());
            sendError(handler.getErrorCode(), handler.getErrorMessage());
        };

        onContentProtectionError = event -> {
            NRLog.d("THEOplayer: CONTENTPROTECTIONERROR");
            String msg = (event.getErrorObject() != null) ? event.getErrorObject().getMessage() : "DRM / content-protection error";
            TheoErrorHandler handler = new TheoErrorHandler(0, msg);
            sendError(handler.getErrorCode(), handler.getErrorMessage());
        };

        onDurationChange = event -> {
            // Duration becomes finite after LOADEDMETADATA; no NR event needed — it is
            // read lazily by getDuration() on the next event that includes it.
            NRLog.d("THEOplayer: DURATIONCHANGE duration=" + player.getDuration());
        };

        onVideoTrackChange = event -> {
            NRLog.d("THEOplayer: video track CHANGE");
            VideoQuality quality = getActiveVideoQuality();
            if (quality == null) return;

            int newWidth  = quality.getWidth();
            int newHeight = quality.getHeight();
            long newArea  = (long) newWidth * newHeight;
            long lastArea = (long) lastRenditionWidth * lastRenditionHeight;

            if (lastArea != 0 && newArea != lastArea) {
                renditionChangeShift = (newArea > lastArea) ? "up" : "down";
                sendRenditionChange();
            }

            lastRenditionWidth  = newWidth;
            lastRenditionHeight = newHeight;
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
        player.getVideoTracks().addEventListener(VideoTrackListEventTypes.TRACKLISTCHANGE, onVideoTrackChange);
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
        player.getVideoTracks().removeEventListener(VideoTrackListEventTypes.TRACKLISTCHANGE, onVideoTrackChange);

        player               = null;
        theoPlayerView       = null;
        renditionChangeShift = null;
        lastRenditionWidth   = 0;
        lastRenditionHeight  = 0;
        lastDroppedFrames    = 0;
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
     * Observed bitrate — THEOplayer does not expose a separate measured throughput;
     * falls back to the manifest-advertised rendition bandwidth.
     */
    @Override
    public Long getBitrate() {
        return getRenditionBitrate();
    }

    @Override
    public Long getActualBitrate() {
        return getRenditionBitrate();
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
        // Fallback: last path segment of the src URL
        String src = player.getSrc();
        if (src != null && !src.isEmpty()) {
            int slash = src.lastIndexOf('/');
            int query = src.indexOf('?');
            int end   = (query > slash) ? query : src.length();
            if (slash >= 0 && end > slash + 1) {
                return src.substring(slash + 1, end);
            }
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
        long current = metrics.getDroppedVideoFrames();
        long delta   = current - lastDroppedFrames;
        if (delta > 0) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("lostFrames", (int) delta);
            attrs.put("lostFramesDuration", 0);  // Metrics API does not provide per-window duration
            attrs.put("eventCount", 1);
            sendVideoEvent("CONTENT_DROPPED_FRAMES", attrs);
        }
        lastDroppedFrames = current;
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
