package com.newrelic.videoagent.tracker;

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
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.newrelic.videoagent.BuildConfig;
import com.newrelic.videoagent.EventDefs;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.jni.swig.CoreTrackerState;
import java.io.IOException;
import java.util.List;

// BUGS:
// Seek start is sent when seeks ends, not when dragging starts. check player.getSeekParameters(),.
// The start is not send on first frame, but on request. First frame event happens when video is loaded not played.
// onTracksChanged the END is sent for the next video, not the finished video. Only with playlists.

public class ExoPlayer2Tracker extends ContentsTracker implements Player.EventListener, AnalyticsListener {

    protected SimpleExoPlayer player;
    private long bitrateEstimate;
    private List<Uri> playlist;
    private int lastWindow;

    public ExoPlayer2Tracker(SimpleExoPlayer player) {
        this.player = player;
    }

    @Override
    public void setup() {
        super.setup();
        player.addListener(this);
        player.addAnalyticsListener(this);
        sendPlayerReady();
    }

    @Override
    public void reset() {
        super.reset();
        bitrateEstimate = 0;
        lastWindow = 0;
    }

    public Object getIsAd() {
        return new Long(0);
    }

    public Object getPlayerName() {
        return "ExoPlayer2";
    }

    public Object getPlayerVersion() {
        return "2.x";
    }

    public Object getTrackerName() {
        return "ExoPlayer2Tracker";
    }

    public Object getTrackerVersion() {
        return BuildConfig.VERSION_NAME;
    }

    public Object getBitrate() {
        return new Long(bitrateEstimate);
    }

    public Object getRenditionBitrate() {
        return getBitrate();
    }

    public Object getRenditionWidth() {
        return new Long((long)player.getVideoFormat().width);
    }

    public Object getRenditionHeight() {
        return new Long((long)player.getVideoFormat().height);
    }

    public Object getDuration() {
        return new Long(player.getDuration());
    }

    public Object getPlayhead() {
        return new Long(player.getContentPosition());
    }

    // to implement getSrc we need to access the mediasource somehow:
    // https://github.com/google/ExoPlayer/issues/2799
    // https://github.com/google/ExoPlayer/issues/3943
    // https://github.com/google/ExoPlayer/issues/2639
    // https://stackoverflow.com/questions/40284772/exoplayer-2-playlist-listener
    // https://github.com/google/ExoPlayer/issues/2328
    // https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/upstream/ContentDataSource.html

