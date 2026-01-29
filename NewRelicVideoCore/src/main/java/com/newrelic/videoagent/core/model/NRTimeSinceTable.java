package com.newrelic.videoagent.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Time since table model.
 */
public class NRTimeSinceTable {

    private List<NRTimeSince> timeSinceTable;

    /**
     * Init a new time since table.
     */
    public NRTimeSinceTable() {
        timeSinceTable = new ArrayList<>();
    }

    /**
     * Add entry to TimeSince table.
     *
     * @param action Action.
     * @param attribute Attribute name.
     * @param filter Filter for the actions where the attribute applies, a regular expression.
     */
    public void addEntryWith(String action, String attribute, String filter) {
        addEntry(new NRTimeSince(action, attribute, filter));
    }

    /**
     * Add entry using NRTimeSince model.
     *
     * @param ts Model.
     */
    public void addEntry(NRTimeSince ts) {
        timeSinceTable.add(ts);
    }

    /**
     * Apply timeSince attributes to a given action.
     *
     * @param action Action.
     * @param attributes Attribute list.
     */
    public void applyAttributes(String action, Map<String, Object> attributes) {
        for (NRTimeSince ts : timeSinceTable) {
            if (ts.isMatch(action)) {
                attributes.put(ts.getAttribute(), ts.timeSince());
            }
            if (ts.isAction(action)) {
                // attributes.put("elapsedTime", ts.timeSince());
                ts.now();
            }
        }
    }}