package com.newrelic.videoagent;

import com.google.android.exoplayer2.*;
import com.newrelic.videoagent.utils.NRLog;

public class NewRelicVideoAgent {

    public static void startWithPlayer(SimpleExoPlayer player) {
        NRLog.d("Starting Video Agent with player");
    }
}
