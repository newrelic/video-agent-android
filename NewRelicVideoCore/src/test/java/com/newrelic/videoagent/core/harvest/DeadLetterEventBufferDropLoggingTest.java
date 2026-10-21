package com.newrelic.videoagent.core.harvest;

import android.util.Log;

import com.newrelic.videoagent.core.utils.NRLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class DeadLetterEventBufferDropLoggingTest {

    // Mobile capacity is 100
    private DeadLetterEventBuffer buffer;

    @Before
    public void setUp() {
        ShadowLog.clear();
        NRLog.setLevel(null);
        NRLog.enable();
        buffer = new DeadLetterEventBuffer(false);
    }

    @After
    public void tearDown() {
        NRLog.setLevel(null);
        NRLog.disable();
    }

    private void addEvents(int count) {
        for (int i = 0; i < count; i++) {
            buffer.addEvent(new HashMap<String, Object>());
        }
    }

    private int countLogs(int priority) {
        int n = 0;
        for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
            if (item.type == priority) n++;
        }
        return n;
    }

    @Test
    public void infoAndWarningOncePerCrossing() {
        addEvents(100);

        assertEquals(1, countLogs(Log.INFO));
        assertEquals(1, countLogs(Log.WARN));
        assertEquals(0, countLogs(Log.ERROR));
    }

    @Test
    public void latchesRearmAfterDrain() {
        addEvents(90);
        buffer.cleanup();
        addEvents(90);

        assertEquals(2, countLogs(Log.INFO));
        assertEquals(2, countLogs(Log.WARN));
    }

    @Test
    public void errorOnEveryDropWithCumulativeCount() {
        addEvents(103);

        assertEquals(3, countLogs(Log.ERROR));
        String last = ShadowLog.getLogs().get(ShadowLog.getLogs().size() - 1).msg;
        assertEquals("Dead letter buffer full (capacity 100), oldest event dropped. Total dropped this session: 3", last);
        assertEquals(100, buffer.getEventCount());
    }
}
