package com.newrelic.videoagent.trackers;

import android.net.Uri;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.TrackerBuilder;
import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.basetrackers.ContentsTracker;
import java.util.List;

public class Exo2TrackerBuilder extends TrackerBuilder {

    ContentsTracker contentsTracker;

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
        if (player instanceof SimpleExoPlayer) {
            ExoPlayer2ContentsTracker tracker = new ExoPlayer2ContentsTracker((SimpleExoPlayer) player);
            contentsTracker = tracker;
        }
        else if (player instanceof CastPlayer) {
            CastPlayerContentsTracker tracker = new CastPlayerContentsTracker((CastPlayer) player);
            contentsTracker = tracker;
        }
        else {
            throw new Error("Player is not a instance of SimpleExoPlayer or CastPlayer");
        }
    }
}
