package com.newrelic.videoagent.tracker;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.newrelic.videoagent.BuildConfig;

public class ExoPlayer2Tracker extends ContentsTracker {

    protected SimpleExoPlayer player;

    public ExoPlayer2Tracker(SimpleExoPlayer player) {
        this.player = player;
    }

    @Override
    public void setup() {
        super.setup();
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
}
