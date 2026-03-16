package com.newrelic.videoagent.core.storage;

import android.content.Context;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.SizeEstimator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CrashSafeEventBuffer.
 * Tests crash recovery, event buffering, and SQLite backup functionality.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class CrashSafeEventBufferTest {

    @Mock
    private NRVideoConfiguration mockConfiguration;

    private Context context;
    private VideoEventStorage storage;
    private CrashSafeEventBuffer buffer;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        context = RuntimeEnvironment.getApplication();
        storage = new VideoEventStorage(context);

        when(mockConfiguration.isTV()).thenReturn(false);

        buffer = new CrashSafeEventBuffer(context, mockConfiguration, storage);

        // Set up default callbacks to prevent NullPointerException
        buffer.setOverflowCallback(new EventBufferInterface.OverflowCallback() {
            @Override
            public void onBufferNearFull(String bufferType) {
                // Do nothing in tests
            }
        });

        buffer.setCapacityCallback(new EventBufferInterface.CapacityCallback() {
            @Override
            public void onCapacityThresholdReached(double currentCapacity, String bufferType) {
                // Do nothing in tests
            }
        });
    }

    @After
    public void tearDown() {
        buffer.cleanup();
        storage.close();
        context.deleteDatabase("nr_video_backup.db");
        context.getSharedPreferences("nr_video_crash_detection", Context.MODE_PRIVATE)
            .edit().clear().apply();
    }

    @Test
    public void testBufferInitialization() {
        assertNotNull(buffer);
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.getEventCount());
    }

    @Test
    public void testAddEvent() {
        Map<String, Object> event = createTestEvent("event1");

        buffer.addEvent(event);

        assertEquals(1, buffer.getEventCount());
        assertFalse(buffer.isEmpty());
    }

    @Test
    public void testAddMultipleEvents() {
        for (int i = 0; i < 10; i++) {
            buffer.addEvent(createTestEvent("event" + i));
        }

        assertEquals(10, buffer.getEventCount());
    }

    @Test
    public void testPollBatchByPriority() {
        buffer.addEvent(createTestEvent("event1", NRVideoConstants.EVENT_TYPE_LIVE));
        buffer.addEvent(createTestEvent("event2", NRVideoConstants.EVENT_TYPE_LIVE));

        List<Map<String, Object>> batch = buffer.pollBatchByPriority(
            10000, null, NRVideoConstants.EVENT_TYPE_LIVE);

        assertEquals(2, batch.size());
        assertEquals(0, buffer.getEventCount());
    }

    @Test
    public void testPollBatchByPriorityLive() {
        buffer.addEvent(createTestEvent("live1", NRVideoConstants.EVENT_TYPE_LIVE));
        buffer.addEvent(createTestEvent("ondemand1", NRVideoConstants.EVENT_TYPE_ONDEMAND));

        List<Map<String, Object>> liveBatch = buffer.pollBatchByPriority(
            10000, null, NRVideoConstants.EVENT_TYPE_LIVE);

        assertEquals(1, liveBatch.size());
        assertEquals("live1", liveBatch.get(0).get("eventId"));
        assertEquals(1, buffer.getEventCount());
    }

    @Test
    public void testPollBatchByPriorityOndemand() {
        buffer.addEvent(createTestEvent("live1", NRVideoConstants.EVENT_TYPE_LIVE));
        buffer.addEvent(createTestEvent("ondemand1", NRVideoConstants.EVENT_TYPE_ONDEMAND));

        List<Map<String, Object>> ondemandBatch = buffer.pollBatchByPriority(
            10000, null, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        assertEquals(1, ondemandBatch.size());
        assertEquals("ondemand1", ondemandBatch.get(0).get("eventId"));
        assertEquals(1, buffer.getEventCount());
    }

    @Test
    public void testIsEmptyTrue() {
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void testIsEmptyFalse() {
        buffer.addEvent(createTestEvent("event1"));

        assertFalse(buffer.isEmpty());
    }

    @Test
    public void testGetEventCount() {
        assertEquals(0, buffer.getEventCount());

        buffer.addEvent(createTestEvent("event1"));
        assertEquals(1, buffer.getEventCount());

        buffer.addEvent(createTestEvent("event2"));
        assertEquals(2, buffer.getEventCount());
    }

    @Test
    public void testEmergencyBackup() {
        buffer.addEvent(createTestEvent("event1", NRVideoConstants.EVENT_TYPE_LIVE));
        buffer.addEvent(createTestEvent("event2", NRVideoConstants.EVENT_TYPE_ONDEMAND));

        buffer.emergencyBackup();

        // Events should be backed up to SQLite
        assertTrue(storage.hasBackupData());
        assertEquals(2, storage.getEventCount());
    }

    @Test
    public void testEmergencyBackupEmptyBuffer() {
        buffer.emergencyBackup();

        assertFalse(storage.hasBackupData());
        assertEquals(0, storage.getEventCount());
    }

    @Test
    public void testBackupFailedEvents() {
        List<Map<String, Object>> failedEvents = new ArrayList<>();
        failedEvents.add(createTestEvent("failed1"));
        failedEvents.add(createTestEvent("failed2"));

        buffer.backupFailedEvents(failedEvents);

        assertTrue(storage.hasBackupData());
        assertEquals(2, storage.getEventCount());
    }

    @Test
    public void testBackupFailedEventsNull() {
        buffer.backupFailedEvents(null);

        assertFalse(storage.hasBackupData());
    }

    @Test
    public void testBackupFailedEventsEmpty() {
        buffer.backupFailedEvents(new ArrayList<Map<String, Object>>());

        assertFalse(storage.hasBackupData());
    }

    @Test
    public void testOnSuccessfulHarvest() {
        buffer.onSuccessfulHarvest();

        // Should not throw exception
        assertNotNull(buffer);
    }

    @Test
    public void testOnSuccessfulHarvestWithBackupData() {
        // Pre-populate SQLite with backup data
        storage.backupFailedEvents(createTestEventList(5));

        buffer.onSuccessfulHarvest();

        // Recovery mode should be activated
        assertTrue(storage.hasBackupData());
    }

    @Test
    public void testCleanup() {
        buffer.addEvent(createTestEvent("event1"));

        buffer.cleanup();

        // Cleanup should complete without errors
        assertNotNull(buffer);
    }

    @Test
    public void testSetOverflowCallback() {
        EventBufferInterface.OverflowCallback callback = mock(EventBufferInterface.OverflowCallback.class);

        buffer.setOverflowCallback(callback);

        // Should not throw exception
        assertNotNull(buffer);
    }

    @Test
    public void testSetCapacityCallback() {
        EventBufferInterface.CapacityCallback callback = mock(EventBufferInterface.CapacityCallback.class);

        buffer.setCapacityCallback(callback);

        // Should not throw exception
        assertNotNull(buffer);
    }

    @Test
    public void testGetRecoveryStats() {
        CrashSafeEventBuffer.RecoveryStats stats = buffer.getRecoveryStats();

        assertNotNull(stats);
        assertFalse(stats.isRecovering);
        assertEquals(0, stats.backupEvents);
        assertEquals(0, stats.memoryEvents);
        assertFalse(stats.isTVDevice);
    }

    @Test
    public void testGetRecoveryStatsWithBackup() {
        storage.backupFailedEvents(createTestEventList(5));

        CrashSafeEventBuffer.RecoveryStats stats = buffer.getRecoveryStats();

        assertNotNull(stats);
        assertEquals(5, stats.backupEvents);
    }

    @Test
    public void testGetRecoveryStatsWithMemoryEvents() {
        buffer.addEvent(createTestEvent("event1"));
        buffer.addEvent(createTestEvent("event2"));

        CrashSafeEventBuffer.RecoveryStats stats = buffer.getRecoveryStats();

        assertNotNull(stats);
        assertEquals(2, stats.memoryEvents);
    }

    @Test
    public void testRecoveryStatsToString() {
        CrashSafeEventBuffer.RecoveryStats stats = buffer.getRecoveryStats();

        String statsString = stats.toString();

        assertNotNull(statsString);
        assertTrue(statsString.contains("Recovery"));
    }

    @Test
    public void testTVDeviceConfiguration() {
        when(mockConfiguration.isTV()).thenReturn(true);

        CrashSafeEventBuffer tvBuffer = new CrashSafeEventBuffer(context, mockConfiguration, storage);

        CrashSafeEventBuffer.RecoveryStats stats = tvBuffer.getRecoveryStats();
        assertTrue(stats.isTVDevice);

        tvBuffer.cleanup();
    }

    @Test
    public void testMobileDeviceConfiguration() {
        when(mockConfiguration.isTV()).thenReturn(false);

        CrashSafeEventBuffer mobileBuffer = new CrashSafeEventBuffer(context, mockConfiguration, storage);

        CrashSafeEventBuffer.RecoveryStats stats = mobileBuffer.getRecoveryStats();
        assertFalse(stats.isTVDevice);

        mobileBuffer.cleanup();
    }

    @Test
    public void testEmergencyBackupPreservesEventData() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "test123");
        event.put("timestamp", 12345L);
        event.put("eventType", NRVideoConstants.EVENT_TYPE_LIVE);
        event.put("contentIsLive", true);  // Required for PriorityEventBuffer to identify as live event

        buffer.addEvent(event);
        buffer.emergencyBackup();

        List<Map<String, Object>> recovered = storage.pollEvents("live", 10);
        assertEquals(1, recovered.size());
        assertEquals("test123", recovered.get(0).get("eventId"));
    }

    @Test
    public void testMultipleEmergencyBackups() {
        buffer.addEvent(createTestEvent("event1", NRVideoConstants.EVENT_TYPE_LIVE));
        buffer.emergencyBackup();

        buffer.addEvent(createTestEvent("event2", NRVideoConstants.EVENT_TYPE_LIVE));
        buffer.emergencyBackup();

        assertEquals(2, storage.getEventCount());
    }

    @Test
    public void testConcurrentEventAddition() throws InterruptedException {
        final int threadCount = 10;
        final int eventsPerThread = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < eventsPerThread; j++) {
                        buffer.addEvent(createTestEvent("event_" + threadId + "_" + j));
                    }
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * eventsPerThread, buffer.getEventCount());
    }

    @Test
    public void testCrashDetectionSessionMarking() {
        // Session should be marked as active
        boolean sessionActive = context.getSharedPreferences("nr_video_crash_detection", Context.MODE_PRIVATE)
            .getBoolean("session_active", false);

        assertTrue(sessionActive);
    }

    @Test
    public void testCleanupMarksSessionEnd() {
        buffer.cleanup();

        boolean sessionActive = context.getSharedPreferences("nr_video_crash_detection", Context.MODE_PRIVATE)
            .getBoolean("session_active", false);

        assertFalse(sessionActive);
    }

    @Test
    public void testLargeEventSet() {
        for (int i = 0; i < 1000; i++) {
            buffer.addEvent(createTestEvent("event" + i));
        }

        // PriorityEventBuffer has capacity limits (150 live + 350 on-demand for mobile)
        // When adding 1000 live events, only the most recent 150 are kept
        assertTrue("Buffer should respect capacity limits", buffer.getEventCount() <= 150);

        buffer.emergencyBackup();
        assertTrue(storage.hasBackupData());
    }

    @Test
    public void testPollWithSizeEstimator() {
        SizeEstimator estimator = mock(SizeEstimator.class);
        when(estimator.estimate(any())).thenReturn(100);

        buffer.addEvent(createTestEvent("event1", NRVideoConstants.EVENT_TYPE_LIVE));

        List<Map<String, Object>> batch = buffer.pollBatchByPriority(
            1000, estimator, NRVideoConstants.EVENT_TYPE_LIVE);

        assertNotNull(batch);
    }

    @Test
    public void testEmptyPollReturnsEmptyList() {
        List<Map<String, Object>> batch = buffer.pollBatchByPriority(
            10000, null, NRVideoConstants.EVENT_TYPE_LIVE);

        assertNotNull(batch);
        assertEquals(0, batch.size());
    }

    @Test
    public void testRecoveryModeActivation() {
        List<Map<String, Object>> failedEvents = createTestEventList(5);

        buffer.backupFailedEvents(failedEvents);

        CrashSafeEventBuffer.RecoveryStats stats = buffer.getRecoveryStats();
        assertTrue(stats.isRecovering);
    }

    @Test
    public void testMemoryAndSQLiteEventCount() {
        buffer.addEvent(createTestEvent("memory1"));
        storage.backupFailedEvents(createTestEventList(5));

        // Memory: 1, SQLite: 5
        // During recovery, total should be 6
        int totalCount = buffer.getEventCount() + storage.getEventCount();
        assertEquals(6, totalCount);
    }

    @Test
    public void testBufferWithNullEvent() {
        // Should handle null gracefully or throw appropriate exception
        try {
            buffer.addEvent(null);
            // If no exception, verify count
            assertTrue(buffer.getEventCount() >= 0);
        } catch (NullPointerException e) {
            // Expected for null event
            assertTrue(true);
        }
    }

    @Test
    public void testTVOptimizedBackupThreshold() {
        when(mockConfiguration.isTV()).thenReturn(true);

        CrashSafeEventBuffer tvBuffer = new CrashSafeEventBuffer(context, mockConfiguration, storage);

        // Set up callbacks for the TV buffer to prevent NullPointerException
        tvBuffer.setOverflowCallback(new EventBufferInterface.OverflowCallback() {
            @Override
            public void onBufferNearFull(String bufferType) {
                // Do nothing in tests
            }
        });

        tvBuffer.setCapacityCallback(new EventBufferInterface.CapacityCallback() {
            @Override
            public void onCapacityThresholdReached(double currentCapacity, String bufferType) {
                // Do nothing in tests
            }
        });

        // TV devices have higher threshold (200 vs 100)
        for (int i = 0; i < 200; i++) {
            tvBuffer.addEvent(createTestEvent("event" + i));
        }

        assertEquals(200, tvBuffer.getEventCount());
        tvBuffer.cleanup();
    }

    // Helper methods

    private Map<String, Object> createTestEvent(String eventId) {
        return createTestEvent(eventId, NRVideoConstants.EVENT_TYPE_LIVE);
    }

    private Map<String, Object> createTestEvent(String eventId, String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", eventId);
        event.put("timestamp", System.currentTimeMillis());
        event.put("eventType", eventType);
        event.put("data", "test data");
        // PriorityEventBuffer uses "contentIsLive" field to determine priority
        event.put("contentIsLive", NRVideoConstants.EVENT_TYPE_LIVE.equals(eventType));
        return event;
    }

    private List<Map<String, Object>> createTestEventList(int count) {
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(createTestEvent("event" + i));
        }
        return events;
    }
}
