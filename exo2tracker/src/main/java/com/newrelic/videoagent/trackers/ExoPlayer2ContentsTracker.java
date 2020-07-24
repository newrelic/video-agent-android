package com.newrelic.videoagent.trackers;

import android.net.Uri;
import android.os.Handler;
import android.view.Surface;

import com.google.android.exoplayer2.BasePlayer;
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
import com.newrelic.videoagent.basetrackers.ContentsTracker;
import com.newrelic.videoagent.jni.CAL;
import com.newrelic.videoagent.jni.swig.CoreTrackerState;

import java.io.IOException;
import java.util.List;

public class ExoPlayer2ContentsTracker extends ContentsTracker implements Player.EventListener, AnalyticsListener {

    protected BasePlayer player;

    private static final long timerTrackTimeMs = 250;
    private long lastErrorTs = 0;
    private long bitrateEstimate;
    private int lastHeight;
    private int lastWidth;
    private boolean isSeeking = false;
    private boolean isBuffering = false;
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
                sendStart();
            }

            // Give it margin to ensure the video won't fin ish before we get the last time event
            double margin = 2.0 * (double)timerTrackTimeMs / 1000.0;
            if (currentTimeSecs + margin >= durationSecs) {
                if (state() != CoreTrackerState.CoreTrackerStateStopped) {
                    NRLog.d("!! End Of Video !!");
                    //sendEnd();
                }
                return;
            }

            if (state() != CoreTrackerState.CoreTrackerStateStopped) {
                handler.postDelayed(this, timerTrackTimeMs );
            }
        }
    };

    public ExoPlayer2ContentsTracker(SimpleExoPlayer player) {
        super();
        this.player = player;
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
        return new Long(getBitrateEstimate());
    }

    public Object getRenditionBitrate() {
        return getBitrate();
    }

    public Object getRenditionWidth() {
        return new Long((long)getPlayer().getVideoFormat().width);
    }

    public Object getRenditionHeight() {
        return new Long((long)getPlayer().getVideoFormat().height);
    }

    public Object getDuration() {
        return new Long(player.getDuration());
    }

    public Object getPlayhead() {
        return new Long(player.getContentPosition());
    }

    public Object getSrc() {
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

    public Object getPlayrate() {
        return new Double(player.getPlaybackParameters().speed);
    }

    public Object getFps() {
        if (getPlayer().getVideoFormat() != null) {
            if (getPlayer().getVideoFormat().frameRate > 0) {
                return new Double(getPlayer().getVideoFormat().frameRate);
            }
        }

        return null;
    }

    public Object getIsMuted() {
        if (getPlayer().getVolume() == 0) {
            return new Long(1);
        }
        else {
            return new Long(0);
        }
    }

    private SimpleExoPlayer getPlayer() {
        return (SimpleExoPlayer)player;
    }

    @Override
    public void setSrc(List<Uri> uris) {
        setPlaylist(uris);
    }


// Old basetracker code, now integrated here


    public void setup() {
        super.setup();

        player.addListener(this);

        if (player instanceof SimpleExoPlayer) {
            ((SimpleExoPlayer)player).addAnalyticsListener(this);
        }

        sendPlayerReady();
    }

    public void reset() {
        super.reset();

        bitrateEstimate = 0;
        lastWindow = 0;
        firstFrameHappened = false;
        lastWidth = 0;
        lastHeight = 0;
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

    @Override
    public void sendRequest() {
        NRLog.d("OVERWRITTEN sendRequest");
        super.sendRequest();
        handler.postDelayed(this.runnable, timerTrackTimeMs);
    }

    @Override
    public void sendEnd() {
        super.sendEnd();
        firstFrameHappened = false;
    }

    void sendError(Exception error) {
        NRLog.d("ERROR RECEIVED = " + error);

        long tsDiff = System.currentTimeMillis() - lastErrorTs;
        lastErrorTs = System.currentTimeMillis();

        // Guarantee a minimum distance of 4s between errors to make sure we are not sending mmultiple error events for the same cause.
        if (tsDiff < 4000) {
            NRLog.d("ERROR TOO CLOSE, DO NOT SEND");
            return;
        }

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

        super.sendError(msg);
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

            if (isBuffering) {
                sendBufferEnd();
                isBuffering = false;
            }

            if (isSeeking) {
                sendSeekEnd();
                isSeeking = false;
            }
        }
        else if (playbackState == Player.STATE_ENDED) {
            NRLog.d("\tVideo Ended Playing");
            if (state() != CoreTrackerState.CoreTrackerStateStopped) {
                if (isBuffering) {
                    sendBufferEnd();
                    isBuffering = false;
                }
                if (isSeeking) {
                    sendSeekEnd();
                    isSeeking = false;
                }
                sendEnd();
            }
        }
        else if (playbackState == Player.STATE_BUFFERING) {
            NRLog.d("\tVideo Is Buffering");

            if (!isBuffering) {
                sendBufferStart();
                isBuffering = true;
            }
        }

        if (playWhenReady && playbackState == Player.STATE_READY) {
            NRLog.d("\tVideo Playing");

            if (state() == CoreTrackerState.CoreTrackerStateStopped) {
                sendRequest();
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

        if (!isSeeking) {
            sendSeekStart();
            isSeeking = true;
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
    public void onLoadStarted(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
        NRLog.d("onLoadStarted analytics");
    }

    @Override
    public void onLoadCompleted(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
        NRLog.d("onLoadCompleted analytics");
        //TODO: use this to get the real contentSrc (uri) and use responseHeader to obtain CDN identity
        NRLog.d("loadEventInfo.responseHeaders = " + loadEventInfo.responseHeaders);
        NRLog.d("loadEventInfo.uri = " + loadEventInfo.uri);
        NRLog.d("IS PLAYING AD ? " + player.isPlayingAd());
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

    @Override
    public void onUpstreamDiscarded(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.MediaLoadData mediaLoadData) {

    }

    @Override
    public void onMediaPeriodCreated(AnalyticsListener.EventTime eventTime) {

    }

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
        sendDroppedFrame(droppedFrames, (int)elapsedMs);
    }

    @Override
    public void onVideoSizeChanged(AnalyticsListener.EventTime eventTime, int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        NRLog.d("onVideoSizeChanged analytics, H = " + height + " W = " + width);

        //String actionName = isAd() ? EventDefs.AD_RENDITION_CHANGE : EventDefs.CONTENT_RENDITION_CHANGE;
        String actionName = EventDefs.CONTENT_RENDITION_CHANGE;

        long currMul = width * height;
        long lastMul = lastWidth * lastHeight;

        if (lastMul != 0) {
            if (lastMul < currMul) {
                updateAttribute("shift", CAL.convertObjectToHolder("up"), actionName);
                sendRenditionChange();
            }
            else if (lastMul > currMul) {
                updateAttribute("shift", CAL.convertObjectToHolder("down"), actionName);
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
