package com.newrelic.nrvideoproject;

import android.app.Application;

import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoConfiguration;

/**
 * Initializes the NRVideo SDK at process startup rather than in MainActivity,
 * so entry points that skip MainActivity (deep links, adb am start, a future
 * notification/widget) don't crash NRVideo.addPlayer() with "NRVideo is not
 * initialized".
 */
public class NRVideoApplication extends Application {

    private NRVideoConfiguration config;

    @Override
    public void onCreate() {
        super.onCreate();
        config = new NRVideoConfiguration.Builder(BuildConfig.NR_APPLICATION_TOKEN)
                .autoDetectPlatform(getApplicationContext())
                .withHarvestCycle(30)
                .enableLogging()
                .enableQoeAggregate(BuildConfig.QOE_AGGREGATE_DEFAULT)
                .withCollectorAddress("staging-mobile-collector.newrelic.com")
                .build();
        NRVideo.newBuilder(getApplicationContext()).withConfiguration(config).build();
    }

    public NRVideoConfiguration getConfig() {
        return config;
    }
}
