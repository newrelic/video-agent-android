package com.newrelic.videoagent.core.harvest;

import android.util.Log;

import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.core.utils.NRLogLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests buffer near-full (WARNING) and dropped-event (ERROR) logging in PriorityEventBuffer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class PriorityEventBufferDropLoggingTest {

    // Mobile on-demand capacity is 350 events; 90% is 315
    private static final int MAX_ONDEMAND = 350;
    private static final int NEAR_FULL = 315;

    private PriorityEventBuffer buffer;

    @Before
    public void setUp() {
        ShadowLog.clear();
        NRLog.setLevel(null);
        NRLog.enable();
        buffer = new PriorityEventBuffer(false);
        buffer.setOverflowCallback(type -> { });
        buffer.setCapacityCallback((capacity, type) -> { });
    }

    @After
    public void tearDown() {
        NRLog.setLevel(null);
        NRLog.disable();
    }

    private void addEvents(int count) {
        for (int i = 0; i < count; i++) {
            Map<String, Object> event = new HashMap<>();
            event.put("actionName", "CONTENT_HEARTBEAT_" + i);
            buffer.addEvent(event);
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
    public void noLogsBelowNinetyPercent() {
        addEvents(NEAR_FULL - 1);

        assertEquals(0, countLogs(Log.WARN));
        assertEquals(0, countLogs(Log.ERROR));
    }

    @Test
    public void warnsOncePerCrossingAtNinetyPercent() {
        addEvents(MAX_ONDEMAND);

        assertEquals(1, countLogs(Log.WARN));
        assertEquals(0, countLogs(Log.ERROR));
    }

    @Test
    public void warningLatchResetsAfterDrain() {
        addEvents(NEAR_FULL);
        assertEquals(1, countLogs(Log.WARN));

        List<Map<String, Object>> batch;
        do {
            batch = buffer.pollBatchByPriority(Integer.MAX_VALUE, null, NRVideoConstants.EVENT_TYPE_ONDEMAND);
        } while (!batch.isEmpty());

        addEvents(NEAR_FULL);
        assertEquals(2, countLogs(Log.WARN));
    }

    @Test
    public void errorsOnEveryDropWithCumulativeCount() {
        addEvents(MAX_ONDEMAND + 3);

        assertEquals(3, countLogs(Log.ERROR));
        int last = ShadowLog.getLogs().size() - 1;
        assertTrue(ShadowLog.getLogs().get(last).msg.contains("Total events dropped this session: 3"));
    }

    @Test
    public void dropCountDoesNotResetAcrossDrains() {
        addEvents(MAX_ONDEMAND + 1);
        while (!buffer.pollBatchByPriority(Integer.MAX_VALUE, null, NRVideoConstants.EVENT_TYPE_ONDEMAND).isEmpty()) {
            // drain
        }
        addEvents(MAX_ONDEMAND + 1);

        int last = ShadowLog.getLogs().size() - 1;
        assertTrue(ShadowLog.getLogs().get(last).msg.contains("Total events dropped this session: 2"));
    }

    @Test
    public void eventCountUnchangedByLogging() {
        addEvents(MAX_ONDEMAND + 5);

        assertEquals(MAX_ONDEMAND, buffer.getEventCount());
    }

    @Test
    public void errorLevelSuppressesWarning() {
        NRLog.setLevel(NRLogLevel.ERROR);
        addEvents(MAX_ONDEMAND + 1);

        assertEquals(0, countLogs(Log.WARN));
        assertEquals(1, countLogs(Log.ERROR));
    }

    @Test
    public void noLogsWhenLoggingDisabled() {
        NRLog.disable();
        addEvents(MAX_ONDEMAND + 1);

        assertEquals(0, ShadowLog.getLogs().size());
    }
}
