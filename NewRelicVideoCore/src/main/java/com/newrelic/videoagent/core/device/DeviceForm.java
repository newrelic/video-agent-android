package com.newrelic.videoagent.core.device;

/**
 * Device form factor enumeration matching New Relic Android Agent DeviceForm
 * Based on Android's Configuration.screenLayout classification
 */
public enum DeviceForm {
    SMALL,
    NORMAL,
    LARGE,
    XLARGE,
    UNKNOWN
}
