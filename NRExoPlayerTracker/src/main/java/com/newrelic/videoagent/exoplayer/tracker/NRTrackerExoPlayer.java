package com.newrelic.videoagent.exoplayer.tracker;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;

import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.exoplayer.BuildConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.newrelic.videoagent.core.NRDef.*;

/**
 * New Relic Video tracker for ExoPlayer.
 */
public class NRTrackerExoPlayer extends NRVideoTracker implements Player.Listener, AnalyticsListener {

    protected ExoPlayer player;

    protected long bitrateEstimate;
    protected int lastHeight;
    protected int lastWidth;
    protected List<Uri> playlist;
    protected int lastWindow;
    protected String renditionChangeShift;
    protected long actualBitrate;

    /**
     * Init a new ExoPlayer tracker.
     */
    public NRTrackerExoPlayer() {
    }

    /**
     * Init a new ExoPlayer tracker.
     *
     * @param player ExoPlayer instance.
     */
    public NRTrackerExoPlayer(ExoPlayer player) {
        setPlayer(player);
    }

    /**
     * Set player.
     *
     * @param player Player instance.
     */
    @Override
    public void setPlayer(Object player) {
        this.player = (ExoPlayer) player;
        registerListeners();
        super.setPlayer(player);
    }

    /**
     * Generate ExoPlayer attributes.
     *
     * @param action     Action being generated.
     * @param attributes Specific attributes sent along the action.
     * @return Map of attributes with action-specific data.
     */
    @Override
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr = super.getAttributes(action, attributes);

        // Implement getter for "playhead"
        if (getState().isAd) {
            attr.put("adPlayrate", getPlayrate());
        } else {
            attr.put("contentPlayrate", getPlayrate());
        }

        if (action.equals(CONTENT_RENDITION_CHANGE) || action.equals(AD_RENDITION_CHANGE)) {
            attr.put("shift", renditionChangeShift);
        }

