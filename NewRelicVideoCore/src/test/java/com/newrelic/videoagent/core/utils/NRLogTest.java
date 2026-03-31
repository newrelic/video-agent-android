package com.newrelic.videoagent.core.utils;

import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static org.junit.Assert.*;

/**
 * Unit tests for NRLog that exercise actual production code.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class NRLogTest {

    @Before
    public void setUp() {
        ShadowLog.clear();
        NRLog.disable(); // Start with logging disabled
    }

    @After
    public void tearDown() {
        NRLog.disable(); // Clean up after each test
    }

    @Test
    public void testLoggingDisabledByDefault() {
        NRLog.d("Debug message");

        assertEquals(0, ShadowLog.getLogs().size());
    }

    @Test
    public void testEnableLogging() {
        NRLog.enable();
        NRLog.d("Debug message");

        assertTrue(ShadowLog.getLogs().size() > 0);
    }

    @Test
    public void testDisableLogging() {
        NRLog.enable();
        NRLog.d("First message");

        NRLog.disable();
        ShadowLog.clear();
        NRLog.d("Second message");

        assertEquals(0, ShadowLog.getLogs().size());
    }

    @Test
    public void testDebugLogging() {
        NRLog.enable();
        NRLog.d("Debug message");

        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals(Log.DEBUG, ShadowLog.getLogs().get(0).type);
        assertEquals("NRVideo", ShadowLog.getLogs().get(0).tag);
        assertEquals("Debug message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testInfoLogging() {
        NRLog.enable();
        NRLog.i("Info message");

        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals(Log.INFO, ShadowLog.getLogs().get(0).type);
        assertEquals("NRVideo", ShadowLog.getLogs().get(0).tag);
        assertEquals("Info message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testErrorLogging() {
        NRLog.enable();
        NRLog.e("Error message");

        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals(Log.ERROR, ShadowLog.getLogs().get(0).type);
        assertEquals("NRVideo", ShadowLog.getLogs().get(0).tag);
        assertEquals("Error message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testErrorLoggingWithException() {
        NRLog.enable();
        Exception exception = new Exception("Test exception");
        NRLog.e("Error with exception", exception);

        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals(Log.ERROR, ShadowLog.getLogs().get(0).type);
        assertEquals("NRVideo", ShadowLog.getLogs().get(0).tag);
        assertEquals("Error with exception", ShadowLog.getLogs().get(0).msg);
        assertNotNull(ShadowLog.getLogs().get(0).throwable);
    }

    @Test
    public void testWarningLogging() {
        NRLog.enable();
        NRLog.w("Warning message");

        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals(Log.WARN, ShadowLog.getLogs().get(0).type);
        assertEquals("NRVideo", ShadowLog.getLogs().get(0).tag);
        assertEquals("Warning message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testMultipleLogMessages() {
        NRLog.enable();
        NRLog.d("Debug");
        NRLog.i("Info");
        NRLog.w("Warning");
        NRLog.e("Error");

        assertEquals(4, ShadowLog.getLogs().size());
    }

    @Test
    public void testNoLogsWhenDisabled() {
        NRLog.d("Debug");
        NRLog.i("Info");
        NRLog.w("Warning");
        NRLog.e("Error");

        assertEquals(0, ShadowLog.getLogs().size());
    }

    @Test
    public void testEnableDisableToggle() {
        NRLog.enable();
        NRLog.d("Message 1");

        NRLog.disable();
        ShadowLog.clear();
        NRLog.d("Message 2");

        NRLog.enable();
        NRLog.d("Message 3");

        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals("Message 3", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testLogWithNullMessage() {
        NRLog.enable();
        NRLog.d(null);

        assertEquals(1, ShadowLog.getLogs().size());
        assertNull(ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testLogWithEmptyMessage() {
        NRLog.enable();
        NRLog.d("");

        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals("", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testLogWithLongMessage() {
        NRLog.enable();
        String longMessage = "This is a very long message that contains a lot of text to test if the logging handles long strings properly without any issues.";
        NRLog.d(longMessage);

        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals(longMessage, ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testErrorWithNullException() {
        NRLog.enable();
        NRLog.e("Error message", null);

        assertEquals(1, ShadowLog.getLogs().size());
        assertEquals("Error message", ShadowLog.getLogs().get(0).msg);
    }

    @Test
    public void testAllLogLevelsWithLoggingEnabled() {
        NRLog.enable();

        NRLog.d("Debug");
        NRLog.i("Info");
        NRLog.w("Warning");
        NRLog.e("Error");
        NRLog.e("Error with exception", new Exception());

        assertEquals(5, ShadowLog.getLogs().size());
        assertEquals(Log.DEBUG, ShadowLog.getLogs().get(0).type);
        assertEquals(Log.INFO, ShadowLog.getLogs().get(1).type);
        assertEquals(Log.WARN, ShadowLog.getLogs().get(2).type);
        assertEquals(Log.ERROR, ShadowLog.getLogs().get(3).type);
        assertEquals(Log.ERROR, ShadowLog.getLogs().get(4).type);
    }

    @Test
    public void testConsecutiveEnableCalls() {
        NRLog.enable();
        NRLog.enable();
        NRLog.enable();
        NRLog.d("Message");

        assertEquals(1, ShadowLog.getLogs().size());
    }

    @Test
    public void testConsecutiveDisableCalls() {
        NRLog.enable();
        NRLog.disable();
        NRLog.disable();
        NRLog.disable();
        NRLog.d("Message");

        assertEquals(0, ShadowLog.getLogs().size());
    }
}
