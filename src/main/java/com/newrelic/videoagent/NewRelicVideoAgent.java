package com.newrelic.videoagent;

import com.google.android.exoplayer2.*;
import com.newrelic.videoagent.utils.NRLog;

public class NewRelicVideoAgent {

    static {
        System.loadLibrary("Core");
    }

    public static void startWithPlayer(SimpleExoPlayer player) {
        NRLog.d("Starting Video Agent with player");

        StupidTest stupidTest = new StupidTest();
        NRLog.d(stupidTest.returnHello("Andreu"));
    }
}
