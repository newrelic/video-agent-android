package com.newrelic.videoagent.exoplayer.tracker;

import android.net.Uri;

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
import static com.newrelic.videoagent.core.NRDef.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Init a new ExoPlayer tracker.
     */
    public NRTrackerExoPlayer() {
        // Superclass constructor handles initialization of eventAttributes and timeSinceTable
    }

    /**
     * Init a new ExoPlayer tracker and set the player instance.
     *
     * @param player ExoPlayer instance.
     */
    public NRTrackerExoPlayer(ExoPlayer player) {
        setPlayer(player);
    }

    /**
     * Set the ExoPlayer instance for this tracker.
     * Registers necessary listeners for playback and analytics events.
     *
     * @param player Player instance (expected to be an ExoPlayer).
     */
    @Override
    public void setPlayer(Object player) {
        if (!(player instanceof ExoPlayer)) {
            NRLog.e("NRTrackerExoPlayer: Provided player is not an ExoPlayer instance.");
            return;
        }
        this.player = (ExoPlayer) player;
        registerListeners(); // Register this tracker as a listener to the ExoPlayer
        super.setPlayer(player); // Call superclass method to notify player is ready
    }

    /**
     * Generate ExoPlayer-specific attributes and combine them with base video attributes.
     *
     * @param action     Action being generated.
     * @param attributes Specific attributes sent along the action.
     * @return Map of attributes including ExoPlayer details.
     */
    @Override
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        // Call superclass to get common video attributes (like trackerName, viewSession, etc.)
        Map<String, Object> attr = super.getAttributes(action, attributes);

        // Add playrate attribute based on whether it's an ad or content
        if (getState().isAd) {
            attr.put("adPlayrate", getPlayrate());
        } else {
            attr.put("contentPlayrate", getPlayrate());
        }

        // Add rendition change shift attribute if applicable
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
    @Override // Override method from NRVideoTracker
    public String getPlayerName() {
        return MediaLibraryInfo.TAG;
    }

    /**
     * Get player version.
     *
     * @return Player version from Media3 library.
     */
    @Override // Override method from NRVideoTracker
    public String getPlayerVersion() {
        return MediaLibraryInfo.VERSION;
    }

    /**
     * Get tracker name.
     *
     * @return The simple class name of this tracker.
     */
    @Override // Override method from NRVideoTracker
    public String getTrackerName() {
        return "ExoPlayer2Tracker"; // Specific name for ExoPlayer tracker
    }

    /**
     * Get tracker version.
     *
     * @return The version defined in BuildConfig.
     */
    @Override // Override method from NRVideoTracker
    public String getTrackerVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * Get tracker source string.
     *
     * @return The source string for this tracker.
     */
    @Override // Override method from NRVideoTracker
    public String getTrackerSrc() {
        return SRC; // This constant should be defined in NRDef
    }

    /**
     * Get estimated bitrate.
     *
     * @return Current bitrate estimate, or null if not available.
     */
    @Override // Override method from NRVideoTracker
    public Long getBitrate() {
        return bitrateEstimate;
    }

    /**
     * Get rendition bitrate.
     * For ExoPlayer, this is the same as the overall bitrate estimate.
     *
     * @return Current rendition bitrate, or null if not available.
     */
    @Override // Override method from NRVideoTracker
    public Long getRenditionBitrate() {
        return getBitrate();
    }

    /**
     * Get rendition width.
     *
     * @return Current video width, or null if player or format is not available.
     */
    @Override // Override method from NRVideoTracker
    public Long getRenditionWidth() {
        if (player == null) return null;
        if (player.getVideoFormat() == null) return null;
        return (long) player.getVideoFormat().width;
    }

    /**
     * Get rendition height.
     *
     * @return Current video height, or null if player or format is not available.
     */
    @Override // Override method from NRVideoTracker
    public Long getRenditionHeight() {
        if (player == null) return null;
        if (player.getVideoFormat() == null) return null;
        return (long) player.getVideoFormat().height;
    }

    /**
     * Get stream duration.
     *
     * @return Total duration of the media in milliseconds, or null if not available.
     */
    @Override // Override method from NRVideoTracker
    public Long getDuration() {
        if (player == null) return null;
        return player.getDuration();
    }

    /**
     * Get current playback position in milliseconds.
     *
     * @return Current content position in milliseconds, or null if not available.
     */
    @Override // Override method from NRVideoTracker
    public Long getPlayhead() {
        if (player == null) return null;
        return player.getContentPosition();
    }

    /**
     * Get stream source URI.
     * Retrieves the current media item's URI from the playlist.
     *
     * @return The URI string of the current media item, or null if not available.
     */
    @Override // Override method from NRVideoTracker
    public String getSrc() {
        if (player == null || playlist == null || playlist.isEmpty()) return null;

        int currentIndex = player.getCurrentMediaItemIndex();
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            try {
                Uri src = playlist.get(currentIndex);
                return src.toString();
            } catch(IndexOutOfBoundsException e) {
                // This might happen if player's current index is out of sync with a dynamically changing playlist
                NRLog.e("NRTrackerExoPlayer: Error getting source from playlist: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * Get playback speed.
     *
     * @return Playback speed multiplier, or null if not available.
     */
    // REMOVED @Override: This method is not overriding a method in NRVideoTracker
    public Double getPlayrate() {
        if (player == null) return null;
        return (double)player.getPlaybackParameters().speed;
    }

    /**
     * Get video frames per second.
     *
     * @return Frame rate, or null if not available.
     */
    @Override // Override method from NRVideoTracker
    public Double getFps() {
        if (player == null) return null;
        if (player.getVideoFormat() != null && player.getVideoFormat().frameRate > 0) {
            return (double) player.getVideoFormat().frameRate;
        }
        return null;
    }

    /**
     * Get whether video is muted or not.
     *
     * @return True if player volume is 0, false otherwise.
     */
    @Override // Override method from NRVideoTracker
    public Boolean getIsMuted() {
        if (player == null) return null;
        return (player.getVolume() == 0f); // Use float comparison for volume
    }

    /**
     * Get the title from the currently played media item's metadata.
     *
     * @return String of the current title, or "Unknown" if not available.
     */
    @Override // Override method from NRVideoTracker
    public String getTitle() {
        String contentTitle = "Unknown";
        if (player != null && player.getCurrentMediaItem() != null && player.getCurrentMediaItem().mediaMetadata != null) {
            MediaMetadata mm = player.getCurrentMediaItem().mediaMetadata;
            if (mm.title != null) {
                contentTitle = mm.title.toString();
            }
            if (mm.subtitle != null) {
                contentTitle += ": " + mm.subtitle; // Usually the episode title is available in subtitle
            }
        }
        return contentTitle;
    }

    /**
     * Get player instance.
     *
     * @return The ExoPlayer instance.
     */
    public ExoPlayer getPlayer() {
        return player;
    }

    /**
     * Set source URLs for the playlist.
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
        super.registerListeners(); // Call superclass to register any base listeners

        if (player != null) {
            player.addListener(this); // Registers this class as a Player.Listener
            player.addAnalyticsListener(this); // Registers this class as an AnalyticsListener
            NRLog.d("NRTrackerExoPlayer: Player listeners registered.");
        } else {
            NRLog.e("NRTrackerExoPlayer: Cannot register listeners, player is null.");
        }
    }

    /**
     * Unregister ExoPlayer listeners and analytics listeners, and reset internal state.
     */
    @Override
    public void unregisterListeners() {
        super.unregisterListeners(); // Call superclass to unregister any base listeners
        if (player != null) {
            player.removeListener(this);
            player.removeAnalyticsListener(this);
            player = null; // Clear player reference
            NRLog.d("NRTrackerExoPlayer: Player listeners unregistered and player reference cleared.");
        } else {
            NRLog.d("NRTrackerExoPlayer: Player is already null, nothing to unregister.");
        }
        resetState(); // Reset tracker's internal state variables
    }

    /**
     * Resets internal state variables specific to ExoPlayer tracking.
     */
    private void resetState() {
        bitrateEstimate = 0;
        lastWindow = 0;
        lastWidth = 0;
        lastHeight = 0;
        renditionChangeShift = null;
        playlist = null; // Clear playlist reference
        NRLog.d("NRTrackerExoPlayer: Internal state reset.");
    }

    /**
     * Get playlist URIs.
     *
     * @return The list of playlist URIs.
     */
    public List<Uri> getPlaylist() {
        return playlist;
    }

    /**
     * Set playlist URIs.
     *
     * @param playlist List of URIs.
     */
    public void setPlaylist(List<Uri> playlist) {
        this.playlist = playlist;
        NRLog.d("NRTrackerExoPlayer: Playlist set with " + (playlist != null ? playlist.size() : 0) + " items.");
    }

    // --- ExoPlayer Player.Listener Callbacks ---

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        logOnPlayerStateChanged(playWhenReady, player.getPlaybackState());
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        logOnPlayerStateChanged(player.getPlayWhenReady(), playbackState);
    }

    /**
     * Helper method to log and process player state changes.
     * This method contains the core logic for sending video events based on player state.
     *
     * @param playWhenReady Boolean indicating if playback is ready.
     * @param playbackState Current playback state (e.g., STATE_READY, STATE_BUFFERING).
     */
    private void logOnPlayerStateChanged(boolean playWhenReady, int playbackState) {
        NRLog.d("NRTrackerExoPlayer: onPlayerStateChanged, playback state = " + playbackState + ", playWhenReady = " + playWhenReady);

        if (playbackState == Player.STATE_READY) {
            NRLog.d("  Video is Ready.");
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
            NRLog.d("  Video Ended Playing.");
            if (getState().isStarted) {
                if (getState().isBuffering) {
                    sendBufferEnd();
                }
                if (getState().isSeeking) {
                    sendSeekEnd();
                }
                sendEnd(); // Call super.sendEnd() which handles common end logic
            }
        } else if (playbackState == Player.STATE_BUFFERING) {
            NRLog.d("  Video Is Buffering.");
            if (!getState().isRequested) {
                sendRequest();
            }
            if (!getState().isBuffering && !player.isPlayingAd()) {
                sendBufferStart();
            }
        }

        if (playWhenReady && playbackState == Player.STATE_READY) {
            NRLog.d("  Video Playing.");
            if (getState().isRequested && !getState().isStarted) {
                sendStart();
            } else if (getState().isPaused && !player.isPlayingAd()) {
                sendResume();
            } else if (!getState().isRequested && !getState().isStarted) {
                NRLog.d("  LAST CHANCE TO SEND REQUEST START. isPlayingAd = " + player.isPlayingAd());
                if (!player.isPlayingAd()) {
                    sendRequest();
                    sendStart();
                }
            }
        } else if (playWhenReady) {
            NRLog.d("  Video Not Playing (but playWhenReady is true).");
        } else {
            NRLog.d("  Video Paused.");
            if (getState().isPlaying && !player.isPlayingAd()) {
                sendPause();
            }
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        NRLog.e("NRTrackerExoPlayer: Player error occurred: " + error.getMessage()); // Log error message
        sendError(error); // Calls super.sendError which handles error event sending
    }

    // --- ExoPlayer AnalyticsListener Callbacks ---

    @Override
    public void onPositionDiscontinuity(EventTime eventTime, Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            NRLog.d("NRTrackerExoPlayer: onPositionDiscontinuity - SEEK detected.");
            if (!getState().isSeeking) {
                sendSeekStart();
            }
        }
    }

    @Override
    public void onTracksChanged(EventTime eventTime, Tracks tracksInfo) {
        NRLog.d("NRTrackerExoPlayer: onTracksChanged.");

        // Check if a new track in the playlist has started
        if (player.getCurrentMediaItemIndex() != lastWindow) {
            NRLog.d("NRTrackerExoPlayer: Next video in the playlist starts. Current window index = " + player.getCurrentMediaItemIndex());
            lastWindow = player.getCurrentMediaItemIndex();
            sendRequest(); // Send a request event for the new video item
        }
    }

    @Override
    public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
        NRLog.e("NRTrackerExoPlayer: Load error occurred: " + error.getMessage()); // Log load error message
        sendError(error); // Send an error event
    }

    @Override
    public void onBandwidthEstimate(AnalyticsListener.EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        NRLog.d("NRTrackerExoPlayer: onBandwidthEstimate - " + bitrateEstimate + " bps.");
        this.bitrateEstimate = bitrateEstimate; // Update bitrate estimate
    }


    @Override
    public void onDroppedVideoFrames(AnalyticsListener.EventTime eventTime, int droppedFrames, long elapsedMs) {
        NRLog.d("NRTrackerExoPlayer: onDroppedVideoFrames, dropped = " + droppedFrames + ", elapsed = " + elapsedMs);
        if (getState().isBuffering || getState().isSeeking) {
            return;
        }
        sendDroppedFrame(droppedFrames, (int) elapsedMs); // ERROR HERE
    }

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        int width = videoSize.width;
        int height = videoSize.height;
        NRLog.d("NRTrackerExoPlayer: onVideoSizeChanged - H = " + height + ", W = " + width);

        if (player.isPlayingAd()) return; // Ignore size changes during ad playback

        long currArea = (long) width * height;
        long lastArea = (long) lastWidth * lastHeight;

        if (lastArea != 0) { // Only check for changes if a previous size was recorded
            if (lastArea < currArea) {
                renditionChangeShift = "up";
                sendRenditionChange();
            } else if (lastArea > currArea) {
                renditionChangeShift = "down";
                sendRenditionChange();
            }
        }

        lastHeight = height;
        lastWidth = width;
    }
}