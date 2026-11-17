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
    public void enableLoggingWithLevel() {
        NRVideoConfiguration config = new NRVideoConfiguration.Builder(TOKEN)
                .enableLogging(true, NRLogLevel.WARNING)
                .build();

        assertTrue(config.isDebugLoggingEnabled());
        assertEquals(NRLogLevel.WARNING, config.getLoggingLevel());
    }

    @Test
    public void plainEnableLoggingClearsEarlierLevel() {
        NRVideoConfiguration config = new NRVideoConfiguration.Builder(TOKEN)
                .enableLogging(true, NRLogLevel.ERROR)
                .enableLogging()
                .build();

        assertTrue(config.isDebugLoggingEnabled());
        assertNull(config.getLoggingLevel());
    }

    @Test
    public void enableLoggingBooleanTogglesFlag() {
        assertTrue(new NRVideoConfiguration.Builder(TOKEN).enableLogging(true).build().isDebugLoggingEnabled());
        assertFalse(new NRVideoConfiguration.Builder(TOKEN).enableLogging(true).enableLogging(false).build().isDebugLoggingEnabled());
    }
}
