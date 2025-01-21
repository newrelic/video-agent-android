package com.newrelic.videoagent.core.model;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Event attributes model.
 */
public class NREventAttributes {

    private Map<String, Map<String, Object>> attributeBuckets;
    private String userId;

    /**
     * Ïnit a new event attributes model.
     */
    public NREventAttributes() {
        attributeBuckets = new HashMap<>();
    }

    /**
     * Set attribute for a given action filter.
     *
     * @param key Attribute name.
     * @param value Attribute value.
     * @param filter Action filter, a regular expression.
     */
    public void setAttribute(String key, Object value, String filter) {
        // If no filter defined, use universal filter that matches any action name
        if (filter == null) {
            filter = "[A-Z_]+";
        }

        // Attribute doesn't exit yet, create it
        if (!attributeBuckets.containsKey(filter)) {
            attributeBuckets.put(filter, new HashMap<>());
        }

        attributeBuckets.get(filter).put(key, value);
    }

    /**
     * Set userId.
     *
     * @param userId User Id.
     */
    public void setUserId(String userId) {
        this.userId = userId;
        setAttribute("enduser.id", userId, null);
    }

    /**
     * Generate list of attributes for a given action.
     *
     * @param action Action.
     * @param attributes Append attributes.
     * @return Map of attributes.
     */
    public Map<String, Object> generateAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr = new HashMap<>();

        if (attributes != null) {
            attr.putAll(attributes);
        }

        for (Map.Entry<String, Map<String, Object>> pair : attributeBuckets.entrySet()) {
            String filter = pair.getKey();
            if (checkFilter(filter, action)) {
                Map<String, Object> bucket = pair.getValue();
                attr.putAll(bucket);
            }
        }

        return attr;
    }

    private boolean checkFilter(String filter, String action) {
        return Pattern.matches(filter, action);
    }

    public String toString() {
        return getClass().getName() + " = " + attributeBuckets;
    }
}
