package com.newrelic.videoagent.core;

import com.newrelic.videoagent.core.utils.NRLogLevel;

import org.junit.Test;

import static org.junit.Assert.*;

public class NRVideoConfigurationLoggingTest {

    private static final String TOKEN = "test-token-1234567890";

    @Test
    public void loggingDisabledAndNoLevelByDefault() {
        NRVideoConfiguration config = new NRVideoConfiguration.Builder(TOKEN).build();

        assertFalse(config.isDebugLoggingEnabled());
        assertNull(config.getLoggingLevel());
    }

    @Test
    public void legacyEnableLoggingStillWorks() {
        NRVideoConfiguration config = new NRVideoConfiguration.Builder(TOKEN).enableLogging().build();

        assertTrue(config.isDebugLoggingEnabled());
    }

    @Test
    public void enableLoggingBooleanAndLevel() {
        NRVideoConfiguration config = new NRVideoConfiguration.Builder(TOKEN)
                .enableLogging(true)
                .withLoggingLevel(NRLogLevel.WARNING)
                .build();

        assertTrue(config.isDebugLoggingEnabled());
        assertEquals(NRLogLevel.WARNING, config.getLoggingLevel());
    }

    @Test
    public void enableLoggingFalseDisables() {
        NRVideoConfiguration config = new NRVideoConfiguration.Builder(TOKEN)
                .enableLogging()
                .enableLogging(false)
                .build();

        assertFalse(config.isDebugLoggingEnabled());
    }
}
