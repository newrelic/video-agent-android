package com.newrelic.videoagent.core.device;

/**
 * Enhanced with Android TV and modern device support
 * Based on Android's Configuration.screenLayout classification with extensions
 */
public enum DeviceForm {
    SMALL,      // Small screen devices (phones)
    NORMAL,     // Normal screen devices (standard phones)
    LARGE,      // Large screen devices (small tablets)
    XLARGE,     // Extra large screen devices (large tablets)
    TABLET,     // Tablet devices (alternative classification)
    TV,         // Android TV devices
    UNKNOWN     // Unknown or unclassified devices
}
