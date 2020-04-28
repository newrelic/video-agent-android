package com.newrelic.videoagent;

import android.os.Handler;

import com.newrelic.videoagent.jni.swig.TrackerCore;

public class Heartbeat {

    private TrackerCore tracker;
    private boolean isHeartbeatRunning = false;
    private int heartbeatInterval = 30000;
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (isHeartbeatRunning) {
                tracker.sendHeartbeat();
                heartbeatHandler.postDelayed(heartbeatRunnable, heartbeatInterval);
            }
        }
    };

    public Heartbeat(TrackerCore tracker) {
        this.tracker = tracker;
        heartbeatHandler = new Handler();
    }

    public void startTimer() {
        isHeartbeatRunning = true;
        heartbeatHandler.postDelayed(heartbeatRunnable, heartbeatInterval);
    }

    public void abortTimer() {
        isHeartbeatRunning = false;
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
    }
}
