package com.newrelic.videoagent.core.model;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class NREventAttributes {

    private Map<String, Map<String, Object>> attributeBuckets;

    public NREventAttributes() {
        attributeBuckets = new HashMap<>();
    }

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

    public Map<String, Object> generateAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr = new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> pair : attributeBuckets.entrySet()) {
            String filter = pair.getKey();
            if (checkFilter(filter, action)) {
                Map<String, Object> bucket = pair.getValue();
                attr.putAll(bucket);
            }
        }

        if (attributes != null) {
            attr.putAll(attributes);
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
