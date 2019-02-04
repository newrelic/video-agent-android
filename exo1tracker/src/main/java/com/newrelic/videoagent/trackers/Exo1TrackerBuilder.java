package com.newrelic.videoagent.trackers;

import android.net.Uri;

import com.google.android.exoplayer.ExoPlayer;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.TrackerBuilder;
import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.basetrackers.ContentsTracker;

import java.util.List;

public class Exo1TrackerBuilder extends TrackerBuilder {

    ExoPlayer1ContentsTracker contentsTracker;

    @Override
    public ContentsTracker contents() {
        return contentsTracker;
    }

    @Override
    public AdsTracker ads() {
        return null;
    }

    @Override
    public void startWithPlayer(Object player, Uri videoUri) {
        NRLog.d("Starting Video Agent with player and one video");

        initPlayer(player);
    }

    @Override
    public void startWithPlayer(Object player, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with player and a playlist");

        initPlayer(player);
    }

    @Override
    public void startWithPlayer(Object player) {
        NRLog.d("Starting Video Agent with player");

        initPlayer(player);
    }

    private void initPlayer(Object player) {
        if (!(player instanceof ExoPlayer)) {
            throw new Error("Player is not an instance of ExoPlayer");
        }
        ExoPlayer1ContentsTracker tracker = new ExoPlayer1ContentsTracker((ExoPlayer) player);
        contentsTracker = tracker;
    }
}
