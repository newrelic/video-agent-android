package com.newrelic.videoagent.trackers;

import android.net.Uri;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.TrackerBuilder;
import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.basetrackers.ContentsTracker;
import java.util.List;

public class Exo2TrackerBuilder extends TrackerBuilder {

    ExoPlayer2ContentsTracker contentsTracker;

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

        initExo2Player(player);
    }

    @Override
    public void startWithPlayer(Object player, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with player and a playlist");

        initExo2Player(player);
    }

    private void initExo2Player(Object player) {
        if (!(player instanceof SimpleExoPlayer)) {
            throw new Error("Player is not a instance of SimpleExoPlayer");
        }
        ExoPlayer2ContentsTracker tracker = new ExoPlayer2ContentsTracker((SimpleExoPlayer) player);
        contentsTracker = tracker;
    }
}
