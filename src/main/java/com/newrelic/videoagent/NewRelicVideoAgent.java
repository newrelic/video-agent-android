package com.newrelic.videoagent;

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
        NRLog.d("Starting Video Agent with player");

        initJNIEnv();

        tracker = new ExoPlayer2Tracker(player);
        tracker.setup();
        tracker.reset();

        // TEST
        tracker.sendRequest();
        tracker.sendStart();
        tracker.sendHeartbeat();
        tracker.sendEnd();
    }
}
