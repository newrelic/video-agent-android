package com.newrelic.videoagent.tracker.ExoPlayer2;

import android.net.Uri;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.newrelic.videoagent.tracker.ContentsTracker;

import java.util.List;

public class ExoPlayer2ContentsTracker extends ContentsTracker {

    private ExoPlayer2BaseTracker baseTracker;

    public ExoPlayer2ContentsTracker(SimpleExoPlayer player) {
        baseTracker = new ExoPlayer2BaseTracker(player, this);
    }

    @Override
    public void setup() {
        super.setup();
        baseTracker.setup();
    }

    @Override
    public void reset() {
        super.reset();
        baseTracker.reset();
    }

    public void setSrc(List<Uri> uris) {
        baseTracker.setSrc(uris);
    }
}
