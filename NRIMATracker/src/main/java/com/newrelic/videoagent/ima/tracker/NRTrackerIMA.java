package com.newrelic.videoagent.ima.tracker;

import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;

public class NRTrackerIMA extends NRVideoTracker implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        NRLog.d("AdErrorEvent = " + adErrorEvent);
    }

    @Override
    public void onAdEvent(AdEvent adEvent) {
        NRLog.d("AdEvent = " + adEvent);
    }

    @Override
    public void unregisterListeners() {
        super.unregisterListeners();
    }
}
