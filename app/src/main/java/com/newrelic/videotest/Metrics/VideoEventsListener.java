package com.newrelic.videotest.Metrics;

import android.os.Handler;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.newrelic.videotest.Logs.AVLog;

import java.util.HashMap;
import java.util.Map;

public class VideoEventsListener implements Player.EventListener {

    private Player player;
    private double videoDurationSeconds;
    private long lastPlayerPosition = 0;
    private Handler handler = new Handler();
    private Boolean videoIsPlaying = false;

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            double currentTimeSecs = (double) player.getContentPosition() / 1000.0;
            AVLog.out("Current position time = " + currentTimeSecs);
            AVLog.out("Current position percentage = " + 100.0 * currentTimeSecs / videoDurationSeconds);
            lastPlayerPosition = player.getContentPosition();

            if (videoIsPlaying) {
                handler.postDelayed(this, 500);
            }
        }
    };

    public VideoEventsListener(final Player player) {
        super();
        this.player = player;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        AVLog.out("onTimelineChanged");
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        AVLog.out("onTracksChanged");
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        AVLog.out("onLoadingChanged, Is Loading = " + isLoading);

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
        AVLog.out("onPlayerStateChanged, payback state = " + playbackState);

        if (playbackState == Player.STATE_READY) {
            this.videoDurationSeconds = (double) player.getDuration() / 1000.0;
            AVLog.out("Saved Video Duration = " + this.videoDurationSeconds);
        }
        else if (playbackState == Player.STATE_ENDED) {
            AVLog.out("Video Ended Playing");
        }
        else if (playbackState == Player.STATE_BUFFERING) {
            AVLog.out("Video Is Buffering");
        }

        if (playWhenReady && playbackState == Player.STATE_READY) {
            AVLog.out("Video Playing");
            this.videoIsPlaying = true;
            //handler.postDelayed(this.runnable, 500);
        }
        else if (playWhenReady) {
            AVLog.out("Video Not Playing");
            this.videoIsPlaying = false;
        }
        else {
            AVLog.out("Video Paused");
            this.videoIsPlaying = false;
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        AVLog.out("onRepeatModeChanged");
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        AVLog.out("onShuffleModeEnabledChanged");
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        AVLog.out("onPlayerError");
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        AVLog.out("onPositionDiscontinuity");
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        AVLog.out("onPlaybackParametersChanged");
    }

    @Override
    public void onSeekProcessed() {
        AVLog.out("onSeekProcessed");
    }
}
