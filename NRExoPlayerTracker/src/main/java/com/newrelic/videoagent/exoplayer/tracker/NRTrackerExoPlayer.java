package com.newrelic.videoagent.exoplayer.tracker;

import android.net.Uri;

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

import static com.newrelic.videoagent.core.NRDef.*;

/**
 * New Relic Video tracker for ExoPlayer.
 */
public class NRTrackerExoPlayer extends NRVideoTracker implements Player.Listener, AnalyticsListener {

    protected ExoPlayer player;

    private long bitrateEstimate;
    private int lastHeight;
    private int lastWidth;
    private List<Uri> playlist;
    private int lastWindow;
    private String renditionChangeShift;

    /**
     * Init a new ExoPlayer tracker.
     */
    public NRTrackerExoPlayer() {}

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
     * @param action Action being generated.
     * @param attributes Specific attributes sent along the action.
     * @return
     */
    @Override
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr = super.getAttributes(action, attributes);

        // Implement getter for "playhead"
        if (getState().isAd) {
            attr.put("adPlayrate", getPlayrate());
        }
        else {
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
     * @return Attribute.
     */
    public String getPlayerName() {
        return "ExoPlayer2";
    }

    /**
     * Get player version.
     *
     * @return Attribute.
     */
    public String getPlayerVersion() {
        return "2.x";
    }

    /**
     * Get tracker name.
     *
     * @return Atribute.
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
     * Get bitrate.
     *
     * @return Attribute.
     */
    public Long getBitrate() {
        return bitrateEstimate;
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

        return (long)getPlayer().getVideoFormat().height;
    }

    /**
     * Get stream duration.
     *
     * @return Attribute.
     */
    public Long getDuration() {
        if (player == null) return null;

        return player.getDuration();
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
        if (player == null) return null;

        if (getPlaylist() != null) {
            NRLog.d("Current window index = " + player.getCurrentMediaItemIndex());
            try {
                Uri src = getPlaylist().get(player.getCurrentMediaItemIndex());
                return src.toString();
            }
            catch (Exception e) {
                return null;
            }
        }
        else {
            return null;
        }
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
                return (double)getPlayer().getVideoFormat().frameRate;
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
     * Get player instance.
     *
     * @return Attribute.
     */
    private ExoPlayer getPlayer() {
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
        if (getState().isAd) {
            sendEvent("AD_DROPPED_FRAMES", attr);
        }
        else {
            sendEvent("CONTENT_DROPPED_FRAMES", attr);
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
        }
        else if (playbackState == Player.STATE_ENDED) {
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
        }
        else if (playbackState == Player.STATE_BUFFERING) {
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
            }
            else if (getState().isPaused && !player.isPlayingAd()) {
                sendResume();
            }
            else if (!getState().isRequested && !getState().isStarted) {
                NRLog.d("LAST CHANCE TO SEND REQUEST START. isPlayingAd = " + player.isPlayingAd());
                if (!player.isPlayingAd()) {
                    sendRequest();
                    sendStart();
                }
            }
        }
        else if (playWhenReady) {
            NRLog.d("\tVideo Not Playing");
        }
        else {
            NRLog.d("\tVideo Paused");

            if (getState().isPlaying && !player.isPlayingAd()) {
                sendPause();
            }
        }

        NRLog.d("}");
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        NRLog.d("onPlayerError");
        sendError(error);
    }

    // ExoPlayer AnalyticsListener

    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            NRLog.d("onSeekStarted analytics");

            if (!getState().isSeeking) {
                sendSeekStart();
            }
        }
    }

    @Override
    public void onTracksChanged(EventTime eventTime, Tracks tracksInfo) {
        NRLog.d("onTracksChanged analytics");

        // Next track in the playlist
        if (player.getCurrentMediaItemIndex() != lastWindow) {
            NRLog.d("Next video in the playlist starts");
            lastWindow = player.getCurrentMediaItemIndex();
            sendRequest();
        }
    }

    @Override
    public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
        NRLog.d("onLoadError analytics");
        sendError(error);
    }

    @Override
    public void onBandwidthEstimate(AnalyticsListener.EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        NRLog.d("onBandwidthEstimate analytics");

        this.bitrateEstimate = bitrateEstimate;
    }

    @Override
    public void onDroppedVideoFrames(AnalyticsListener.EventTime eventTime, int droppedFrames, long elapsedMs) {
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

        long currMul = width * height;
        long lastMul = lastWidth * lastHeight;

        if (lastMul != 0) {
            if (lastMul < currMul) {
                renditionChangeShift = "up";
                sendRenditionChange();
            }
            else if (lastMul > currMul) {
                renditionChangeShift = "down";
                sendRenditionChange();
            }
        }

        lastHeight = height;
        lastWidth = width;
    }
}
