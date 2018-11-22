package com.newrelic.videoagent.tracker;

import android.net.NetworkInfo;
import android.support.annotation.Nullable;
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
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.jni.swig.CoreTrackerState;

import java.io.IOException;

public class ExoPlayer2Tracker extends ContentsTracker implements Player.EventListener, AnalyticsListener {

    protected SimpleExoPlayer player;

    public ExoPlayer2Tracker(SimpleExoPlayer player) {
        this.player = player;
        sendPlayerReady();
    }

    @Override
    public void setup() {
        super.setup();
        player.addListener(this);
        player.addAnalyticsListener(this);
    }

    @Override
    public void reset() {
        super.reset();
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
        NRLog.d("Video format bitrate = " + player.getVideoFormat().bitrate);
        // TODO: we need a bandwidth meter:
        // https://android.jlelse.eu/android-exoplayer-starters-guide-6350433f256c
        // https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/upstream/BandwidthMeter.html
        // https://github.com/google/ExoPlayer/issues/1570
        return new Long(0);
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

    public Object getSrc() {
        // TODO: can't find a way to ontain it from player. Found post saying we have to store it ourselves:
        // https://github.com/google/ExoPlayer/issues/2328
        // https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/upstream/ContentDataSource.html
        return "";
    }

    public Object getPlayrate() {
        return new Double(player.getPlaybackParameters().speed);
    }

    public Object getFps() {
        // TODO
        NRLog.d("Video format frameRate = " + player.getVideoFormat().frameRate);
        //player.getVideoDecoderCounters().
        return new Long(0);
    }

    public Object getIsAutoplayed() {
        return new Long(player.getPlayWhenReady() ? 1L : 0L);
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

    // TODO: track events:
    // actual seek start (from dragging start)
    // actual video start (first frame)
    // rendition change

    // TODO: test with playlists

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        NRLog.d("onTimelineChanged");
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        NRLog.d("onTracksChanged");
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
                // TODO: after request, check for actual first frame event (how? track playback time change?)
                sendStart();
            }
            else if (state() == CoreTrackerState.CoreTrackerStatePaused) {
                sendResume();
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
    }

    @Override
    public void onViewportSizeChange(EventTime eventTime, int width, int height) {

    }

    @Override
    public void onNetworkTypeChanged(EventTime eventTime, @Nullable NetworkInfo networkInfo) {

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
