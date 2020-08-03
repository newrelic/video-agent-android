package com.newrelic.videoagent.trackers;

import android.net.Uri;
import android.view.Surface;

import com.google.android.exoplayer2.C;
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
import java.io.IOException;
import java.util.List;

public class BellExoTracker extends ContentsTracker implements Player.EventListener, AnalyticsListener {

    protected SimpleExoPlayer player;
    private long bitrateEstimate;
    private List<Uri> playlist;
    private int lastHeight;
    private int lastWidth;
    private long lastErrorTs = 0;

    private boolean didRequest = false;
    private boolean didStart = false;
    private boolean isPaused = false;
    private boolean isBuffering = false;
    private boolean isSeeking = false;
    private boolean isInAdBreak = false;

    public BellExoTracker(SimpleExoPlayer player) {
        super();
        this.player = player;
        NRLog.d("BellExoTracker constructor");
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

        player.removeListener(this);
        player.removeAnalyticsListener(this);
    }

    boolean goRequest() {
        if (!didRequest) {
            sendRequest();
            didRequest = true;
            return true;
        }
        else {
            return false;
        }
    }

    boolean goStart() {
        if (didRequest && !didStart) {
            sendStart();
            didStart = true;
            return true;
        }
        else {
            return false;
        }
    }

    boolean goPause() {
        if (didStart) {
            if (!isPaused) {
                sendPause();
                isPaused = true;
                return true;
            }
            else {
                return false;
            }
        }
        else {
            return false;
        }
    }

    boolean goResume() {
        if (didStart) {
            if (isPaused) {
                sendResume();
                isPaused = false;
                return true;
            }
            else {
                return false;
            }
        }
        else {
            return false;
        }
    }

    boolean goBufferStart() {
        if (!isBuffering && !isInAdBreak) {
            sendBufferStart();
            isBuffering = true;
            return true;
        }
        else {
            return false;
        }
    }

    boolean goBufferEnd() {
        if (isBuffering) {
            sendBufferEnd();
            isBuffering = false;
            return true;
        }
        else {
            return false;
        }
    }

    boolean goSeekStart() {
        if (!isSeeking) {
            sendSeekStart();
            isSeeking = true;
            return true;
        }
        else {
            return false;
        }
    }

    boolean goSeekEnd() {
        if (isSeeking) {
            sendSeekEnd();
            isSeeking = false;
            return true;
        }
        else {
            return false;
        }
    }

