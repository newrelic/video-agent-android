package com.newrelic.videoagent.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NRTimeSinceTable {

    private List<NRTimeSince> timeSinceTable;

    public NRTimeSinceTable() {
        timeSinceTable = new ArrayList<>();
    }

    public void addEntryWith(String action, String attribute, String filter) {
        addEntry(new NRTimeSince(action, attribute, filter));
    }

    public void addEntry(NRTimeSince ts) {
        timeSinceTable.add(ts);
    }

    public void applyAttributes(String action, Map<String, Object> attributes) {
        for (NRTimeSince ts : timeSinceTable) {
            if (ts.isMatch(action)) {
                attributes.put(ts.getAttribute(), ts.timeSince());
            }
            if (ts.isAction(action)) {
                ts.now();
            }
        }
    }
}
