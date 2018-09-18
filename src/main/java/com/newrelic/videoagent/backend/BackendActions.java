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

    public void sendRequest() {
        sendAction(EventDefs.CONTENT_REQUEST.toString());
    }
}

