package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;

/**
 * Callback interface for harvest operations
 * Extracted from inner interface for better organization
 */
public interface HarvestCallback {
    void beforeHarvest(List<Map<String, Object>> events);
    void afterHarvest(List<Map<String, Object>> events, boolean success, Exception error);
}
