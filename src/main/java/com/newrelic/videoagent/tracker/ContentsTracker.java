package com.newrelic.videoagent.tracker;

import com.newrelic.videoagent.CAL;
import com.newrelic.videoagent.swig.ContentsTrackerCore;

public class ContentsTracker extends ContentsTrackerCore {

    // TODO: register callbacks (getTrackeName, getIsAd, etc)

    public ContentsTracker() {
        super();

        long ptr = getCPtr(this);
        try {
            CAL.registerGetter("trackerName", this, this.getClass().getMethod("getTrackerName"), new Long(ptr));
            CAL.registerGetter("isAd", this, this.getClass().getMethod("getIsAd"), new Long(ptr));
        }
        catch (Exception e) { }
    }

    public Object getTrackerName() {
        return "MyContentsTracker2";
    }

    public Object getIsAd() {
        return new Long(0);
    }
}
