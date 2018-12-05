package com.newrelic.videoagent.tracker.ExoPlayer2;

import android.net.Uri;
import android.os.Handler;
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
import com.newrelic.videoagent.EventDefs;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.jni.CAL;
import com.newrelic.videoagent.jni.swig.AttrList;
import com.newrelic.videoagent.jni.swig.CoreTrackerState;
import com.newrelic.videoagent.jni.swig.TrackerCore;
import com.newrelic.videoagent.jni.swig.ValueHolder;
import com.newrelic.videoagent.tracker.AdsTracker;

import java.io.IOException;
import java.util.List;

// BUGS:
// Seek start is sent when seeks ends, not when dragging starts. check player.getSeekParameters(),.

public class ExoPlayer2BaseTracker extends Object implements Player.EventListener, AnalyticsListener {

    protected SimpleExoPlayer player;
    protected TrackerCore trackerCore;

    private static final long timerTrackTimeMs = 250;
    private long bitrateEstimate;
    private List<Uri> playlist;
    private int lastWindow;
    private boolean firstFrameHappened;
    private Handler handler = new Handler();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            double currentTimeSecs = (double)player.getContentPosition() / 1000.0;
            double durationSecs = (double)player.getDuration() / 1000.0;

            /*
            NRLog.d("Current content position time = " + currentTimeSecs);
            NRLog.d("Duration time = " + durationSecs);
            NRLog.d("Current content position percentage = " + 100.0 * currentTimeSecs / durationSecs);
            NRLog.d("Get current seek bar postion = " + player.getCurrentPosition());
            */

            if (currentTimeSecs > 0 && firstFrameHappened == false) {
                NRLog.d("!! First Frame !!");
                firstFrameHappened = true;
                trackerCore.sendStart();
            }

            // Give it margin to ensure the video won't fin ish before we get the last time event
            double margin = 2.0 * (double)timerTrackTimeMs / 1000.0;
            if (currentTimeSecs + margin >= durationSecs) {
                if (trackerCore.state() != CoreTrackerState.CoreTrackerStateStopped) {
                    NRLog.d("!! End Of Video !!");
                    sendEnd();
                }
                return;
            }

            if (trackerCore.state() != CoreTrackerState.CoreTrackerStateStopped) {
                handler.postDelayed(this, timerTrackTimeMs );
            }
        }
    };

    public ExoPlayer2BaseTracker(SimpleExoPlayer player, TrackerCore trackerCore) {
        this.trackerCore = trackerCore;
        this.player = player;
    }

    public void setup() {
        player.addListener(this);
        player.addAnalyticsListener(this);
        trackerCore.sendPlayerReady();
    }

    public void reset() {
        bitrateEstimate = 0;
        lastWindow = 0;
        firstFrameHappened = false;
    }

    public Boolean isAd() {
        return this.trackerCore instanceof AdsTracker;
    }

    public long getBitrateEstimate() {
        return bitrateEstimate;
    }

    public List<Uri> getPlaylist() {
        return playlist;
    }

    public void setPlaylist(List<Uri> playlist) {
        this.playlist = playlist;
    }

    // "Overwritten" senders

    private void sendRequest() {
        NRLog.d("OVERWRITTEN sendRequest");
        trackerCore.sendRequest();
        handler.postDelayed(this.runnable, timerTrackTimeMs);
    }

    private void sendEnd() {
        trackerCore.sendEnd();
        firstFrameHappened = false;
    }

    private void sendError(Exception error) {
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

        trackerCore.sendError(msg);
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

            if (trackerCore.state() == CoreTrackerState.CoreTrackerStateBuffering) {
                trackerCore.sendBufferEnd();

                if (trackerCore.state() == CoreTrackerState.CoreTrackerStateSeeking) {
                    trackerCore.sendSeekEnd();
                }
            }
        }
        else if (playbackState == Player.STATE_ENDED) {
            NRLog.d("\tVideo Ended Playing");
        }
        else if (playbackState == Player.STATE_BUFFERING) {
            NRLog.d("\tVideo Is Buffering");

            if (trackerCore.state() != CoreTrackerState.CoreTrackerStateBuffering) {
                trackerCore.sendBufferStart();
            }
        }

        if (playWhenReady && playbackState == Player.STATE_READY) {
            NRLog.d("\tVideo Playing");

            if (trackerCore.state() == CoreTrackerState.CoreTrackerStateStopped) {
                sendRequest();
            }
            else if (trackerCore.state() == CoreTrackerState.CoreTrackerStatePaused) {
                trackerCore.sendResume();
            }
        }
        else if (playWhenReady) {
            NRLog.d("\tVideo Not Playing");
        }
        else {
            NRLog.d("\tVideo Paused");

            if (trackerCore.state() == CoreTrackerState.CoreTrackerStatePlaying) {
                trackerCore.sendPause();
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

        trackerCore.sendSeekStart();
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
            sendRequest();
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
        sendEnd();
        reset();
    }

    @Override
    public void onLoadError(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
        NRLog.d("onLoadError analytics");
        sendError(error);
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

        String actionName = isAd() ? EventDefs.AD_RENDITION_CHANGE : EventDefs.CONTENT_RENDITION_CHANGE;

        if (this.bitrateEstimate != 0) {
            if (bitrateEstimate > this.bitrateEstimate) {
                trackerCore.updateAttribute("shift", CAL.convertObjectToHolder("up"), actionName);
                trackerCore.sendRenditionChange();
            }
            else if (bitrateEstimate < this.bitrateEstimate) {
                trackerCore.updateAttribute("shift", CAL.convertObjectToHolder("down"), actionName);
                trackerCore.sendRenditionChange();
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
        AttrList attributes = new AttrList();
        attributes.set("lostFrames", new ValueHolder(droppedFrames));
        attributes.set("lostFramesDuration", new ValueHolder(elapsedMs));

        String actionName = isAd() ? "AD_DROPPED_FRAMES" : "CONTENT_DROPPED_FRAMES";

        trackerCore.sendCustomAction(actionName, attributes);
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

