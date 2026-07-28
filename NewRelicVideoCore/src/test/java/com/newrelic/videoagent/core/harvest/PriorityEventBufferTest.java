package com.newrelic.videoagent.core.harvest;

import com.newrelic.videoagent.core.NRVideoConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Comprehensive unit tests for PriorityEventBuffer.
 * Tests priority handling, capacity management, overflow callbacks, and platform-specific optimizations.
 */
public class PriorityEventBufferTest {

    private PriorityEventBuffer mobileBuffer;
    private PriorityEventBuffer tvBuffer;
    private SizeEstimator sizeEstimator;
    private TestOverflowCallback overflowCallback;
    private TestCapacityCallback capacityCallback;

    @Before
    public void setUp() {
        mobileBuffer = new PriorityEventBuffer(false);  // Mobile device
        tvBuffer = new PriorityEventBuffer(true);       // TV device
        sizeEstimator = new DefaultSizeEstimator();
        overflowCallback = new TestOverflowCallback();
        capacityCallback = new TestCapacityCallback();

        mobileBuffer.setOverflowCallback(overflowCallback);
        mobileBuffer.setCapacityCallback(capacityCallback);

        // Also set callbacks for TV buffer
        tvBuffer.setOverflowCallback(new TestOverflowCallback());
        tvBuffer.setCapacityCallback(new TestCapacityCallback());
    }

    @After
    public void tearDown() {
        if (mobileBuffer != null) {
            mobileBuffer.cleanup();
        }
        if (tvBuffer != null) {
            tvBuffer.cleanup();
        }
    }

    // ========== Priority Tests ==========

    @Test
    public void testLiveEventsPrioritizedOverOndemand() {
        // Add on-demand event first
        Map<String, Object> ondemandEvent = createOndemandEvent("ondemand1");
        mobileBuffer.addEvent(ondemandEvent);

        // Add live event
        Map<String, Object> liveEvent = createLiveEvent("live1");
        mobileBuffer.addEvent(liveEvent);

        // Poll live events first
        List<Map<String, Object>> liveBatch = mobileBuffer.pollBatchByPriority(
            64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );

        assertEquals("Live events should be polled first", 1, liveBatch.size());
        assertEquals("live1", liveBatch.get(0).get("actionName"));

        // Poll on-demand events
        List<Map<String, Object>> ondemandBatch = mobileBuffer.pollBatchByPriority(
            64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_ONDEMAND
        );

        assertEquals("On-demand events should remain", 1, ondemandBatch.size());
        assertEquals("ondemand1", ondemandBatch.get(0).get("actionName"));
    }

    @Test
    public void testLiveEventsPolledBeforeOndemandEvents() {
        // Add multiple events of each type
        for (int i = 0; i < 5; i++) {
            mobileBuffer.addEvent(createOndemandEvent("ondemand" + i));
            mobileBuffer.addEvent(createLiveEvent("live" + i));
        }

        // Poll live events first
        List<Map<String, Object>> liveBatch = mobileBuffer.pollBatchByPriority(
            64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );

        assertTrue("Should get live events", liveBatch.size() > 0);
        for (Map<String, Object> event : liveBatch) {
            assertTrue("Event should be live",
                      event.get("actionName").toString().startsWith("live"));
        }
    }

    @Test
    public void testOndemandEventsPreservedDuringLivePolling() {
        mobileBuffer.addEvent(createOndemandEvent("ondemand1"));
        mobileBuffer.addEvent(createLiveEvent("live1"));
        mobileBuffer.addEvent(createOndemandEvent("ondemand2"));

        // Poll live events
        List<Map<String, Object>> liveBatch = mobileBuffer.pollBatchByPriority(
            64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );

        assertEquals("Should get 1 live event", 1, liveBatch.size());

        // Verify on-demand events are still there
        List<Map<String, Object>> ondemandBatch = mobileBuffer.pollBatchByPriority(
            64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_ONDEMAND
        );

        assertEquals("Should have 2 on-demand events", 2, ondemandBatch.size());
    }

    @Test
    public void testPriorityPreservationWithLargeDataset() {
        // Add 100 events of each type
        for (int i = 0; i < 100; i++) {
            mobileBuffer.addEvent(createOndemandEvent("ondemand" + i));
            mobileBuffer.addEvent(createLiveEvent("live" + i));
        }

        // Poll all live events
        int liveCount = 0;
        while (true) {
            List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
                64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
            );
            if (batch.isEmpty()) break;
            liveCount += batch.size();
        }

        assertTrue("Should poll all live events", liveCount > 0);

