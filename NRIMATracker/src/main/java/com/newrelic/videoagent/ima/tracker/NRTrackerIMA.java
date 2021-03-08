package com.newrelic.videoagent.ima.tracker;

import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;

public class NRTrackerIMA extends NRVideoTracker implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        NRLog.d("AdErrorEvent = " + adErrorEvent);
        sendError(adErrorEvent.getError());
    }

    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (adEvent.getType() != AdEvent.AdEventType.AD_PROGRESS) {
            NRLog.d("AdEvent = " + adEvent);
        }

        switch (adEvent.getType()) {
            case CONTENT_PAUSE_REQUESTED:
                sendAdBreakStart();
                break;
            case CONTENT_RESUME_REQUESTED:
                sendAdBreakEnd();
                break;
            case STARTED:
                sendRequest();
                sendStart();
                break;
            case COMPLETED:
                sendEnd();
                break;
            case CLICKED:
                sendAdClick();
                break;
            case FIRST_QUARTILE:
            case MIDPOINT:
            case THIRD_QUARTILE:
                sendAdQuartile();
                break;
        }
    }

    //TODO: getters
}
