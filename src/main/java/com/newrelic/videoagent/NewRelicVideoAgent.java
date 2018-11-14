package com.newrelic.videoagent;

import com.google.android.exoplayer2.*;
import com.newrelic.videoagent.swig.ContentsTrackerCore;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static native void initJNIEnv();

    public static void startWithPlayer(SimpleExoPlayer player) {
        NRLog.d("Starting Video Agent with player");

        initJNIEnv();

        ContentsTrackerCore contentsTracker = new ContentsTrackerCore();
        contentsTracker.setup();
        contentsTracker.reset();
        contentsTracker.sendHeartbeat();
    }
}
