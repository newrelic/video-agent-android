package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;

/**
 * Interface for HTTP client implementations
 * Handles sending events to New Relic endpoints
 * OPTIMIZED: Removed unused timeout and cleanup methods
 */
public interface HttpClientInterface {

    /**
     * Sends a batch of events to the specified endpoint type
     * @param events List of events to send
     * @param endpointType Type of endpoint ("regular", "live", etc.)
     * @return true if successful, false otherwise
     */
    boolean sendEvents(List<Map<String, Object>> events, String endpointType);
}
