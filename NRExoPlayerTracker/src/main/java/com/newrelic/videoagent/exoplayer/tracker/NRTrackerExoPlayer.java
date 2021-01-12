package com.newrelic.videoagent.exoplayer.tracker;

import android.net.Uri;
import android.view.Surface;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.exoplayer.BuildConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.newrelic.videoagent.core.NRDef.*;

public class NRTrackerExoPlayer extends NRVideoTracker implements Player.EventListener, AnalyticsListener {

    protected SimpleExoPlayer player;

    private static final long timerTrackTimeMs = 250;
    private long lastErrorTs = 0;
    private long bitrateEstimate;
    private int lastHeight;
    private int lastWidth;
    private List<Uri> playlist;
    private int lastWindow;
    private String renditionChangeShift;

    public NRTrackerExoPlayer(SimpleExoPlayer player) {
        setPlayer(player);
    }

    @Override
    public void setPlayer(Object player) {
        this.player = (SimpleExoPlayer) player;
        registerListeners();
        super.setPlayer(player);
    }

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

    public String getPlayerName() {
        return "ExoPlayer2";
    }

    public String getPlayerVersion() {
        return "2.x";
    }

    public String getTrackerName() {
        return "ExoPlayer2Tracker";
    }

    public String getTrackerVersion() {
        return BuildConfig.VERSION_NAME;
    }

    public Long getBitrate() {
        return bitrateEstimate;
    }

    public Long getRenditionBitrate() {
        return getBitrate();
    }

    public Long getRenditionWidth() {
        try {
            return (long) getPlayer().getVideoFormat().width;
        }
        catch (Exception e) {
            return null;
        }
    }

    public Long getRenditionHeight() {
        try {
            return (long)getPlayer().getVideoFormat().height;
        }
        catch (Exception e) {
            return 0L;
        }
    }

    public Long getDuration() {
        return player.getDuration();
    }

    public Long getPlayhead() {
        return player.getContentPosition();
    }

    public String getSrc() {
        if (getPlaylist() != null) {
            NRLog.d("Current window index = " + player.getCurrentWindowIndex());
            try {
                Uri src = getPlaylist().get(player.getCurrentWindowIndex());
                return src.toString();
            }
            catch (Exception e) {
                return "";
            }
        }
        else {
            return "";
        }
    }

    public Double getPlayrate() {
        return (double)player.getPlaybackParameters().speed;
    }

    public Double getFps() {
        if (getPlayer().getVideoFormat() != null) {
            if (getPlayer().getVideoFormat().frameRate > 0) {
                return (double)getPlayer().getVideoFormat().frameRate;
            }
        }

        return null;
    }

    public Boolean getIsMuted() {
        return (getPlayer().getVolume() == 0);
    }

    private SimpleExoPlayer getPlayer() {
        return player;
    }

    public void setSrc(List<Uri> uris) {
        setPlaylist(uris);
    }

    @Override
    public void registerListeners() {
        super.registerListeners();

        player.addListener(this);
        player.addAnalyticsListener(this);
    }

    @Override
    public void unregisterListeners() {
        super.unregisterListeners();
        player.removeListener(this);
        player.removeAnalyticsListener(this);
        resetState();
    }

    private void resetState() {
        bitrateEstimate = 0;
        lastWindow = 0;
        lastWidth = 0;
        lastHeight = 0;
    }

    public List<Uri> getPlaylist() {
        return playlist;
    }

    public void setPlaylist(List<Uri> playlist) {
        this.playlist = playlist;
    }

    // senders

    @Override
    public void sendEnd() {
        super.sendEnd();
        resetState();
    }

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
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        NRLog.d("onTimelineChanged");
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        NRLog.d("onLoadingChanged, Is Loading = " + isLoading);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

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
            else if (getState().isPaused) {
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

            if (getState().isPlaying) {
                sendPause();
            }
        }

