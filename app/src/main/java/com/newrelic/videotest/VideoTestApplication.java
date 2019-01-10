package com.newrelic.videotest;

import android.app.Application;

import com.newrelic.agent.android.NewRelic;

public class VideoTestApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NewRelic.withApplicationToken("AAd70f1744b7d2515913925c397ec9500f60e62b7b").start(this);
    }
}