        // Poll all on-demand events
        int ondemandCount = 0;
        while (true) {
            List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
                64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_ONDEMAND
            );
            if (batch.isEmpty()) break;
            ondemandCount += batch.size();
        }

        assertTrue("Should poll all on-demand events", ondemandCount > 0);
    }

    // ========== Capacity Management Tests ==========

    @Test
    public void testMobileBufferCapacity() {
        // Mobile has smaller capacity (150 live, 350 on-demand)
        for (int i = 0; i < 200; i++) {
            mobileBuffer.addEvent(createLiveEvent("live" + i));
        }

        int eventCount = mobileBuffer.getEventCount();
        assertTrue("Mobile buffer should limit live events to ~150",
                  eventCount <= 150);
    }

    @Test
    public void testTVBufferCapacity() {
        // TV has larger capacity (300 live, 700 on-demand)
        for (int i = 0; i < 400; i++) {
            tvBuffer.addEvent(createLiveEvent("live" + i));
        }

        int eventCount = tvBuffer.getEventCount();
        assertTrue("TV buffer should allow more events",
                  eventCount > 150);
        assertTrue("TV buffer should limit live events to ~300",
                  eventCount <= 300);
    }

    @Test
    public void testBufferOverflowEvictsOldestEvents() {
        // Add more events than capacity
        for (int i = 0; i < 200; i++) {
            mobileBuffer.addEvent(createLiveEvent("live" + i));
        }

        // Poll events and check that oldest were evicted
        List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
            1024 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );

        // Should not contain oldest events (live0, live1, etc.)
        boolean hasOldestEvent = false;
        for (Map<String, Object> event : batch) {
            if ("live0".equals(event.get("actionName"))) {
                hasOldestEvent = true;
                break;
            }
        }

        assertFalse("Oldest events should be evicted", hasOldestEvent);
    }

    @Test
    public void testCapacityForBothQueues() {
        // Fill both queues
        for (int i = 0; i < 500; i++) {
            mobileBuffer.addEvent(createLiveEvent("live" + i));
            mobileBuffer.addEvent(createOndemandEvent("ondemand" + i));
        }

        int totalCount = mobileBuffer.getEventCount();

        // Mobile: 150 live + 350 on-demand = 500 max
        assertTrue("Total capacity should be enforced", totalCount <= 500);
    }

    @Test
    public void testOverflowCallbackTriggeredAt90Percent() {
        // Add events until 90% capacity
        int liveCapacity = 150; // Mobile capacity
        int targetCount = (int) (liveCapacity * 0.9);

        // Add events just below 90%
        for (int i = 0; i < targetCount - 1; i++) {
            mobileBuffer.addEvent(createLiveEvent("live" + i));
        }

        // Reset the overflow callback to check if it gets triggered at 90%
        overflowCallback.reset();

        // Add one more to reach 90%
        mobileBuffer.addEvent(createLiveEvent("live" + (targetCount - 1)));

        assertTrue("Overflow callback should be triggered at 90%",
                  overflowCallback.wasTriggered());
        assertEquals("Should indicate live buffer type",
                    NRVideoConstants.EVENT_TYPE_LIVE,
                    overflowCallback.getBufferType());
    }

    @Test
    public void testCapacityCallbackOnFirstEvent() {
        capacityCallback.reset();

        // Add first event
        mobileBuffer.addEvent(createLiveEvent("live1"));

        assertTrue("Capacity callback should be triggered on first event",
                  capacityCallback.wasTriggered());
        assertEquals("Should indicate live buffer type",
                    NRVideoConstants.EVENT_TYPE_LIVE,
                    capacityCallback.getBufferType());
    }

    // ========== Batch Operations Tests ==========

    @Test
    public void testPollBatchRespectsSizeLimit() {
        // Add multiple events
        for (int i = 0; i < 20; i++) {
            mobileBuffer.addEvent(createLiveEvent("live" + i));
        }

        // Poll with small size limit
        int maxSize = 4096; // 4KB
        List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
            maxSize, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );

        // Calculate actual batch size
        int batchSize = 0;
        for (Map<String, Object> event : batch) {
            batchSize += sizeEstimator.estimate(event);
        }

        assertTrue("Batch size should not exceed limit",
                  batchSize <= maxSize || batch.size() == 1);
    }

    @Test
    public void testPollBatchReturnsEmptyForEmptyQueue() {
        List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
            64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );

        assertTrue("Should return empty list for empty queue", batch.isEmpty());
    }

    @Test
    public void testPollBatchWithNullSizeEstimator() {
        mobileBuffer.addEvent(createLiveEvent("live1"));

        // Poll without size estimator (uses default estimation)
        List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
            64 * 1024, null, NRVideoConstants.EVENT_TYPE_LIVE
        );

        assertEquals("Should still poll events", 1, batch.size());
    }

    @Test
    public void testMultipleBatchPolling() {
        // Add 50 events
        for (int i = 0; i < 50; i++) {
            mobileBuffer.addEvent(createLiveEvent("live" + i));
        }

        // Poll in multiple batches
        List<Map<String, Object>> batch1 = mobileBuffer.pollBatchByPriority(
            8192, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );
        List<Map<String, Object>> batch2 = mobileBuffer.pollBatchByPriority(
            8192, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );

        assertTrue("First batch should have events", batch1.size() > 0);
        assertTrue("Second batch should have events", batch2.size() > 0);

        // Verify no duplicates
        String firstEventInBatch1 = (String) batch1.get(0).get("actionName");
        String firstEventInBatch2 = (String) batch2.get(0).get("actionName");
        assertNotEquals("Batches should not contain same events",
                       firstEventInBatch1, firstEventInBatch2);
    }

    @Test
    public void testTVDeviceHasLargerBatchSize() {
        // Add events to both buffers
        for (int i = 0; i < 50; i++) {
            mobileBuffer.addEvent(createLiveEvent("live" + i));
            tvBuffer.addEvent(createLiveEvent("live" + i));
        }

        List<Map<String, Object>> mobileBatch = mobileBuffer.pollBatchByPriority(
            1024 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );
        List<Map<String, Object>> tvBatch = tvBuffer.pollBatchByPriority(
            1024 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
        );

        assertTrue("TV should allow larger batches",
                  tvBatch.size() >= mobileBatch.size());
    }

    // ========== Edge Cases and State Management Tests ==========

    @Test
    public void testAddNullEvent() {
        mobileBuffer.addEvent(null);

        assertTrue("Buffer should remain empty after null event",
                  mobileBuffer.isEmpty());
    }

    @Test
    public void testIsEmptyAfterAddingEvents() {
        assertTrue("Buffer should start empty", mobileBuffer.isEmpty());

        mobileBuffer.addEvent(createLiveEvent("live1"));

        assertFalse("Buffer should not be empty after adding event",
                   mobileBuffer.isEmpty());
    }

    @Test
    public void testGetEventCount() {
        assertEquals("Initial count should be 0", 0, mobileBuffer.getEventCount());

        mobileBuffer.addEvent(createLiveEvent("live1"));
        mobileBuffer.addEvent(createOndemandEvent("ondemand1"));

        assertEquals("Count should include both queues", 2, mobileBuffer.getEventCount());
    }

    @Test
    public void testGetEventCountAfterPolling() {
        for (int i = 0; i < 10; i++) {
            mobileBuffer.addEvent(createLiveEvent("live" + i));
        }

        int initialCount = mobileBuffer.getEventCount();

        mobileBuffer.pollBatchByPriority(64 * 1024, sizeEstimator,
                                        NRVideoConstants.EVENT_TYPE_LIVE);

        int afterCount = mobileBuffer.getEventCount();

        assertTrue("Count should decrease after polling", afterCount < initialCount);
    }

    @Test
    public void testClearResetsBuffer() {
        mobileBuffer.addEvent(createLiveEvent("live1"));
        mobileBuffer.addEvent(createOndemandEvent("ondemand1"));

        assertFalse("Buffer should have events", mobileBuffer.isEmpty());

        mobileBuffer.clear();

        assertTrue("Buffer should be empty after clear", mobileBuffer.isEmpty());
        assertEquals("Event count should be 0 after clear", 0,
                    mobileBuffer.getEventCount());
    }

    @Test
    public void testCleanupResetsBuffer() {
        mobileBuffer.addEvent(createLiveEvent("live1"));

        mobileBuffer.cleanup();

        assertTrue("Buffer should be empty after cleanup", mobileBuffer.isEmpty());
    }

    @Test
    public void testEventWithoutLiveMarkerIsOndemand() {
        Map<String, Object> event = new HashMap<>();
        event.put("actionName", "CONTENT_START");
        // No contentIsLive marker

        mobileBuffer.addEvent(event);

        // Poll on-demand queue
        List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
            64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_ONDEMAND
        );

        assertEquals("Event without live marker should be on-demand",
                    1, batch.size());
    }

    @Test
    public void testEventWithExplicitLiveFalseIsOndemand() {
        Map<String, Object> event = new HashMap<>();
        event.put("actionName", "CONTENT_START");
        event.put("contentIsLive", false);

        mobileBuffer.addEvent(event);

        // Poll on-demand queue
        List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
            64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_ONDEMAND
        );

        assertEquals("Event with live=false should be on-demand",
                    1, batch.size());
    }

    // ========== Concurrency Tests ==========

    @Test
    public void testConcurrentAddingEvents() throws InterruptedException {
        int threadCount = 10;
        int eventsPerThread = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    mobileBuffer.addEvent(createLiveEvent("thread" + threadId + "_event" + j));
                }
                latch.countDown();
            }).start();
        }

        assertTrue("All threads should complete",
                  latch.await(5, TimeUnit.SECONDS));

        int totalEvents = mobileBuffer.getEventCount();
        assertTrue("Should have events from concurrent threads", totalEvents > 0);
        assertTrue("Should respect capacity limits", totalEvents <= 150);
    }

    @Test
    public void testConcurrentPolling() throws InterruptedException {
        // Add events
        for (int i = 0; i < 100; i++) {
            mobileBuffer.addEvent(createLiveEvent("live" + i));
        }

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalPolled = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
                    64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
                );
                totalPolled.addAndGet(batch.size());
                latch.countDown();
            }).start();
        }

        assertTrue("All threads should complete",
                  latch.await(5, TimeUnit.SECONDS));

        assertTrue("Should poll events from concurrent threads",
                  totalPolled.get() > 0);
    }

    @Test
    public void testConcurrentAddAndPoll() throws InterruptedException {
        int duration = 2000; // 2 seconds
        AtomicInteger addCount = new AtomicInteger(0);
        AtomicInteger pollCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        // Adding thread
        Thread addThread = new Thread(() -> {
            long endTime = System.currentTimeMillis() + duration;
            while (System.currentTimeMillis() < endTime) {
                mobileBuffer.addEvent(createLiveEvent("live" + addCount.incrementAndGet()));
                try { Thread.sleep(10); } catch (InterruptedException e) { break; }
            }
            latch.countDown();
        });

        // Polling thread
        Thread pollThread = new Thread(() -> {
            long endTime = System.currentTimeMillis() + duration;
            while (System.currentTimeMillis() < endTime) {
                List<Map<String, Object>> batch = mobileBuffer.pollBatchByPriority(
                    64 * 1024, sizeEstimator, NRVideoConstants.EVENT_TYPE_LIVE
                );
                pollCount.addAndGet(batch.size());
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
            latch.countDown();
        });

        addThread.start();
        pollThread.start();

        assertTrue("Both threads should complete",
                  latch.await(5, TimeUnit.SECONDS));

        assertTrue("Should add events", addCount.get() > 0);
        assertTrue("Should poll events", pollCount.get() > 0);
    }

    // ========== Helper Methods ==========

    private Map<String, Object> createLiveEvent(String actionName) {
        Map<String, Object> event = new HashMap<>();
        event.put("actionName", actionName);
        event.put("contentIsLive", true);
        event.put("timestamp", System.currentTimeMillis());
        event.put("eventType", "VIDEO");
        return event;
    }

    private Map<String, Object> createOndemandEvent(String actionName) {
        Map<String, Object> event = new HashMap<>();
        event.put("actionName", actionName);
        event.put("contentIsLive", false);
        event.put("timestamp", System.currentTimeMillis());
        event.put("eventType", "VIDEO");
        return event;
    }

    // ========== Test Callback Implementations ==========

    private static class TestOverflowCallback implements EventBufferInterface.OverflowCallback {
        private boolean triggered = false;
        private String bufferType = null;

        @Override
        public void onBufferNearFull(String bufferType) {
            this.triggered = true;
            this.bufferType = bufferType;
        }

        public boolean wasTriggered() {
            return triggered;
        }

        public String getBufferType() {
            return bufferType;
        }

        public void reset() {
            triggered = false;
            bufferType = null;
        }
    }

    private static class TestCapacityCallback implements EventBufferInterface.CapacityCallback {
        private boolean triggered = false;
        private double capacity = 0.0;
        private String bufferType = null;

        @Override
        public void onCapacityThresholdReached(double currentCapacity, String bufferType) {
            this.triggered = true;
            this.capacity = currentCapacity;
            this.bufferType = bufferType;
        }

        public boolean wasTriggered() {
            return triggered;
        }

        public double getCapacity() {
            return capacity;
        }

        public String getBufferType() {
            return bufferType;
        }

        public void reset() {
            triggered = false;
            capacity = 0.0;
            bufferType = null;
        }
    }
}
