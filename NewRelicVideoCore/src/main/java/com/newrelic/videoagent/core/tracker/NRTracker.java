package com.newrelic.videoagent.core.tracker;

import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.core.model.NREventAttributes;
import com.newrelic.videoagent.core.model.NRTimeSince;
import com.newrelic.videoagent.core.model.NRTimeSinceTable;
import com.newrelic.videoagent.core.utils.NRLog;

import java.util.Iterator;
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
        sendVideoEvent(TRACKER_READY);
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
     * Sends the given eventType and action wrapped in attributes.
     * @param eventType EventType for this telemetry.
     * @param action Action name.
     * @param attributes Event Type attributes for this action.
     */
    public void sendEvent(String eventType, String action, Map<String, Object> attributes) {
        attributes = getAttributes(action, attributes);
        timeSinceTable.applyAttributes(action, attributes);

        // Process QoE events that require timing attributes
        processQoeEvents(action, attributes);

        attributes.put("agentSession", getAgentSession());
        attributes.put("instrumentation.provider", "newrelic");
        attributes.put("instrumentation.name", getInstrumentationName());
        attributes.put("instrumentation.version", getCoreVersion());

        // Remove null and empty values
        Iterator<Object> it = attributes.values().iterator();
        while (it.hasNext()) {
            Object v = it.next();
            if (v == null || "".equals(v)) {
                it.remove();
            }
        }
        NRLog.d("SEND EVENT " + action + " , attr = " + attributes);
        if (preSend(action, attributes)) {
            attributes.put("actionName", action);
            NRVideo.recordEvent(eventType, attributes);
        }
    }

    /**
     * Send event of type VideoCustomAction with attributes.
     *
     * @param action Action name.
     * @param attributes Action attributes.
     */
    public void sendEvent(String action, Map<String, Object> attributes) {
        sendEvent(NR_VIDEO_CUSTOM_EVENT, action, attributes);
    }

    /**
     * Send event with attributes.
     *
     * @param action Action name.
     */
    public void sendVideoEvent(String action) {
        sendVideoEvent(action, null);
    }

    /**
     * Send event with attributes.
     *
     * @param action Action name.
     * @param attributes Event attributes.
     */
    public void sendVideoEvent(String action, Map<String, Object> attributes) {
        sendEvent(NR_VIDEO_EVENT, action, attributes);
    }

    /**
     * Send event with attributes.
     *
     * @param action Action name.
     * @param attributes Event attributes.
     */
    public void sendVideoAdEvent(String action) {
        sendVideoAdEvent(action, null);
    }

    /**
     * Send event with attributes.
     *
     * @param action Action name.
     * @param attributes Event attributes.
     */
    public void sendVideoAdEvent(String action, Map<String, Object> attributes) {
        sendEvent(NR_VIDEO_AD_EVENT, action, attributes);
    }

    /**
     * Send event with attributes.
     *
     * @param action Action name.
     * @param attributes Event attributes.
     */
    public void sendVideoErrorEvent(String action, Map<String, Object> attributes) {
        sendEvent(NR_VIDEO_ERROR_EVENT, action, attributes);
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
     * Get the core version.
     *
     * @return Core version.
     */
    public String getInstrumentationName() {
        return "Mobile/Android";
    }

    /**
     * Get agent session.
     *
     * @return Agent session ID.
     */
    public String getAgentSession() {
        return NewRelicVideoAgent.getInstance().getSessionId();
    }
    /**
     * Process QoE events that require timing attributes to be already applied.
     * This method handles QOE_AGGREGATE and CONTENT_BUFFER_END events for various QoE calculations.
     *
     * @param action The event action name
     * @param attributes The event attributes map (timing attributes already applied)
     */
    private void processQoeEvents(String action, Map<String, Object> attributes) {
        if (this instanceof NRVideoTracker) {
            NRVideoTracker videoTracker = (NRVideoTracker) this;

            if (QOE_AGGREGATE.equals(action)) {
                // Process QOE_AGGREGATE events for startup time calculation
                Long timeSinceRequested = (Long) attributes.get("timeSinceRequested");
                Long timeSinceStarted = (Long) attributes.get("timeSinceStarted");
                Long timeSinceLastError = (Long) attributes.get("timeSinceLastError");
                videoTracker.calculateAndAddStartupTime(attributes, timeSinceRequested, timeSinceStarted, timeSinceLastError);
            }
            else if (CONTENT_BUFFER_END.equals(action)) {
                // Process CONTENT_BUFFER_END events for rebuffering time calculation
                videoTracker.calculateRebufferingTime(attributes);
            }
        }
    }
}