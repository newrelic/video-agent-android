package com.newrelic.videoagent;

import android.os.Handler;

import com.newrelic.videoagent.jni.swig.TrackerCore;

public class Heartbeat {

    private TrackerCore tracker;
    private boolean isHeartbeatRunning = false;
    private boolean hbEnabled = true;
    private int heartbeatInterval = 30000;
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;

    public Heartbeat(TrackerCore tracker) {
        this.tracker = tracker;
        heartbeatHandler = new Handler();
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHeartbeatRunning) {
                    tracker.sendHeartbeat();
                    heartbeatHandler.postDelayed(heartbeatRunnable, heartbeatInterval);
                }
            }
        };
    }

    public void startTimer() {
        if (hbEnabled) {
            if (!isHeartbeatRunning) {
                isHeartbeatRunning = true;
                heartbeatHandler.postDelayed(heartbeatRunnable, heartbeatInterval);
            }
        }
    }

    public void abortTimer() {
        isHeartbeatRunning = false;
        heartbeatHandler.removeCallbacks(heartbeatRunnable, null);
    }

    public void setHeartbeatInterval(int interval) {
        heartbeatInterval = interval;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void enableHeartbeat() {
        hbEnabled = true;
        startTimer();
    }

    public void disableHeartbeat() {
        hbEnabled = false;
        abortTimer();
    }
}
