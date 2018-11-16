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

        // TODO: create a ContentsTracker class, subclass of ContentsTracker
        // TODO: make ContentsTrackerCore's swigCPtr protected, to be able to access it, This is the "origin" to register callbacks
        // TODO: register callbacks to test (getTrackeName, etc)

        // TEST
        ContentsTrackerCore contentsTracker = new ContentsTrackerCore();
        contentsTracker.setup();
        contentsTracker.reset();
        contentsTracker.sendRequest();
        contentsTracker.sendStart();
        contentsTracker.sendHeartbeat();
        contentsTracker.sendEnd();
    }
}
