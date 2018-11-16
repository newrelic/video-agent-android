package com.newrelic.videoagent;

import com.google.android.exoplayer2.*;
import com.newrelic.videoagent.tracker.ContentsTracker;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static native void initJNIEnv();

    public static void startWithPlayer(SimpleExoPlayer player) {
        NRLog.d("Starting Video Agent with player");

        initJNIEnv();

        // TEST
        ContentsTracker contentsTracker = new ContentsTracker();
        contentsTracker.setup();
        contentsTracker.reset();
        contentsTracker.sendRequest();
        contentsTracker.sendStart();
        contentsTracker.sendHeartbeat();
        contentsTracker.sendEnd();
    }
}
