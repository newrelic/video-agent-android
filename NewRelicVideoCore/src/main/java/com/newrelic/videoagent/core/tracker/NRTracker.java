package com.newrelic.videoagent.core.tracker;

import com.newrelic.agent.android.NewRelic;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.core.model.NREventAttributes;
import com.newrelic.videoagent.core.model.NRTimeSince;
import com.newrelic.videoagent.core.model.NRTimeSinceTable;
import com.newrelic.videoagent.core.utils.NRLog;

import java.util.HashMap;
import java.util.Map;
import static com.newrelic.videoagent.core.NRDef.*;

public class NRTracker {

    public NRTracker linkedTracker;
    private final NREventAttributes eventAttributes;
    private NRTimeSinceTable timeSinceTable;

    public NRTracker() {
        eventAttributes = new NREventAttributes();
        generateTimeSinceTable();
    }

    public void trackerReady() {
        sendEvent(TRACKER_READY);
    }

    public void setAttribute(String key, Object value) {
        setAttribute(key, value, null);
    }

    public void setAttribute(String key, Object value, String action) {
        eventAttributes.setAttribute(key, value, action);
    }

    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        attributes = eventAttributes.generateAttributes(action, attributes);
        attributes.put("coreVersion", getCoreVersion());
        attributes.put("agentSession", getAgentSession());
        return attributes;
    }

    public void addTimeSinceEntry(String action, String attribute, String filter) {
        timeSinceTable.addEntryWith(action, attribute, filter);
    }

    public void addTimeSinceEntry(NRTimeSince ts) {
        timeSinceTable.addEntry(ts);
    }

    public void generateTimeSinceTable() {
        timeSinceTable = new NRTimeSinceTable();
        addTimeSinceEntry(TRACKER_READY, "timeSinceTrackerReady", "[A-Z_]+");
    }

    // Method placeholder, to be implemented by a subclass
    public void registerListeners() {}

    // Method placeholder, to be implemented by a subclass
    public void unregisterListeners() {}

    public void dispose() {
        unregisterListeners();
    }

    public boolean preSend(String action, Map<String, Object> attributes) {
        return true;
    }

    public void sendEvent(String action) {
        sendEvent(action, null);
    }

    public void sendEvent(String action, Map<String, Object> attributes) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }

        attributes = getAttributes(action, attributes);
        timeSinceTable.applyAttributes(action, attributes);

        NRLog.d("SEND EVENT " + action + " , attr = " + attributes);

        // Remove null values
        while (attributes.values().remove(null));

        if (preSend(action, attributes)) {
            attributes.put("actionName", action);

            if (!NewRelic.recordCustomEvent(NR_VIDEO_EVENT, attributes)) {
                NRLog.e("⚠️ Failed to recordCustomEvent. Maybe the NewRelicAgent is not initialized or the attribute list contains invalid/empty values. ⚠️");
            }
        }
    }

    public String getCoreVersion() {
        return NRVIDEO_CORE_VERSION;
    }

    public String getAgentSession() {
        return NewRelicVideoAgent.getInstance().getSessionId();
    }
}
