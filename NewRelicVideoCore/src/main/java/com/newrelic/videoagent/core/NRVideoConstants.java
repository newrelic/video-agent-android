package com.newrelic.videoagent.core;

/**
 * New Relic Video Agent constants for consistent usage across the video agent
 * Improves performance by avoiding string literal creation and reduces typos
 */
public final class NRVideoConstants {

    // Device Types
    public static final String ANDROID_TV = "AndroidTV";
    public static final String MOBILE = "Mobile";

    // Event Types
    public static final String EVENT_TYPE_LIVE = "live";
    public static final String EVENT_TYPE_ONDEMAND = "ondemand";

    // Event Categories
    public static final String CATEGORY_DEFAULT = "default";
    public static final String CATEGORY_NORMAL = "normal";

    // Health Status Constants
    public static final String HEALTH_IDLE = "IDLE";
    public static final String HEALTH_HEALTHY = "HEALTHY";
    public static final String HEALTH_WARNING = "WARNING";
    public static final String HEALTH_DEGRADED = "DEGRADED";
    public static final String HEALTH_CRITICAL = "CRITICAL";

    // Prevent instantiation
    private NRVideoConstants() {
        throw new AssertionError("Constants class should not be instantiated");
    }
}
