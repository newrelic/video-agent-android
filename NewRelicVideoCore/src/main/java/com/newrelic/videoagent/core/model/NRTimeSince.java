package com.newrelic.videoagent.core.model;

import java.util.regex.Pattern;

/**
 * Time since model.
 */
public class NRTimeSince {
    private String action;
    private String attributeName;
    private String filter;
    private long timestamp;

    /**
     * Init model with action, attribute name and filter.
     *
     * @param action Action.
     * @param attribute Attribute name.
     * @param filter Filter for the actions where the attribute applies, a regular expression.
     */
    public NRTimeSince(String action, String attribute, String filter) {
        this.action = action;
        this.attributeName = attribute;
        this.filter = filter;
        this.timestamp = 0;
    }

    /**
     * Check if model applies to the given action.
     *
     * @return True if applies, false otherwise.
     */
    public boolean isAction(String action) {
        return this.action.equals(action);
    }

    /**
     * Check if model matches the action filter.
     *
     * @return True if applies, false otherwise.
     */
    public boolean isMatch(String action) {
        return Pattern.matches(filter, action);
    }

    /**
     * Set current timestamp for timeSince attribute.
     */
    public void now() {
        timestamp = System.currentTimeMillis();
    }

    /**
     * Get current timeSince value.
     *
     * @return Time since.
     */
    public Long timeSince() {
        if (timestamp > 0) {
            return System.currentTimeMillis() - timestamp;
        }
        else {
            return 0L;
        }
    }

    /**
     * Get attribute name.
     *
     * @return Attribute name.
     */
    public String getAttribute() {
        return attributeName;
    }
}
