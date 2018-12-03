package com.newrelic.videoagent.tracker;

import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.jni.CAL;
import com.newrelic.videoagent.jni.swig.AdsTrackerCore;

public class AdsTracker extends AdsTrackerCore {

    public AdsTracker(ContentsTracker contentsTracker) {
        super(contentsTracker);
        setupGetters();
    }

    public AdsTracker() {
        super();
        setupGetters();
    }

    private void setupGetters() {

        registerGetter("numberOfAds", "getNumberOfAdsAttr");
        registerGetter("trackerName", "getTrackerName");
        registerGetter("trackerVersion", "getTrackerVersion");
        registerGetter("playerVersion", "getPlayerVersion");
        registerGetter("playerName", "getPlayerName");
        registerGetter("isAd", "getIsAd");

        registerGetter("adId", "getVideoId");
        registerGetter("adTitle", "getTitle");
        registerGetter("adBitrate", "getBitrate");
        registerGetter("adRenditionName", "getRenditionName");
        registerGetter("adRenditionBitrate", "getRenditionBitrate");
        registerGetter("adRenditionWidth", "getRenditionWidth");
        registerGetter("adRenditionHeight", "getRenditionHeight");
        registerGetter("adDuration", "getDuration");
        registerGetter("adPlayhead", "getPlayhead");
        registerGetter("adLanguage", "getLanguage");
        registerGetter("adSrc", "getSrc");
        registerGetter("adIsMuted", "getIsMuted");
        registerGetter("adCdn", "getCdn");
        registerGetter("adFps", "getFps");
        registerGetter("adCreativeId", "getAdCreativeId");
        registerGetter("adPosition", "getAdPosition");
        registerGetter("adPartner", "getAdPartner");
    }

    public Object getNumberOfAdsAttr() {
        return new Long((long)getNumberOfAds());
    }

    public Object getIsAd() {
        throw new RuntimeException("getIsAd must be overwritten by subclass");
    }

    public Object getPlayerName() {
        throw new RuntimeException("getPlayerName must be overwritten by subclass");
    }

    public Object getPlayerVersion() {
        throw new RuntimeException("getPlayerVersion must be overwritten by subclass");
    }

    public Object getTrackerName() {
        throw new RuntimeException("getTrackerName must be overwritten by subclass");
    }

    public Object getTrackerVersion() {
        throw new RuntimeException("getTrackerVersion must be overwritten by subclass");
    }

    protected void registerGetter(String name, String methodName) {
        long ptr = getCPtr(this);
        try {
            CAL.registerGetter(name, this, this.getClass().getMethod(methodName), new Long(ptr));
        }
        catch (Exception e) {
            NRLog.e("Error registering a getter in " + this.getClass().getSimpleName() + " = " + e);
        }
    }
}
