package com.newrelic.videoagent;

import android.net.Uri;

import com.google.android.exoplayer2.*;
import com.newrelic.videoagent.tracker.ContentsTracker;
import com.newrelic.videoagent.tracker.ExoPlayer2Tracker;

import java.util.ArrayList;
import java.util.List;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static native void initJNIEnv();

    private static ContentsTracker tracker;

    // TODO: ads tracker

    public static void startWithPlayer(SimpleExoPlayer player, Uri videoUri) {
        NRLog.d("Starting Video Agent with player");

        initJNIEnv();

        tracker = new ExoPlayer2Tracker(player);

        if (videoUri != null) {
            List<Uri> playlist = new ArrayList<>();
            playlist.add(videoUri);
            ((ExoPlayer2Tracker) tracker).setSrc(playlist);
        }

        tracker.reset();
        tracker.setup();
    }

    public static void startWithPlayer(SimpleExoPlayer player, List<Uri> playlist) {
        NRLog.d("Starting Video Agent with player");

        initJNIEnv();

        tracker = new ExoPlayer2Tracker(player);

        if (playlist != null && playlist.size() > 0) {
            ((ExoPlayer2Tracker) tracker).setSrc(playlist);
        }

        tracker.reset();
        tracker.setup();
    }

    public static ContentsTracker getTracker() {
        return tracker;
    }
}
