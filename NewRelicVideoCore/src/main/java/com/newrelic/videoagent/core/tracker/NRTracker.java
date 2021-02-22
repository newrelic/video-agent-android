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

/**
 * `NRTracker` defines the basic behaviour of a tracker.
 */
public class NRTracker {

    /**
     * Linked tracker.
     */
    public NRTracker linkedTracker;

    private final NREventAttributes eventAttributes;
    private NRTimeSinceTable timeSinceTable;

    /**
     * Create a new NRTracker.
     */
    public NRTracker() {
        eventAttributes = new NREventAttributes();
        generateTimeSinceTable();
    }

    /**
     * Tracker is ready.
     */
    public void trackerReady() {
        sendEvent(TRACKER_READY);
    }

    /**
     * Set custom attribute for all events.
     *
     * @param key Attribute name.
     * @param value Attribute value.
     */
    public void setAttribute(String key, Object value) {
        setAttribute(key, value, null);
    }

    /**
     * Set custom attribute for selected events.
     *
     * WARNING: if the same attribute is defined for multiple action filters that could potentially match the same action, the behaviour is undefined. The user is responsable for designing filters that are sufficiently selective.
     *
     * @param key Attribute name.
     * @param value Attribute value.
     * @param action Action filter, a regexp.
     */
    public void setAttribute(String key, Object value, String action) {
        eventAttributes.setAttribute(key, value, action);
    }

    /**
     * Generate attributes for a given action.
     *
     * @param action Action being generated.
     * @param attributes Specific attributes sent along the action.
     * @return Map of attributes.
     */
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        attributes = eventAttributes.generateAttributes(action, attributes);
        attributes.put("coreVersion", getCoreVersion());
        attributes.put("agentSession", getAgentSession());
        return attributes;
    }

    /**
     * Add an entry to the timeSince table.
     *
     * @param action Action name.
     * @param attribute Attribute name.
     * @param filter Filter for the actions where the attribute applies.
     */
    public void addTimeSinceEntry(String action, String attribute, String filter) {
        timeSinceTable.addEntryWith(action, attribute, filter);
    }

    /**
     * Add entry using NRTimeSince model.
     *
     * @param ts Model.
     */
    public void addTimeSinceEntry(NRTimeSince ts) {
        timeSinceTable.addEntry(ts);
    }

    /**
     * Generate table of timeSince attributes.
     */
    public void generateTimeSinceTable() {
        timeSinceTable = new NRTimeSinceTable();
        addTimeSinceEntry(TRACKER_READY, "timeSinceTrackerReady", "[A-Z_]+");
    }

    /**
     * Register tracker listeners.
     */
    public void registerListeners() {}

    /**
     * Unregister tracker listeners.
     */
    public void unregisterListeners() {}

    /**
     * Dispose of the tracker. Internally call `unregisterListeners()`.
     */
    public void dispose() {
        unregisterListeners();
    }

    /**
     * Method called right before sending the event to New Relic.
     *
     * Last chance to decide if the event must be sent or not, or to modify the attributes.
     *
     * @param action Action name.
     * @param attributes Action attributes.
     * @return Must be send or not.
     */
    public boolean preSend(String action, Map<String, Object> attributes) {
        return true;
    }

    /**
     * Send event.
     *
     * @param action Action name.
     */
    public void sendEvent(String action) {
        sendEvent(action, null);
    }

    /**
     * Send event with attributes.
     *
     * @param action Action name.
     * @param attributes Action attributes.
     */
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

    /**
     * Get the core version.
     *
     * @return Core version.
     */
    public String getCoreVersion() {
        return NRVIDEO_CORE_VERSION;
    }

    /**
     * Get agent session.
     *
     * @return Agent session ID.
     */
    public String getAgentSession() {
        return NewRelicVideoAgent.getInstance().getSessionId();
    }
}
