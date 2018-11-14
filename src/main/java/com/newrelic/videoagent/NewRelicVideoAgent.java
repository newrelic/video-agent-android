package com.newrelic.videoagent;

import com.google.android.exoplayer2.*;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static void startWithPlayer(SimpleExoPlayer player) {
        NRLog.d("Starting Video Agent with player");
    }
}
