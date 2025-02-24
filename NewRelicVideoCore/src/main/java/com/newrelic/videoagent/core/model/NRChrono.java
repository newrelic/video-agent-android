package com.newrelic.videoagent.core.model;

public class NRChrono {

    private long startTime;

    // Constructor
    public NRChrono() {
        startTime = 0;
    }

    // Start the timer
    public void start() {
        startTime = System.currentTimeMillis();
    }

    // Get the delta time in milliseconds
    public long getDeltaTime() {
        if (startTime > 0) {
            return System.currentTimeMillis() - startTime;
        } else {
            return 0;
        }
    }
}