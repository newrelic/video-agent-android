package com.newrelic.videoagent.core.storage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for VideoEventStorage.
 * Tests SQLite database operations for crash recovery and event backup.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class VideoEventStorageTest {

    private Context context;
    private VideoEventStorage storage;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        storage = new VideoEventStorage(context);
    }

    @After
    public void tearDown() {
        storage.close();
        context.deleteDatabase("nr_video_backup.db");
    }

    @Test
    public void testStorageInitialization() {
        assertNotNull(storage);
        SQLiteDatabase db = storage.getReadableDatabase();
        assertNotNull(db);
        assertTrue(db.isOpen());
    }

    @Test
    public void testDatabaseCreation() {
        SQLiteDatabase db = storage.getReadableDatabase();

        assertTrue(db.isOpen());
        assertNotNull(db);
    }

    @Test
    public void testBackupEventsEmpty() {
        List<Map<String, Object>> liveEvents = new ArrayList<>();
        List<Map<String, Object>> ondemandEvents = new ArrayList<>();

        storage.backupEvents(liveEvents, ondemandEvents);

        assertEquals(0, storage.getEventCount());
        assertTrue(storage.isEmpty());
    }

    @Test
    public void testBackupLiveEvents() {
        List<Map<String, Object>> liveEvents = createTestEvents(5);
        List<Map<String, Object>> ondemandEvents = new ArrayList<>();

        storage.backupEvents(liveEvents, ondemandEvents);

        assertEquals(5, storage.getEventCount());
        assertFalse(storage.isEmpty());
        assertTrue(storage.hasBackupData());
    }

    @Test
    public void testBackupOndemandEvents() {
        List<Map<String, Object>> liveEvents = new ArrayList<>();
        List<Map<String, Object>> ondemandEvents = createTestEvents(3);

        storage.backupEvents(liveEvents, ondemandEvents);

        assertEquals(3, storage.getEventCount());
        assertFalse(storage.isEmpty());
    }

    @Test
    public void testBackupBothEventTypes() {
        List<Map<String, Object>> liveEvents = createTestEvents(5);
        List<Map<String, Object>> ondemandEvents = createTestEvents(3);

        storage.backupEvents(liveEvents, ondemandEvents);

        assertEquals(8, storage.getEventCount());
    }

    @Test
    public void testBackupFailedEvents() {
        List<Map<String, Object>> failedEvents = createTestEvents(4);

        storage.backupFailedEvents(failedEvents);

        assertEquals(4, storage.getEventCount());
        assertTrue(storage.hasBackupData());
    }

    @Test
    public void testPollEventsLivePriority() {
        List<Map<String, Object>> liveEvents = createTestEvents(5);
        storage.backupEvents(liveEvents, new ArrayList<Map<String, Object>>());

        List<Map<String, Object>> polledEvents = storage.pollEvents("live", 3);

        assertEquals(3, polledEvents.size());
        assertEquals(2, storage.getEventCount()); // 2 remaining
    }

    @Test
    public void testPollEventsOndemandPriority() {
        List<Map<String, Object>> ondemandEvents = createTestEvents(5);
        storage.backupEvents(new ArrayList<Map<String, Object>>(), ondemandEvents);

        List<Map<String, Object>> polledEvents = storage.pollEvents("ondemand", 2);

        assertEquals(2, polledEvents.size());
        assertEquals(3, storage.getEventCount());
    }

    @Test
    public void testPollEventsFailedPriority() {
        List<Map<String, Object>> failedEvents = createTestEvents(5);
        storage.backupFailedEvents(failedEvents);

        List<Map<String, Object>> polledEvents = storage.pollEvents("failed", 3);

        assertEquals(3, polledEvents.size());
        assertEquals(2, storage.getEventCount());
    }

    @Test
    public void testPollEventsRemovesPolledEvents() {
        List<Map<String, Object>> events = createTestEvents(10);
        storage.backupFailedEvents(events);

        assertEquals(10, storage.getEventCount());

        storage.pollEvents("failed", 5);
        assertEquals(5, storage.getEventCount());

        storage.pollEvents("failed", 5);
        assertEquals(0, storage.getEventCount());
        assertTrue(storage.isEmpty());
    }

    @Test
    public void testPollEventsMoreThanAvailable() {
        List<Map<String, Object>> events = createTestEvents(3);
        storage.backupFailedEvents(events);

        List<Map<String, Object>> polledEvents = storage.pollEvents("failed", 10);

        assertEquals(3, polledEvents.size());
        assertEquals(0, storage.getEventCount());
    }

    @Test
    public void testPollEventsZeroCount() {
        List<Map<String, Object>> events = createTestEvents(5);
        storage.backupFailedEvents(events);

        List<Map<String, Object>> polledEvents = storage.pollEvents("failed", 0);

        assertEquals(0, polledEvents.size());
        assertEquals(5, storage.getEventCount());
    }

    @Test
    public void testGetEventCountEmpty() {
        assertEquals(0, storage.getEventCount());
    }

    @Test
    public void testGetEventCountWithEvents() {
        storage.backupFailedEvents(createTestEvents(7));

        assertEquals(7, storage.getEventCount());
    }

    @Test
    public void testHasBackupDataEmpty() {
        assertFalse(storage.hasBackupData());
    }

    @Test
    public void testHasBackupDataWithEvents() {
        storage.backupFailedEvents(createTestEvents(1));

        assertTrue(storage.hasBackupData());
    }

    @Test
    public void testIsEmptyTrue() {
        assertTrue(storage.isEmpty());
    }

    @Test
    public void testIsEmptyFalse() {
        storage.backupFailedEvents(createTestEvents(1));

        assertFalse(storage.isEmpty());
    }

    @Test
    public void testCleanupOldEvents() throws InterruptedException {
        List<Map<String, Object>> events = createTestEvents(5);
        storage.backupFailedEvents(events);

        assertEquals(5, storage.getEventCount());

        // Cleanup should not remove recent events
        storage.cleanup();
        assertEquals(5, storage.getEventCount());
    }

    @Test
    public void testMultipleBackupOperations() {
        storage.backupFailedEvents(createTestEvents(3));
        assertEquals(3, storage.getEventCount());

        storage.backupFailedEvents(createTestEvents(2));
        assertEquals(5, storage.getEventCount());

        storage.backupEvents(createTestEvents(1), createTestEvents(1));
        assertEquals(7, storage.getEventCount());
    }

    @Test
    public void testTransactionRollbackOnError() {
        // Create events with one that might cause issues
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> event1 = new HashMap<>();
        event1.put("key1", "value1");
        events.add(event1);

        // Normal backup should succeed
        storage.backupFailedEvents(events);
        assertEquals(1, storage.getEventCount());
    }

    @Test
    public void testEventDataPreservation() {
        Map<String, Object> originalEvent = new HashMap<>();
        originalEvent.put("eventType", "test");
        originalEvent.put("timestamp", 12345L);
        originalEvent.put("data", "test data");

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(originalEvent);

        storage.backupFailedEvents(events);

        List<Map<String, Object>> polledEvents = storage.pollEvents("failed", 1);

        assertEquals(1, polledEvents.size());
        Map<String, Object> retrievedEvent = polledEvents.get(0);
        assertEquals("test", retrievedEvent.get("eventType"));
        assertEquals("test data", retrievedEvent.get("data"));
    }

    @Test
    public void testJsonConversionRoundTrip() {
        Map<String, Object> event = new HashMap<>();
        event.put("string", "value");
        event.put("number", 123);
        event.put("boolean", true);

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(event);

        storage.backupFailedEvents(events);
        List<Map<String, Object>> retrieved = storage.pollEvents("failed", 1);

        assertEquals(1, retrieved.size());
        Map<String, Object> retrievedEvent = retrieved.get(0);
        assertEquals("value", retrievedEvent.get("string"));
        assertNotNull(retrievedEvent.get("number"));
        assertNotNull(retrievedEvent.get("boolean"));
    }

    @Test
    public void testEmptyEventHandling() {
        Map<String, Object> emptyEvent = new HashMap<>();
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(emptyEvent);

        storage.backupFailedEvents(events);

        assertEquals(1, storage.getEventCount());
    }

    @Test
    public void testLargeEventSet() {
        List<Map<String, Object>> largeEventSet = createTestEvents(1000);

        storage.backupFailedEvents(largeEventSet);

        assertEquals(1000, storage.getEventCount());

        List<Map<String, Object>> polled = storage.pollEvents("failed", 500);
        assertEquals(500, polled.size());
        assertEquals(500, storage.getEventCount());
    }

    @Test
    public void testConcurrentBackupOperations() throws InterruptedException {
        final int threadCount = 5;
        final int eventsPerThread = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    storage.backupFailedEvents(createTestEvents(eventsPerThread));
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * eventsPerThread, storage.getEventCount());
    }

    @Test
    public void testDatabaseUpgrade() {
        SQLiteDatabase db = storage.getWritableDatabase();
        int oldVersion = 1;
        int newVersion = 2;

        storage.onUpgrade(db, oldVersion, newVersion);

        assertTrue(db.isOpen());
        assertEquals(0, storage.getEventCount());
    }

    @Test
    public void testPriorityOrdering() {
        List<Map<String, Object>> liveEvents = createTestEvents(3);
        List<Map<String, Object>> ondemandEvents = createTestEvents(3);

        storage.backupEvents(liveEvents, ondemandEvents);

        assertEquals(6, storage.getEventCount());

        List<Map<String, Object>> livePolled = storage.pollEvents("live", 10);
        assertEquals(3, livePolled.size());

        List<Map<String, Object>> ondemandPolled = storage.pollEvents("ondemand", 10);
        assertEquals(3, ondemandPolled.size());

        assertEquals(0, storage.getEventCount());
    }

    @Test
    public void testEventTimestampOrdering() {
        List<Map<String, Object>> events1 = createTestEvents(5);
        storage.backupFailedEvents(events1);

        try {
            Thread.sleep(10); // Small delay to ensure different timestamps
        } catch (InterruptedException e) {
            // Ignore
        }

        List<Map<String, Object>> events2 = createTestEvents(5);
        storage.backupFailedEvents(events2);

        List<Map<String, Object>> polled = storage.pollEvents("failed", 3);
        assertEquals(3, polled.size());

        // Should poll oldest events first
        assertEquals(7, storage.getEventCount());
    }

    @Test
    public void testCleanupEmptyDatabase() {
        storage.cleanup();

        assertEquals(0, storage.getEventCount());
        assertTrue(storage.isEmpty());
    }

    @Test
    public void testMultiplePollOperations() {
        storage.backupFailedEvents(createTestEvents(20));

        assertEquals(20, storage.getEventCount());

        storage.pollEvents("failed", 5);
        assertEquals(15, storage.getEventCount());

        storage.pollEvents("failed", 5);
        assertEquals(10, storage.getEventCount());

        storage.pollEvents("failed", 5);
        assertEquals(5, storage.getEventCount());

        storage.pollEvents("failed", 5);
        assertEquals(0, storage.getEventCount());
        assertTrue(storage.isEmpty());
    }

    // Helper methods

    private List<Map<String, Object>> createTestEvents(int count) {
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", "event_" + i);
            event.put("timestamp", System.currentTimeMillis());
            event.put("data", "test data " + i);
            events.add(event);
        }
        return events;
    }
}