    public Object getSrc() {
        if (playlist != null) {
            NRLog.d("Current window index = " + player.getCurrentWindowIndex());
            try {
                Uri src = playlist.get(player.getCurrentWindowIndex());
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

    public void setSrc(List<Uri> uris) {
        playlist = uris;
    }

    public Object getPlayrate() {
        return new Double(player.getPlaybackParameters().speed);
    }

    public Object getFps() {
        if (player.getVideoFormat() != null) {
            if (player.getVideoFormat().frameRate > 0) {
                return new Double(player.getVideoFormat().frameRate);
            }
        }

        return null;
    }

    public Object getIsMuted() {
        if (player.getVolume() == 0) {
            return new Long(1);
        }
        else {
            return new Long(0);
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

            if (state() == CoreTrackerState.CoreTrackerStateBuffering) {
                sendBufferEnd();

                if (state() == CoreTrackerState.CoreTrackerStateSeeking) {
                    sendSeekEnd();
                }
            }
        }
        else if (playbackState == Player.STATE_ENDED) {
            NRLog.d("\tVideo Ended Playing");

            sendEnd();
        }
        else if (playbackState == Player.STATE_BUFFERING) {
            NRLog.d("\tVideo Is Buffering");

            sendBufferStart();
        }

        if (playWhenReady && playbackState == Player.STATE_READY) {
            NRLog.d("\tVideo Playing");

            if (state() == CoreTrackerState.CoreTrackerStateStopped) {
                sendRequest();
                sendStart();
            }
            else if (state() == CoreTrackerState.CoreTrackerStatePaused) {
                sendResume();
            }
        }
        else if (playWhenReady) {
            NRLog.d("\tVideo Not Playing");
        }
        else {
            NRLog.d("\tVideo Paused");

            if (state() == CoreTrackerState.CoreTrackerStatePlaying) {
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

        String msg;
        if (error != null) {
            if (error.getMessage() != null) {
                msg = error.getMessage();
            }
            else {
                msg = error.toString();
            }
        }
        else {
            msg = "<Unknown error>";
        }

        sendError(msg);
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
    public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {
        NRLog.d("onPlayerStateChanged analytics");
    }

    @Override
    public void onTimelineChanged(EventTime eventTime, int reason) {
        NRLog.d("onTimelineChanged analytics");
    }

    @Override
    public void onPositionDiscontinuity(EventTime eventTime, int reason) {
        NRLog.d("onPositionDiscontinuity analytics");
    }

    @Override
    public void onSeekStarted(EventTime eventTime) {
        NRLog.d("onSeekStarted analytics");

        sendSeekStart();
    }

    @Override
    public void onSeekProcessed(EventTime eventTime) {
        NRLog.d("onSeekProcessed analytics");
    }

    @Override
    public void onPlaybackParametersChanged(EventTime eventTime, PlaybackParameters playbackParameters) {
        NRLog.d("onPlaybackParametersChanged analytics");
    }

    @Override
    public void onRepeatModeChanged(EventTime eventTime, int repeatMode) {
        NRLog.d("onRepeatModeChanged analytics");
    }

    @Override
    public void onShuffleModeChanged(EventTime eventTime, boolean shuffleModeEnabled) {
        NRLog.d("onShuffleModeChanged analytics");
    }

    @Override
    public void onLoadingChanged(EventTime eventTime, boolean isLoading) {
        NRLog.d("onLoadingChanged analytics");
    }

    @Override
    public void onPlayerError(EventTime eventTime, ExoPlaybackException error) {
        NRLog.d("onPlayerError analytics");
    }

    @Override
    public void onTracksChanged(EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        NRLog.d("onTracksChanged analytics");

        // Next track in the playlist
        if (player.getCurrentWindowIndex() != lastWindow) {
            NRLog.d("Next video in the playlist starts");
            lastWindow = player.getCurrentWindowIndex();

            sendEnd();

            sendRequest();
            sendStart();
        }
    }

    @Override
    public void onLoadStarted(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
        NRLog.d("onLoadStarted analytics");
    }

    @Override
    public void onLoadCompleted(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
        NRLog.d("onLoadCompleted analytics");
    }

    @Override
    public void onLoadCanceled(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
        NRLog.d("onLoadCanceled analytics");
    }

    @Override
    public void onLoadError(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
        NRLog.d("onLoadError analytics");
    }

    @Override
    public void onDownstreamFormatChanged(EventTime eventTime, MediaSourceEventListener.MediaLoadData mediaLoadData) {

    }

    @Override
    public void onMediaPeriodReleased(EventTime eventTime) {

    }

    @Override
    public void onReadingStarted(EventTime eventTime) {
        NRLog.d("onReadingStarted analytics");
    }

    @Override
    public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        NRLog.d("onBandwidthEstimate analytics");

        if (this.bitrateEstimate != 0) {
            if (bitrateEstimate > this.bitrateEstimate) {
                setOptionKey("shift", "up", EventDefs.CONTENT_RENDITION_CHANGE);
                sendRenditionChange();
            }
            else if (bitrateEstimate < this.bitrateEstimate) {
                setOptionKey("shift", "down", EventDefs.CONTENT_RENDITION_CHANGE);
                sendRenditionChange();
            }
        }

        this.bitrateEstimate = bitrateEstimate;
    }

    @Override
    public void onMetadata(EventTime eventTime, Metadata metadata) {

    }

    @Override
    public void onDecoderEnabled(EventTime eventTime, int trackType, DecoderCounters decoderCounters) {

    }

    @Override
    public void onDecoderInitialized(EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {

    }

    @Override
    public void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {

    }

    @Override
    public void onUpstreamDiscarded(EventTime eventTime, MediaSourceEventListener.MediaLoadData mediaLoadData) {

    }

    @Override
    public void onMediaPeriodCreated(EventTime eventTime) {

    }

    @Override
    public void onDecoderDisabled(EventTime eventTime, int trackType, DecoderCounters decoderCounters) {

    }

    @Override
    public void onAudioSessionId(EventTime eventTime, int audioSessionId) {

    }

    @Override
    public void onAudioUnderrun(EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {

    }

    @Override
    public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
        NRLog.d("onDroppedVideoFrames analytics");
        // TODO: interesting event to register
    }

    @Override
    public void onVideoSizeChanged(EventTime eventTime, int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        NRLog.d("onVideoSizeChanged analytics");
    }

    @Override
    public void onRenderedFirstFrame(EventTime eventTime, Surface surface) {
        NRLog.d("onRenderedFirstFrame analytics");
    }

    @Override
    public void onDrmKeysLoaded(EventTime eventTime) {

    }

    @Override
    public void onDrmSessionManagerError(EventTime eventTime, Exception error) {

    }

    @Override
    public void onDrmKeysRestored(EventTime eventTime) {

    }

    @Override
    public void onDrmKeysRemoved(EventTime eventTime) {

    }
}
