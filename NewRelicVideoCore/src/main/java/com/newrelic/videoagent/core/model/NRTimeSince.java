package com.newrelic.videoagent.core.model;

import java.util.regex.Pattern;

public class NRTimeSince {
    private String action;
    private String attributeName;
    private String filter;
    private long timestamp;

    public NRTimeSince(String action, String attribute, String filter) {
        this.action = action;
        this.attributeName = attribute;
        this.filter = filter;
        this.timestamp = 0;
    }

    public boolean isAction(String action) {
        return this.action.equals(action);
    }

    public boolean isMatch(String action) {
        return Pattern.matches(filter, action);
    }

    public void now() {
        timestamp = System.currentTimeMillis();
    }

    public Long timeSince() {
        if (timestamp > 0) {
            return System.currentTimeMillis() - timestamp;
        }
        else {
            return null;
        }
    }

    public String getAttribute() {
        return attributeName;
    }
}
