package com.newrelic.videoagent.core.utils;

/**
 * Minimum severity for NRLog output. Set via NRVideoConfiguration.Builder#withLoggingLevel.
 */
public enum NRLogLevel {
    INFO,
    WARNING,
    ERROR
}
