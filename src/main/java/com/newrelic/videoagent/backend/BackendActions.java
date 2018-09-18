package com.newrelic.videoagent.backend;

import com.newrelic.agent.android.NewRelic;
import com.newrelic.videoagent.AVLog;
import java.util.HashMap;
import java.util.Map;

// TODO: remove "public" for this class
public class BackendActions {

    private Map generalOptions = new HashMap();

    public Map getGeneralOptions() {
        return generalOptions;
    }

    public void setGeneralOptions(Map generalOptions) {
        this.generalOptions = generalOptions;
    }

    public void sendAction(String name) {
        sendAction(name, null);
    }

    public void sendAction(String name, Map attr) {

        attr = (attr == null) ? new HashMap() : attr;
        attr.put("actionName", name);
        attr.putAll(generalOptions);

        // TODO: implement action specific options

        if (NewRelic.currentSessionId() != null) {
            NewRelic.recordCustomEvent("VideoEvent", attr);
        }
        else {
            AVLog.e("⚠️ The NewRelicAgent is not initialized, you need to do it before using the NewRelicVideo. ⚠️");
        }

        AVLog.d("sendAction name = " + name + " attr = " + attr);
    }

    // Contents senders

    public void sendRequest() {
        sendAction(EventDefs.CONTENT_REQUEST.toString());
    }

    public void sendStart() {
        sendAction(EventDefs.CONTENT_START.toString());
    }

    public void sendEnd() {
        sendAction(EventDefs.CONTENT_END.toString());
    }

    public void sendPause() {
        sendAction(EventDefs.CONTENT_PAUSE.toString());
    }

    public void sendResume() {
        sendAction(EventDefs.CONTENT_RESUME.toString());
    }

    public void sendSeekStart() {
        sendAction(EventDefs.CONTENT_SEEK_START.toString());
    }

    public void sendSeekEnd() {
        sendAction(EventDefs.CONTENT_SEEK_END.toString());
    }

    public void sendBufferStart() {
        sendAction(EventDefs.CONTENT_BUFFER_START.toString());
    }

    public void sendBufferEnd() {
        sendAction(EventDefs.CONTENT_BUFFER_END.toString());
    }

    public void sendHeartbeat() {
        sendAction(EventDefs.CONTENT_HEARTBEAT.toString());
    }

    public void sendRenditionChange() {
        sendAction(EventDefs.CONTENT_RENDITION_CHANGE.toString());
    }

    public void sendError() {
        sendAction(EventDefs.CONTENT_ERROR.toString());
    }

    // Ads senders

    public void sendAdRequest() {
        sendAction(EventDefs.AD_REQUEST.toString());
    }

    public void sendAdStart() {
        sendAction(EventDefs.AD_START.toString());
    }

    public void sendAdEnd() {
        sendAction(EventDefs.AD_END.toString());
    }

    public void sendAdPause() {
        sendAction(EventDefs.AD_PAUSE.toString());
    }

    public void sendAdResume() {
        sendAction(EventDefs.AD_RESUME.toString());
    }

    public void sendAdSeekStart() {
        sendAction(EventDefs.AD_SEEK_START.toString());
    }

    public void sendAdSeekEnd() {
        sendAction(EventDefs.AD_SEEK_END.toString());
    }

    public void sendAdBufferStart() {
        sendAction(EventDefs.AD_BUFFER_START.toString());
    }

    public void sendAdBufferEnd() {
        sendAction(EventDefs.AD_BUFFER_END.toString());
    }

    public void sendAdHeartbeat() {
        sendAction(EventDefs.AD_HEARTBEAT.toString());
    }

    public void sendAdRenditionChange() {
        sendAction(EventDefs.AD_RENDITION_CHANGE.toString());
    }

    public void sendAdError() {
        sendAction(EventDefs.AD_ERROR.toString());
    }

    public void sendAdBreakStart() {
        sendAction(EventDefs.AD_BREAK_START.toString());
    }

    public void sendAdBreakEnd() {
        sendAction(EventDefs.AD_BREAK_END.toString());
    }

    public void sendAdQuartile() {
        sendAction(EventDefs.AD_QUARTILE.toString());
    }

    public void sendAdClick() {
        sendAction(EventDefs.AD_CLICK.toString());
    }

    // Misc senders

    public void sendPlayerReady() {
        sendAction(EventDefs.PLAYER_READY.toString());
    }

    public void sendDownload() {
        sendAction(EventDefs.DOWNLOAD.toString());
    }
}
