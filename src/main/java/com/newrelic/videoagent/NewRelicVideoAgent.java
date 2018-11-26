package com.newrelic.videoagent;

import android.net.Uri;

import com.google.android.exoplayer2.*;
import com.newrelic.videoagent.tracker.ContentsTracker;
import com.newrelic.videoagent.tracker.ExoPlayer2Tracker;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static native void initJNIEnv();

    private static ContentsTracker tracker;

    // TODO: ads tracker

    public static void startWithPlayer(SimpleExoPlayer player) {
        startWithPlayer(player, null);
    }

    public static void startWithPlayer(SimpleExoPlayer player, Uri videoUri) {
        NRLog.d("Starting Video Agent with player");

        initJNIEnv();

        tracker = new ExoPlayer2Tracker(player);

        if (videoUri != null) {
            ((ExoPlayer2Tracker) tracker).setSrc(videoUri);
        }

        tracker.reset();
        tracker.setup();
    }

    public static ContentsTracker getTracker() {
        return tracker;
    }
}
