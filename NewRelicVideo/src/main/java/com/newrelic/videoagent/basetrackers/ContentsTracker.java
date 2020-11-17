package com.newrelic.videoagent.basetrackers;

import android.net.Uri;

import com.newrelic.videoagent.Heartbeat;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.jni.CAL;
import com.newrelic.videoagent.jni.swig.ContentsTrackerCore;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ContentsTracker extends ContentsTrackerCore {

    protected Heartbeat heartbeat;

    public ContentsTracker() {
        super();

        registerGetter("trackerName", "getTrackerName");
        registerGetter("trackerVersion", "getTrackerVersion");
        registerGetter("playerVersion", "getPlayerVersion");
        registerGetter("playerName", "getPlayerName");
        registerGetter("isAd", "getIsAd");

        registerGetter("contentTitle", "getTitle");
        registerGetter("contentBitrate", "getBitrate");
        registerGetter("contentRenditionName", "getRenditionName");
        registerGetter("contentRenditionBitrate", "getRenditionBitrate");
        registerGetter("contentRenditionWidth", "getRenditionWidth");
        registerGetter("contentRenditionHeight", "getRenditionHeight");
        registerGetter("contentDuration", "getDuration");
        registerGetter("contentPlayhead", "getPlayhead");
        registerGetter("contentLanguage", "getLanguage");
        registerGetter("contentSrc", "getSrc");
        registerGetter("contentIsMuted", "getIsMuted");
        registerGetter("contentCdn", "getCdn");
        registerGetter("contentFps", "getFps");
        registerGetter("contentPlayrate", "getPlayrate");
        registerGetter("contentIsLive", "getIsLive");
        registerGetter("contentIsAutoplayed", "getIsAutoplayed");
        registerGetter("contentPreload", "getPreload");
        registerGetter("contentIsFullscreen", "getIsFullscreen");

        heartbeat = new Heartbeat(this);
    }

    @Override
    public void sendRequest() {
        if (heartbeat != null) {
            heartbeat.startTimer();
        }
        super.sendRequest();
    }

    @Override
    public void sendEnd() {
        if (heartbeat != null) {
            heartbeat.abortTimer();
        }
        super.sendEnd();
    }

    public Heartbeat getHeartbeat() {
        return heartbeat;
    }

    public void releaseTracker() {
        if (heartbeat != null) {
            heartbeat.abortTimer();
            heartbeat = null;
        }
    }

    public Object getIsAd() {
        return new Long(0);
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

    public void setOptionKey(String name, Object value) {
        updateAttribute(name, CAL.convertObjectToHolder(value));
    }

    public void setOptionKey(String name, Object value, String action) {
        updateAttribute(name, CAL.convertObjectToHolder(value), action);
    }

    public void generateCustomViewId() {
        UUID uuid = UUID.randomUUID();
        setCustomViewId(uuid.toString());
    }

    protected void registerGetter(String name, String methodName) {
        long ptr = getCPtr(this);
        try {
            CAL.registerGetter(name, this, this.getClass().getMethod(methodName), new Long(ptr));
        }
        catch (Exception e) {
            NRLog.e("Getter not registered in " + this.getClass().getSimpleName() + " = " + e);
        }
    }

    public long getCppPointer() {
        return getCPtr(this);
    }

    public void setSrc(Uri uri) { this.setSrc(Arrays.asList(uri)); }

    // To be overwritten
    public void setSrc(List<Uri> uris) { }
}
