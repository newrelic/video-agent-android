package com.newrelic.videoagent.tracker;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.newrelic.videoagent.BuildConfig;
import com.newrelic.videoagent.NRLog;

public class ExoPlayer2Tracker extends ContentsTracker implements Player.EventListener {

    protected SimpleExoPlayer player;

    private double videoDurationSeconds;
    private Boolean videoIsPlaying = false;

    public ExoPlayer2Tracker(SimpleExoPlayer player) {
        this.player = player;
    }

    @Override
    public void setup() {
        super.setup();
        player.addListener(this);
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

    // ExoPlayer EventListener

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

        /*
        BackendActions ba = new BackendActions();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("state", isLoading);
        attributes.put("hi", "x");
        attributes.put("ho", "y");
        //ba.setGeneralOptions(attributes);

        Map<String, Map<String, Object>> actionAttributes = new HashMap<>();
        actionAttributes.put("_CLICK", attributes);
        ba.setActionOptions(actionAttributes);

        ba.sendRequest();
        ba.sendAdClick();
        */

        //ba.sendAction("ON_LOADING_CHANGED", attributes);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        NRLog.d("onPlayerStateChanged, payback state = " + playbackState);

        if (playbackState == Player.STATE_READY) {
            this.videoDurationSeconds = (double) player.getDuration() / 1000.0;
            NRLog.d("Saved Video Duration = " + this.videoDurationSeconds);
        }
        else if (playbackState == Player.STATE_ENDED) {
            NRLog.d("Video Ended Playing");
        }
        else if (playbackState == Player.STATE_BUFFERING) {
            NRLog.d("Video Is Buffering");
        }

        if (playWhenReady && playbackState == Player.STATE_READY) {
            NRLog.d("Video Playing");
            this.videoIsPlaying = true;
        }
        else if (playWhenReady) {
            NRLog.d("Video Not Playing");
            this.videoIsPlaying = false;
        }
        else {
            NRLog.d("Video Paused");
            this.videoIsPlaying = false;
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
}
