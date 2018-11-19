package com.newrelic.videoagent.tracker;

import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.jni.CAL;
import com.newrelic.videoagent.jni.swig.AdsTrackerCore;

public class AdsTracker extends AdsTrackerCore {

    public AdsTracker() {
        super();

        // TODO: register callbacks
    }

    protected void registerGetter(String name, String methodName) {
        long ptr = getCPtr(this);
        try {
            CAL.registerGetter(name, this, this.getClass().getMethod(methodName), new Long(ptr));
        }
        catch (Exception e) {
            NRLog.e("Error registering a getter in AdsTracker = " + e);
        }
    }
}
