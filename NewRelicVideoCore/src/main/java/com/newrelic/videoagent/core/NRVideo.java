package com.newrelic.videoagent.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.newrelic.videoagent.core.harvest.HarvestManager;

public class NRVideo {
    private Map<String, Object> globalAttributes = new ConcurrentHashMap<>();

    public void setGlobalAttributes(Map<String, Object> attributes) {
        this.globalAttributes = attributes;
    }

    public void recordEvent(String eventType, Map<String, Object> attributes) {
        Map<String, Object> mergedAttributes = attributes;
        if (globalAttributes != null && !globalAttributes.isEmpty()) {
            mergedAttributes = new java.util.HashMap<>(globalAttributes);
            if (attributes != null) {
                mergedAttributes.putAll(attributes);
            }
        }
        com.newrelic.videoagent.core.harvest.HarvestManager.getInstance().recordCustomEvent(eventType, mergedAttributes);
    }

    public void addGlobalAttribute(String key, Object value) {
        globalAttributes.put(key, value);
    }

    public void removeGlobalAttribute(String key) {
        globalAttributes.remove(key);
    }

    public void clearGlobalAttributes() {
        globalAttributes.clear();
    }

    public void flushEvents() {
        // Manual flush/harvest: force immediate harvest of buffered events
        try {
            HarvestManager.getInstance().harvest();
        } catch (Exception e) {
            logError("Error during manual flush/harvest", e);
        }
    }

    public void shutdown() {
        HarvestManager.getInstance().shutdown();
    }

    public void logError(String message, Throwable t) {
        // Use Android Log for robust logging
        android.util.Log.e("NRVideo", message, t);
    }
    // Add more customer-facing API methods as needed
}