        NRLog.d("}");
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        NRLog.d("onRepeatModeChanged");
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        NRLog.d("onShuffleModeEnabledChanged");
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        NRLog.d("onPlayerError");
        sendError(error);
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        NRLog.d("onPositionDiscontinuity");
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        NRLog.d("onPlaybackParametersChanged");
    }

    @Override
    public void onSeekProcessed() {
        NRLog.d("onSeekProcessed");
    }

    // ExoPlayer AnalyticsListener

    @Override
    public void onPlayerStateChanged(AnalyticsListener.EventTime eventTime, boolean playWhenReady, int playbackState) {
        NRLog.d("onPlayerStateChanged analytics");
    }

    @Override
    public void onTimelineChanged(AnalyticsListener.EventTime eventTime, int reason) {
        NRLog.d("onTimelineChanged analytics");
    }

    @Override
    public void onPositionDiscontinuity(AnalyticsListener.EventTime eventTime, int reason) {
        NRLog.d("onPositionDiscontinuity analytics");
    }

    @Override
    public void onSeekStarted(AnalyticsListener.EventTime eventTime) {
        NRLog.d("onSeekStarted analytics");

        if (!getState().isSeeking) {
            sendSeekStart();
        }
    }

    @Override
    public void onSeekProcessed(AnalyticsListener.EventTime eventTime) {
        NRLog.d("onSeekProcessed analytics");
    }

    @Override
    public void onPlaybackParametersChanged(AnalyticsListener.EventTime eventTime, PlaybackParameters playbackParameters) {
        NRLog.d("onPlaybackParametersChanged analytics");
    }

    @Override
    public void onRepeatModeChanged(AnalyticsListener.EventTime eventTime, int repeatMode) {
        NRLog.d("onRepeatModeChanged analytics");
    }

    @Override
    public void onShuffleModeChanged(AnalyticsListener.EventTime eventTime, boolean shuffleModeEnabled) {
        NRLog.d("onShuffleModeChanged analytics");
    }

    @Override
    public void onLoadingChanged(AnalyticsListener.EventTime eventTime, boolean isLoading) {
        NRLog.d("onLoadingChanged analytics");
    }

    @Override
    public void onPlayerError(AnalyticsListener.EventTime eventTime, ExoPlaybackException error) {
        NRLog.d("onPlayerError analytics");
    }

    @Override
    public void onTracksChanged(AnalyticsListener.EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        NRLog.d("onTracksChanged analytics");

        // Next track in the playlist
        if (player.getCurrentWindowIndex() != lastWindow) {
            NRLog.d("Next video in the playlist starts");
            lastWindow = player.getCurrentWindowIndex();
            sendRequest();
        }
    }

    @Override
    public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
        NRLog.d("onLoadError analytics");
        sendError(error);
    }

    /*
            @Override
            public void onLoadStarted(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
                NRLog.d("onLoadStarted analytics");
            }

            @Override
            public void onLoadCompleted(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
                NRLog.d("onLoadCompleted analytics");
            }

            @Override
            public void onLoadCanceled(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
                NRLog.d("onLoadCanceled analytics");
            }

            @Override
            public void onLoadError(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
                NRLog.d("onLoadError analytics");
                sendError(error);
            }

            @Override
            public void onDownstreamFormatChanged(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.MediaLoadData mediaLoadData) {

            }

            @Override
            public void onMediaPeriodReleased(AnalyticsListener.EventTime eventTime) {

            }

            @Override
            public void onReadingStarted(AnalyticsListener.EventTime eventTime) {
                NRLog.d("onReadingStarted analytics");
            }
        */
    @Override
    public void onBandwidthEstimate(AnalyticsListener.EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        NRLog.d("onBandwidthEstimate analytics");

        this.bitrateEstimate = bitrateEstimate;
    }

    @Override
    public void onMetadata(AnalyticsListener.EventTime eventTime, Metadata metadata) {

    }

    @Override
    public void onDecoderEnabled(AnalyticsListener.EventTime eventTime, int trackType, DecoderCounters decoderCounters) {

    }

    @Override
    public void onDecoderInitialized(AnalyticsListener.EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {

    }

    @Override
    public void onDecoderInputFormatChanged(AnalyticsListener.EventTime eventTime, int trackType, Format format) {

    }
/*
    @Override
    public void onUpstreamDiscarded(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.MediaLoadData mediaLoadData) {

    }

    @Override
    public void onMediaPeriodCreated(AnalyticsListener.EventTime eventTime) {

    }
*/
    @Override
    public void onDecoderDisabled(AnalyticsListener.EventTime eventTime, int trackType, DecoderCounters decoderCounters) {

    }

    @Override
    public void onAudioSessionId(AnalyticsListener.EventTime eventTime, int audioSessionId) {

    }

    @Override
    public void onAudioUnderrun(AnalyticsListener.EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {

    }

    @Override
    public void onDroppedVideoFrames(AnalyticsListener.EventTime eventTime, int droppedFrames, long elapsedMs) {
        NRLog.d("onDroppedVideoFrames analytics");
        if (!player.isPlayingAd()) {
            sendDroppedFrame(droppedFrames, (int) elapsedMs);
        }
    }

    @Override
    public void onVideoSizeChanged(AnalyticsListener.EventTime eventTime, int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
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

    @Override
    public void onRenderedFirstFrame(AnalyticsListener.EventTime eventTime, Surface surface) {
        NRLog.d("onRenderedFirstFrame analytics");
    }

    @Override
    public void onDrmKeysLoaded(AnalyticsListener.EventTime eventTime) {
        NRLog.d("onDrmKeysLoaded analytics");
    }

    @Override
    public void onDrmSessionManagerError(AnalyticsListener.EventTime eventTime, Exception error) {
        NRLog.d("onDrmSessionManagerError analytics");
    }

    @Override
    public void onDrmKeysRestored(AnalyticsListener.EventTime eventTime) {
        NRLog.d("onDrmKeysRestored analytics");
    }

    @Override
    public void onDrmKeysRemoved(AnalyticsListener.EventTime eventTime) {
        NRLog.d("onDrmKeysRemoved analytics");
    }
}
