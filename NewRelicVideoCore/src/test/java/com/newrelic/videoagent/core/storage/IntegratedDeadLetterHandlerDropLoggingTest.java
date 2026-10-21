package com.newrelic.videoagent.core.storage;

import android.util.Log;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;
import com.newrelic.videoagent.core.utils.NRLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests drop logging when the dead letter handler evicts events to make room for retries.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class IntegratedDeadLetterHandlerDropLoggingTest {

    @Mock
    private CrashSafeEventBuffer mockMainBuffer;

    @Mock
    private HttpClientInterface mockHttpClient;

    @Mock
    private NRVideoConfiguration mockConfiguration;

    private IntegratedDeadLetterHandler handler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConfiguration.isTV()).thenReturn(false);
        when(mockConfiguration.isMemoryOptimized()).thenReturn(false);
        when(mockConfiguration.getRegularBatchSizeBytes()).thenReturn(50000);
        when(mockConfiguration.getLiveBatchSizeBytes()).thenReturn(25000);
        when(mockConfiguration.getDeadLetterRetryInterval()).thenReturn(60000L);
        when(mockConfiguration.getMaxDeadLetterSize()).thenReturn(20);

        ShadowLog.clear();
        NRLog.setLevel(null);
        NRLog.enable();
        handler = new IntegratedDeadLetterHandler(mockMainBuffer, mockHttpClient, mockConfiguration);
    }

    @After
    public void tearDown() {
        NRLog.setLevel(null);
        NRLog.disable();
    }

    private List<Map<String, Object>> events(int count) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new HashMap<String, Object>());
        }
        return list;
    }

    private int countLogs(int priority) {
        int n = 0;
        for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
            if (item.type == priority) n++;
        }
        return n;
    }

    @Test
    public void logsEachEvictedEventWithCumulativeCount() {
        // Capacity 20, evicts max(20/20, 1) = 1 event per add once full
        handler.handleFailedEvents(events(25), NRVideoConstants.EVENT_TYPE_ONDEMAND);

        int errors = countLogs(Log.ERROR);
        assertTrue(errors > 0);
        String last = null;
        for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
            if (item.type == Log.ERROR) last = item.msg;
        }
        assertEquals("Dead letter retry buffer full (capacity 20), oldest event dropped. Total dropped this session: "
                + errors, last);
    }

    @Test
    public void exhaustedRetriesAreBackedUpNotDropped() {
        Map<String, Object> exhausted = new HashMap<>();
        Map<String, Object> meta = new HashMap<>();
        meta.put("retryCount", 99);
        exhausted.put("retryMetadata", meta);
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(exhausted);

        handler.handleFailedEvents(list, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        verify(mockMainBuffer).backupFailedEvents(anyList());
        assertEquals(0, countLogs(Log.ERROR));
    }

    @Test
    public void fillLinesComeFromOneLoggerAtDefaultCapacity() {
        when(mockConfiguration.getMaxDeadLetterSize()).thenReturn(100);
        handler = new IntegratedDeadLetterHandler(mockMainBuffer, mockHttpClient, mockConfiguration);
        ShadowLog.clear();

        handler.handleFailedEvents(events(130), NRVideoConstants.EVENT_TYPE_ONDEMAND);

        assertEquals(1, countLogs(Log.INFO));
        assertEquals(1, countLogs(Log.WARN));
    }
}