    void sendError(Exception error) {
        NRLog.d("ERROR RECEIVED = " + error);

        long tsDiff = System.currentTimeMillis() - lastErrorTs;
        lastErrorTs = System.currentTimeMillis();

        // Guarantee a minimum distance of 4s between errors to make sure we are not sending multiple error events for the same cause.
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

    public void setInAdBreak(boolean inAdBreak) {
        isInAdBreak = inAdBreak;
        NRLog.d("IS IN AD BREAK = " + isInAdBreak);
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
        return player;
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


    // ExoPlayer Player.EventListener

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        NRLog.d("onTimelineChanged timeline = " + timeline + " manifest = " + manifest);
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        NRLog.d("onTracksChanged groups = " + trackGroups + " selections = " + trackSelections);
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        NRLog.d("onLoadingChanged, Is Loading = " + isLoading);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

        String stateString = "?";
        switch (playbackState) {
            case Player.STATE_IDLE:
                stateString =  "Idle";
                break;
            case Player.STATE_BUFFERING:
                stateString =  "Buffering";
                break;
            case Player.STATE_READY:
                stateString =  "Ready";
                break;
            case Player.STATE_ENDED:
                stateString =  "Ended";
                break;
        }
        NRLog.d("onPlayerStateChanged, payback state (" + playbackState + ") = " + stateString + ", playWhenReady = " + playWhenReady);

        if (playbackState == Player.STATE_BUFFERING) {
            if (!player.isPlayingAd()) {
                goBufferStart();
            }
        }

        if (playbackState == Player.STATE_READY && playWhenReady == false) {
            goPause();
        }

        if (playbackState == Player.STATE_READY) {
            goBufferEnd();
            goSeekEnd();
            if (playWhenReady == true) {
                goResume();
            }
        }

        if (playbackState == Player.STATE_ENDED) {
            sendEnd();
        }
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
        NRLog.d("onPositionDiscontinuity = " + reason);
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
        NRLog.d("onPlayerStateChanged analytics payback state = " + playbackState + " , playWhenReady = " + playWhenReady);
    }

    @Override
    public void onTimelineChanged(AnalyticsListener.EventTime eventTime, int reason) {
        NRLog.d("onTimelineChanged analytics = " + reason);
    }

    @Override
    public void onPositionDiscontinuity(AnalyticsListener.EventTime eventTime, int reason) {
        NRLog.d("onPositionDiscontinuity analytics = " + reason);
    }

    @Override
    public void onSeekStarted(AnalyticsListener.EventTime eventTime) {
        NRLog.d("onSeekStarted analytics");
        goSeekStart();

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
    }

    @Override
    public void onLoadStarted(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
        NRLog.d("onLoadStarted analytics");
        NRLog.d("     loadEventInfo.uri = " + loadEventInfo.uri);
        NRLog.d("     loadEventInfo.dataSpec.key = " + loadEventInfo.dataSpec.key);
        NRLog.d("     loadEventInfo.dataSpec.absoluteStreamPosition = " + loadEventInfo.dataSpec.absoluteStreamPosition);
        NRLog.d("     SRC = " + getSrc());

        switch (mediaLoadData.dataType){
            case C.DATA_TYPE_AD:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType + " DATA_TYPE_AD");
                break;
            case C.DATA_TYPE_CUSTOM_BASE:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType + " DATA_TYPE_CUSTOM_BASE");
                break;
            case C.DATA_TYPE_DRM:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType + " DATA_TYPE_DRM");
                break;
            case C.DATA_TYPE_MANIFEST:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType + " DATA_TYPE_MANIFEST");
                break;
            case C.DATA_TYPE_MEDIA:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType + " DATA_TYPE_MEDIA");
                break;
            case C.DATA_TYPE_MEDIA_INITIALIZATION:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType + " DATA_TYPE_MEDIA_INITIALIZATION");
                break;
            case C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType + " DATA_TYPE_MEDIA_PROGRESSIVE_LIVE");
                break;
            case C.DATA_TYPE_TIME_SYNCHRONIZATION:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType + " DATA_TYPE_TIME_SYNCHRONIZATION");
                break;
            case C.DATA_TYPE_UNKNOWN:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType + " DATA_TYPE_UNKNOWN");
                break;
            default:
                NRLog.d("     mediaLoadData.dataType = " + mediaLoadData.dataType);
        }

        NRLog.d("     mediaLoadData.trackType = " + mediaLoadData.trackType);

        if (mediaLoadData.trackFormat != null) {
            NRLog.d("     mediaLoadData.trackFormat.containerMimeType = " + mediaLoadData.trackFormat.containerMimeType);
        }

        NRLog.d("     IS PLAYING AD ? " + player.isPlayingAd());

        // Use MANIFEST loading to CONTENT_REQUEST, if no manifest loaded, then use MEDIA_INITIALIZATION that happens when the actual video data is loaded
        if (mediaLoadData.dataType == C.DATA_TYPE_MANIFEST) {
            goRequest();
        }
        else if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA_INITIALIZATION) {
            goRequest();
        }
    }

    @Override
    public void onLoadCompleted(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
        NRLog.d("onLoadCompleted analytics");
        //TODO: use this to get the real contentSrc (uri) and use responseHeader to obtain CDN identity
        /*
        NRLog.d("loadEventInfo.responseHeaders = " + loadEventInfo.responseHeaders);
        NRLog.d("loadEventInfo.uri = " + loadEventInfo.uri);
        NRLog.d("IS PLAYING AD ? " + player.isPlayingAd());
        */
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
        NRLog.d("onDownstreamFormatChanged analytics");
    }

    @Override
    public void onMediaPeriodReleased(AnalyticsListener.EventTime eventTime) {
        NRLog.d("onMediaPeriodReleased analytics");
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
        NRLog.d("onMetadata analytics");
    }

    @Override
    public void onDecoderEnabled(AnalyticsListener.EventTime eventTime, int trackType, DecoderCounters decoderCounters) {
        NRLog.d("onDecoderEnabled analytics");
    }

    @Override
    public void onDecoderInitialized(AnalyticsListener.EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {
        NRLog.d("onDecoderInitialized analytics");
    }

    @Override
    public void onDecoderInputFormatChanged(AnalyticsListener.EventTime eventTime, int trackType, Format format) {
        NRLog.d("onDecoderInputFormatChanged analytics");
    }

    @Override
    public void onUpstreamDiscarded(AnalyticsListener.EventTime eventTime, MediaSourceEventListener.MediaLoadData mediaLoadData) {
        NRLog.d("onUpstreamDiscarded analytics");
    }

    @Override
    public void onMediaPeriodCreated(AnalyticsListener.EventTime eventTime) {
        NRLog.d("onMediaPeriodCreated analytics");
    }

    @Override
    public void onDecoderDisabled(AnalyticsListener.EventTime eventTime, int trackType, DecoderCounters decoderCounters) {
        NRLog.d("onDecoderDisabled analytics");
    }

    @Override
    public void onAudioSessionId(AnalyticsListener.EventTime eventTime, int audioSessionId) {
        NRLog.d("onAudioSessionId analytics");
    }

    @Override
    public void onAudioUnderrun(AnalyticsListener.EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
        NRLog.d("onAudioUnderrun analytics");
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
        NRLog.d("     adGroupIndex = " + eventTime.mediaPeriodId.adGroupIndex);
        NRLog.d("     adIndexInAdGroup = " + eventTime.mediaPeriodId.adIndexInAdGroup);
        NRLog.d("     windowSequenceNumber = " + eventTime.mediaPeriodId.windowSequenceNumber);
        NRLog.d("     windowsIndex = " + eventTime.windowIndex);
        NRLog.d("     currentPlaybackPositionMs = " + eventTime.currentPlaybackPositionMs);
        NRLog.d("     eventPlaybackPositionMs = " + eventTime.eventPlaybackPositionMs);

        if (eventTime.mediaPeriodId.adIndexInAdGroup < 0 || eventTime.mediaPeriodId.adIndexInAdGroup > 200) {
            goStart();
        }
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
