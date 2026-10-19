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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class NRLogLevelTest {

    @Before
    public void setUp() {
        ShadowLog.clear();
        NRLog.setLevel(null);
        NRLog.enable();
    }

    @After
    public void tearDown() {
        NRLog.setLevel(null);
        NRLog.disable();
    }

    private int count() {
        return ShadowLog.getLogs().size();
    }

    @Test
    public void defaultLogsEveryLevel() {
        NRLog.d("d");
        NRLog.i("i");
        NRLog.w("w");
        NRLog.e("e");

        assertEquals(4, count());
    }

    @Test
    public void infoLevelSuppressesDebugOnly() {
        NRLog.setLevel(NRLogLevel.INFO);
        NRLog.d("d");
        NRLog.i("i");
        NRLog.w("w");
        NRLog.e("e");

        assertEquals(3, count());
    }

    @Test
    public void warningLevelKeepsWarningAndError() {
        NRLog.setLevel(NRLogLevel.WARNING);
        NRLog.i("i");
        NRLog.w("w");
        NRLog.e("e");

        assertEquals(2, count());
        assertEquals(Log.WARN, ShadowLog.getLogs().get(0).type);
    }

    @Test
    public void errorLevelKeepsErrorOnly() {
        NRLog.setLevel(NRLogLevel.ERROR);
        NRLog.w("w");
        NRLog.e("e");

        assertEquals(1, count());
        assertEquals(Log.ERROR, ShadowLog.getLogs().get(0).type);
    }

    @Test
    public void levelDoesNotEnableLoggingWhenDisabled() {
        NRLog.disable();
        NRLog.setLevel(NRLogLevel.INFO);
        NRLog.e("e");

        assertEquals(0, count());
    }
}
