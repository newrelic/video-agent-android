package com.newrelic.videoagent.core.telemetry.configuration;

public class VideoAgentConfiguration {
    private static final String APPLICATION_TOKEN = "AAb789c05df687cecaa33bc6b159e2a76a7cac9f65-NRMA";

    // Default New Relic mobile collector host. This will now be overridden in VideoHarvestConnection.
    private String collectorHost = "mobile-collector.newrelic.com";

    // Represents an invalid session duration.
    private static final long INVALID_SESSION_DURATION = 0;

    /**
     * Constructs a new VideoAgentConfiguration with the default application token.
     */
    public VideoAgentConfiguration() {
        // Constructor uses the hardcoded token.
    }

    /**
     * Retrieves the application token for licensing.
     * @return The New Relic application token.
     */
    public String getApplicationToken() {
        return APPLICATION_TOKEN;
    }

    /**
     * Retrieves the host for the New Relic mobile data collector.
     * @return The collector host URL.
     */
    public String getCollectorHost() {
        return collectorHost;
    }

    /**
     * Sets a custom host for the New Relic mobile data collector.
     * @param collectorHost The custom collector host URL.
     */
    public void setCollectorHost(String collectorHost) {
        this.collectorHost = collectorHost;
    }

    // getCrashCollectorHost and setCrashCollectorHost methods have been removed as requested.

    /**
     * Returns the value representing an invalid session duration.
     * @return The invalid session duration value.
     */
    public long getInvalidSessionDuration() {
        return INVALID_SESSION_DURATION;
    }
}