        return attr;
    }

    /**
     * Get player name.
     *
     * @return Player name from Media3 library.
     */
    public String getPlayerName() {
        return MediaLibraryInfo.TAG;
    }

    /**
     * Get player version.
     *
     * @return Player version from Media3 library.
     */
    public String getPlayerVersion() {
        return MediaLibraryInfo.VERSION;
    }

    /**
     * Get tracker name.
     *
     * @return The simple class name of this tracker.
     */
    public String getTrackerName() {
        return "ExoPlayer2Tracker";
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
     * Get bitrate.
     *
     * @return Attribute.
     */
    public Long getBitrate() {
        return bitrateEstimate;
    }

    public Long getActualBitrate() {
        return actualBitrate;
    }

    /**
     * Get rendition bitrate.
     *
     * @return Attribute.
     */
    public Long getRenditionBitrate() {
        return getBitrate();
    }

    /**
     * Get rendition width.
     *
     * @return Attribute.
     */
    public Long getRenditionWidth() {
        if (player == null) return null;
        else if (getPlayer().getVideoFormat() == null) return null;

        return (long) getPlayer().getVideoFormat().width;
    }

    /**
     * Get rendition height.
     *
     * @return Attribute.
     */
    public Long getRenditionHeight() {
        if (player == null) return null;
        else if (getPlayer().getVideoFormat() == null) return null;

        return (long) getPlayer().getVideoFormat().height;
    }

    /**
     * Get stream duration.
     *
     * @return Attribute.
     */
    public Long getDuration() {
        if (player == null) return null;

        return Math.max(player.getDuration() , 0);
    }

    /**
     * Get current playhead.
     *
     * @return Attribute.
     */
    public Long getPlayhead() {
        if (player == null) return null;

        return player.getContentPosition();
    }

    /**
     * Get stream source.
     *
     * @return Attribute.
     */
    public String getSrc() {
        // Prefer direct MediaItem URI if available
        if (player == null || player.getCurrentMediaItem() == null) return null;
        if (player.getCurrentMediaItem().localConfiguration != null) {
            return player.getCurrentMediaItem().localConfiguration.uri.toString();
        }
        // Fallback to playlist if available
        if (getPlaylist() != null) {
            try {
                Uri src = getPlaylist().get(player.getCurrentMediaItemIndex());
                return src.toString();
            } catch(Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get playrate.
     *
     * @return Attribute.
     */
    public Double getPlayrate() {
        if (player == null) return null;

        return (double)player.getPlaybackParameters().speed;
    }

    /**
     * Get video frames per second.
     *
     * @return Attribute.
     */
    public Double getFps() {
        if (player == null) return null;

        if (getPlayer().getVideoFormat() != null) {
            if (getPlayer().getVideoFormat().frameRate > 0) {
                return (double) getPlayer().getVideoFormat().frameRate;
            }
        }

        return null;
    }

    /**
     * Get is muted.
     *
     * @return Attribute.
     */
    public Boolean getIsMuted() {
        if (player == null) return null;

        return (getPlayer().getVolume() == 0);
    }


    /**
     * Get the title from the currently played media
     *
     * @return String of the current title
     */
    public String getTitle() {
        // Try to get title from MediaItem metadata
        if (player != null && player.getCurrentMediaItem() != null) {
            MediaMetadata mm = player.getCurrentMediaItem().mediaMetadata;
            if (mm != null && mm.title != null) {
                String contentTitle = mm.title.toString();
                if (mm.subtitle != null) {
                    contentTitle += ": " + mm.subtitle;
                }
                return contentTitle;
            }
            // Fallback: use URI if title is not set
            if (player.getCurrentMediaItem().localConfiguration != null) {
                return player.getCurrentMediaItem().localConfiguration.uri.getLastPathSegment();
            }
        }
        // Fallback: use playlist URI if available
        if (getPlaylist() != null && player != null) {
            try {
                Uri src = getPlaylist().get(player.getCurrentMediaItemIndex());
                return src.getLastPathSegment();
            } catch(Exception e) {
                // ignore
            }
        }
        return "Unknown";
    }

    /**
     * Get player instance.
     *
     * @return Attribute.
     */
    public ExoPlayer getPlayer() {
        return player;
    }

    /**
     * Set source URL.
     *
     * @param uris List of URIs.
     */
    public void setSrc(List<Uri> uris) {
        setPlaylist(uris);
    }

    /**
     * Register ExoPlayer listeners and analytics listeners.
     */
    @Override
    public void registerListeners() {
        super.registerListeners();

        player.addListener(this);
        player.addAnalyticsListener(this);
    }

    /**
     * Unregister ExoPlayer listeners and analytics listeners, and reset state.
     */
    @Override
    public void unregisterListeners() {
        super.unregisterListeners();
        player.removeListener(this);
        player.removeAnalyticsListener(this);
        player = null;
        resetState();
    }

    private void resetState() {
        bitrateEstimate = 0;
        lastWindow = 0;
        lastWidth = 0;
        lastHeight = 0;
        actualBitrate = 0;
    }

    /**
     * Get playlist URIs.
     *
     * @return Playlist URIs.
     */
    public List<Uri> getPlaylist() {
        return playlist;
    }

    /**
     * Set playlist URIs.
     *
     * @param playlist Playlist URIs.
     */
    public void setPlaylist(List<Uri> playlist) {
        this.playlist = playlist;
    }

    // senders

    @Override
    public void sendEnd() {
        super.sendEnd();
        resetState();
    }

    /**
     * Send dropped frames event.
     *
     * @param count Number of dropped frames.
     * @param elapsed Time elapsed.
     */
    public void sendDroppedFrame(int count, int elapsed) {
        Map<String, Object> attr = new HashMap<>();
        attr.put("lostFrames", count);
        attr.put("lostFramesDuration", elapsed);
//        generatePlayElapsedTime();
        if (getState().isAd) {
            sendVideoAdEvent("AD_DROPPED_FRAMES", attr);
        }
        else {
            sendVideoEvent("CONTENT_DROPPED_FRAMES", attr);
        }
    }

    // ExoPlayer Player.EventListener

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        logOnPlayerStateChanged(playWhenReady, player.getPlaybackState());
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        logOnPlayerStateChanged(player.getPlayWhenReady(), playbackState);
    }

    private void logOnPlayerStateChanged(boolean playWhenReady, int playbackState) {
        NRLog.d("onPlayerStateChanged, payback state = " + playbackState + " {");

        if (playbackState == Player.STATE_READY) {
            NRLog.d("\tVideo Is Ready");

            if (getState().isBuffering) {
                sendBufferEnd();
            }

            if (getState().isSeeking) {
                sendSeekEnd();
            }

            if (getState().isRequested && !getState().isStarted) {
                sendStart();
            }
        } else if (playbackState == Player.STATE_ENDED) {
            NRLog.d("\tVideo Ended Playing");
            if (getState().isStarted) {
                if (getState().isBuffering) {
                    sendBufferEnd();
                }
                if (getState().isSeeking) {
                    sendSeekEnd();
                }
                sendEnd();
            }
        } else if (playbackState == Player.STATE_BUFFERING) {
            NRLog.d("\tVideo Is Buffering");

            if (!getState().isRequested) {
                sendRequest();
            }

            if (!getState().isBuffering && !player.isPlayingAd()) {
                sendBufferStart();
            }
        }

        if (playWhenReady && playbackState == Player.STATE_READY) {
            NRLog.d("\tVideo Playing");

            if (getState().isRequested && !getState().isStarted) {
                sendStart();
            } else if (getState().isPaused && !player.isPlayingAd()) {
                sendResume();
            } else if (!getState().isRequested && !getState().isStarted) {
                NRLog.d("LAST CHANCE TO SEND REQUEST START. isPlayingAd = " + player.isPlayingAd());
                if (!player.isPlayingAd()) {
                    sendRequest();
                    sendStart();
                }
            }
        } else if (playWhenReady) {
            NRLog.d("\tVideo Not Playing");
        } else {
            NRLog.d("\tVideo Paused");

            if (getState().isPlaying && !player.isPlayingAd()) {
                sendPause();
            }
        }

        NRLog.d("}");
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        NRLog.d("onPlayerError");
        sendError(error);
    }

    // ExoPlayer AnalyticsListener

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            NRLog.d("onSeekStarted analytics");

            if (!getState().isSeeking) {
                sendSeekStart();
            }
        }
    }

    @Override
    public void onTracksChanged(@NonNull EventTime eventTime, @NonNull Tracks tracksInfo) {
        NRLog.d("onTracksChanged analytics");

        // Next track in the playlist
        if (player.getCurrentMediaItemIndex() != lastWindow) {
            NRLog.d("Next video in the playlist starts");
            lastWindow = player.getCurrentMediaItemIndex();
            sendRequest();
        }
    }

    @Override
    public void onLoadError(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo, @NonNull MediaLoadData mediaLoadData, @NonNull IOException error, boolean wasCanceled) {
        NRLog.d("onLoadError analytics");
        sendError(error);
    }

    @Override
    public void onLoadCompleted(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo, @NonNull MediaLoadData mediaLoadData) {
        if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA
                && mediaLoadData.trackType == C.TRACK_TYPE_VIDEO
                && loadEventInfo.loadDurationMs > 0) {
            // Calculate the bitrate for this specific chunk in bits per second
            this.actualBitrate = (loadEventInfo.bytesLoaded * 8 * 1000) / loadEventInfo.loadDurationMs;
        }
    }

    @Override
    public void onBandwidthEstimate(@NonNull AnalyticsListener.EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        NRLog.d("onBandwidthEstimate analytics");

        this.bitrateEstimate = bitrateEstimate;
    }

    @Override
    public void onDroppedVideoFrames(@NonNull AnalyticsListener.EventTime eventTime, int droppedFrames, long elapsedMs) {
        NRLog.d("onDroppedVideoFrames analytics");
        if (!player.isPlayingAd()) {
            sendDroppedFrame(droppedFrames, (int) elapsedMs);
        }
    }

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        int width = videoSize.width;
        int height = videoSize.height;
        NRLog.d("onVideoSizeChanged analytics, H = " + height + " W = " + width);

        if (player.isPlayingAd()) return;

        long currMul = (long) width * height;
        long lastMul = (long) lastWidth * lastHeight;

        if (lastMul != 0) {
            if (lastMul < currMul) {
                renditionChangeShift = "up";
                sendRenditionChange();
            } else if (lastMul > currMul) {
                renditionChangeShift = "down";
                sendRenditionChange();
            }
        }

        lastHeight = height;
        lastWidth = width;
    }

    @Override
    public String getLanguage() {
        if (player != null && player.getCurrentMediaItem() != null) {
            // 1. Try to get language from URI query param
            if (player.getCurrentMediaItem().localConfiguration != null) {
                Uri uri = player.getCurrentMediaItem().localConfiguration.uri;
                String lang = uri.getQueryParameter("lang");
                if (lang != null) return lang;
                // 2. Try to get language from any path segment
                for (String segment : uri.getPathSegments()) {
                    if (segment.matches("[a-zA-Z]{2,5}")) return segment;
                }
                // 3. Try to get language from last path segment
                String lastSegment = uri.getLastPathSegment();
                if (lastSegment != null && lastSegment.matches("[a-zA-Z]{2,5}")) return lastSegment;
            }
            // 4. Try to get language from MediaMetadata title or description
            MediaMetadata mm = player.getCurrentMediaItem().mediaMetadata;
            if (mm != null) {
                String[] fields = { mm.title != null ? mm.title.toString() : null, mm.description != null ? mm.description.toString() : null };
                for (String field : fields) {
                    if (field == null) continue;
                    // Look for patterns like (en), [en], en:
                    Matcher m = Pattern.compile("(?:\\(|\\[)?([a-zA-Z]{2,5})(?:\\)|\\])?|([a-zA-Z]{2,5}):").matcher(field);
                    if (m.find()) {
                        if (m.group(1) != null) return m.group(1);
                        if (m.group(2) != null) return m.group(2);
                    }
                }
            }
        }
        // 5. Fallback: try playlist URI
        if (getPlaylist() != null && player != null) {
            try {
                Uri src = getPlaylist().get(player.getCurrentMediaItemIndex());
                String lang = src.getQueryParameter("lang");
                if (lang != null) return lang;
                for (String segment : src.getPathSegments()) {
                    if (segment.matches("[a-zA-Z]{2,5}")) return segment;
                }
                String lastSegment = src.getLastPathSegment();
                if (lastSegment != null && lastSegment.matches("[a-zA-Z]{2,5}")) return lastSegment;
            } catch(Exception e) {
                // ignore
            }
        }
        return null;
    }

}
