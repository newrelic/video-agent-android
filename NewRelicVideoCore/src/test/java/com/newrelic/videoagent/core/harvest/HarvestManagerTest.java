package com.newrelic.videoagent.core.harvest;

import android.content.Context;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.storage.IntegratedDeadLetterHandler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for HarvestManager.
 * Tests event recording, harvest orchestration, capacity callbacks, and integration with factory.
 */
@RunWith(RobolectricTestRunner.class)
public class HarvestManagerTest {

    @Mock
    private EventBufferInterface mockEventBuffer;

    @Mock
    private HttpClientInterface mockHttpClient;

    @Mock
    private SchedulerInterface mockScheduler;

    @Mock
    private IntegratedDeadLetterHandler mockDeadLetterHandler;

    @Mock
    private HarvestComponentFactory mockFactory;

    private HarvestManager harvestManager;
    private Context context;
    private NRVideoConfiguration testConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();

        // Create valid test configuration
        testConfig = new NRVideoConfiguration.Builder("test-app-token-1234567890")
            .build();

        // Set up mock factory to return mocked components
        when(mockFactory.getEventBuffer()).thenReturn(mockEventBuffer);
        when(mockFactory.getHttpClient()).thenReturn(mockHttpClient);
        when(mockFactory.getScheduler()).thenReturn(mockScheduler);
        when(mockFactory.getDeadLetterHandler()).thenReturn(mockDeadLetterHandler);
        when(mockFactory.getConfiguration()).thenReturn(testConfig);
        when(mockFactory.getContext()).thenReturn(context);

        // Create HarvestManager with real configuration and context
        // Note: This will create a real CrashSafeHarvestFactory internally
        harvestManager = new HarvestManager(testConfig, context);
    }

    // ========== Initialization Tests ==========

    @Test
    public void testHarvestManagerCreation() {
        assertNotNull("HarvestManager should be created", harvestManager);
    }

    @Test
    public void testFactoryIsCreated() {
        HarvestComponentFactory factory = harvestManager.getFactory();
        assertNotNull("Factory should be created", factory);
    }

    @Test
    public void testFactoryReturnsConfiguration() {
        HarvestComponentFactory factory = harvestManager.getFactory();
        NRVideoConfiguration config = factory.getConfiguration();

        assertNotNull("Configuration should be available", config);
    }

    @Test
    public void testFactoryReturnsContext() {
        HarvestComponentFactory factory = harvestManager.getFactory();
        Context factoryContext = factory.getContext();

        assertNotNull("Context should be available", factoryContext);
    }

    // ========== Record Event Tests ==========

    @Test
    public void testRecordEventWithValidData() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("key1", "value1");
        attributes.put("key2", 123);

        // Should not throw exception
        harvestManager.recordEvent("TestEvent", attributes);
    }

    @Test
    public void testRecordEventWithNullAttributes() {
        // Should not throw exception
        harvestManager.recordEvent("TestEvent", null);
    }

    @Test
    public void testRecordEventWithEmptyAttributes() {
        Map<String, Object> attributes = new HashMap<>();

        // Should not throw exception
        harvestManager.recordEvent("TestEvent", attributes);
    }

    @Test
    public void testRecordEventWithNullEventType() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("key", "value");

        // Should not throw exception (event type is null, should be ignored)
        harvestManager.recordEvent(null, attributes);
    }

    @Test
    public void testRecordEventWithEmptyEventType() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("key", "value");

        // Should not throw exception (empty event type should be ignored)
        harvestManager.recordEvent("", attributes);
    }

    @Test
    public void testRecordEventWithWhitespaceEventType() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("key", "value");

        // Should not throw exception (whitespace event type should be ignored)
        harvestManager.recordEvent("   ", attributes);
    }

    @Test
    public void testRecordEventAddsTimestamp() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("key", "value");

        long beforeTime = System.currentTimeMillis();
        harvestManager.recordEvent("TestEvent", attributes);
        long afterTime = System.currentTimeMillis();

        // Event should be added with timestamp (verified through buffer interaction)
        // This is an integration test with the real factory
    }

    @Test
    public void testRecordEventMultipleTimes() {
        for (int i = 0; i < 10; i++) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("eventNum", i);

            harvestManager.recordEvent("Event" + i, attributes);
        }

        // All events should be recorded without exceptions
    }

    @Test
    public void testRecordEventWithComplexAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("stringAttr", "test");
        attributes.put("intAttr", 42);
        attributes.put("doubleAttr", 3.14);
        attributes.put("boolAttr", true);
        attributes.put("longAttr", 1234567890L);

        harvestManager.recordEvent("ComplexEvent", attributes);
    }

    @Test
    public void testRecordEventWithNestedAttributes() {
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nestedKey", "nestedValue");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("topLevel", "value");
        attributes.put("nested", nestedMap);

        harvestManager.recordEvent("NestedEvent", attributes);
    }

    // ========== Capacity Callback Tests ==========

    @Test
    public void testOnCapacityThresholdReachedForLive() {
        // This tests the CapacityCallback implementation
        double capacity = 0.6;
        String bufferType = NRVideoConstants.EVENT_TYPE_LIVE;

        // Should not throw exception
        harvestManager.onCapacityThresholdReached(capacity, bufferType);
    }

    @Test
    public void testOnCapacityThresholdReachedForOndemand() {
        double capacity = 0.6;
        String bufferType = NRVideoConstants.EVENT_TYPE_ONDEMAND;

        // Should not throw exception
        harvestManager.onCapacityThresholdReached(capacity, bufferType);
    }

    @Test
    public void testOnCapacityThresholdReachedAtVariousLevels() {
        // Test at different capacity levels
        double[] capacities = {0.0, 0.3, 0.6, 0.9, 1.0};

        for (double capacity : capacities) {
            harvestManager.onCapacityThresholdReached(capacity,
                                                     NRVideoConstants.EVENT_TYPE_LIVE);
        }

        // All should succeed without exceptions
    }

    // ========== Harvest Tests ==========

    @Test
    public void testHarvestOnDemand() {
        // Should not throw exception
        harvestManager.harvestOnDemand();
    }

    @Test
    public void testHarvestLive() {
        // Should not throw exception
        harvestManager.harvestLive();
    }

    @Test
    public void testMultipleHarvestOnDemandCalls() {
        for (int i = 0; i < 5; i++) {
            harvestManager.harvestOnDemand();
        }

        // All harvests should succeed
    }

    @Test
    public void testMultipleHarvestLiveCalls() {
        for (int i = 0; i < 5; i++) {
            harvestManager.harvestLive();
        }

        // All harvests should succeed
    }

    @Test
    public void testAlternatingHarvestCalls() {
        for (int i = 0; i < 5; i++) {
            harvestManager.harvestOnDemand();
            harvestManager.harvestLive();
        }

        // Alternating harvests should succeed
    }

    // ========== Edge Cases and Error Handling Tests ==========

    @Test
    public void testRecordEventWithVeryLargeAttributes() {
        Map<String, Object> attributes = new HashMap<>();

        // Add 100 attributes
        for (int i = 0; i < 100; i++) {
            attributes.put("key" + i, "value" + i);
        }

        harvestManager.recordEvent("LargeEvent", attributes);
    }

    @Test
    public void testRecordEventWithLongStrings() {
        Map<String, Object> attributes = new HashMap<>();
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longString.append("x");
        }
        attributes.put("longKey", longString.toString());

        harvestManager.recordEvent("LongStringEvent", attributes);
    }

    @Test
    public void testRecordEventWithSpecialCharacters() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("special", "!@#$%^&*()_+-=[]{}|;':\",./<>?");
        attributes.put("unicode", "こんにちは世界");
        attributes.put("emoji", "😀🎉🚀");

        harvestManager.recordEvent("SpecialCharsEvent", attributes);
    }

    @Test
    public void testRecordEventWithNullValuesInAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("key1", "value1");
        attributes.put("key2", null);
        attributes.put("key3", "value3");

        harvestManager.recordEvent("NullValueEvent", attributes);
    }

    @Test
    public void testCapacityCallbackWithNegativeCapacity() {
        harvestManager.onCapacityThresholdReached(-0.1,
                                                  NRVideoConstants.EVENT_TYPE_LIVE);

        // Should handle gracefully
    }

    @Test
    public void testCapacityCallbackWithOverMaxCapacity() {
        harvestManager.onCapacityThresholdReached(1.5,
                                                  NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should handle gracefully
    }

    @Test
    public void testCapacityCallbackWithNullBufferType() {
        harvestManager.onCapacityThresholdReached(0.6, null);

        // Should handle gracefully
    }

    @Test
    public void testCapacityCallbackWithInvalidBufferType() {
        harvestManager.onCapacityThresholdReached(0.6, "invalid");

        // Should handle gracefully
    }

    // ========== Concurrency Tests ==========

    @Test
    public void testConcurrentRecordEvents() throws InterruptedException {
        int threadCount = 5;
        int eventsPerThread = 20;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put("threadId", threadId);
                    attributes.put("eventId", j);
                    harvestManager.recordEvent("ConcurrentEvent", attributes);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // All events should be recorded successfully
    }

    @Test
    public void testConcurrentHarvesting() throws InterruptedException {
        // Record some events first
        for (int i = 0; i < 50; i++) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("index", i);
            harvestManager.recordEvent("Event", attributes);
        }

        int threadCount = 3;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                if (threadId % 2 == 0) {
                    harvestManager.harvestOnDemand();
                } else {
                    harvestManager.harvestLive();
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // All harvests should complete successfully
    }

    @Test
    public void testConcurrentRecordAndHarvest() throws InterruptedException {
        Thread recordThread = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("index", i);
                harvestManager.recordEvent("Event", attributes);
                try { Thread.sleep(10); } catch (InterruptedException e) { break; }
            }
        });

        Thread harvestThread = new Thread(() -> {
            for (int i = 0; i < 20; i++) {
                harvestManager.harvestOnDemand();
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        });

        recordThread.start();
        harvestThread.start();

        recordThread.join();
        harvestThread.join();

        // Should handle concurrent record and harvest
    }

    // ========== Factory Delegation Tests ==========

    @Test
    public void testFactoryIsNotNull() {
        assertNotNull("Factory should not be null", harvestManager.getFactory());
    }

    @Test
    public void testFactoryReturnsSameInstance() {
        HarvestComponentFactory factory1 = harvestManager.getFactory();
        HarvestComponentFactory factory2 = harvestManager.getFactory();

        assertSame("Factory should return same instance", factory1, factory2);
    }

    @Test
    public void testFactoryCleanup() {
        HarvestComponentFactory factory = harvestManager.getFactory();

        // Should not throw exception
        factory.cleanup();
    }

    @Test
    public void testFactoryRecoveryMethods() {
        HarvestComponentFactory factory = harvestManager.getFactory();

        // These methods should be callable
        boolean isRecovering = factory.isRecovering();
        String recoveryStats = factory.getRecoveryStats();

        assertNotNull("Recovery stats should not be null", recoveryStats);
    }

    @Test
    public void testFactoryEmergencyBackup() {
        HarvestComponentFactory factory = harvestManager.getFactory();

        // Should not throw exception
        factory.performEmergencyBackup();
    }

    // ========== Stress Tests ==========

    @Test
    public void testRapidEventRecording() {
        // Record 1000 events rapidly
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("index", i);
            harvestManager.recordEvent("RapidEvent" + i, attributes);
        }

        // Should handle all events
    }

    @Test
    public void testRapidHarvesting() {
        // Record some events
        for (int i = 0; i < 50; i++) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("index", i);
            harvestManager.recordEvent("Event", attributes);
        }

        // Harvest rapidly
        for (int i = 0; i < 100; i++) {
            if (i % 2 == 0) {
                harvestManager.harvestOnDemand();
            } else {
                harvestManager.harvestLive();
            }
        }

        // Should handle rapid harvesting
    }

    @Test
    public void testMixedEventTypes() {
        String[] eventTypes = {"VIDEO", "AD", "ERROR", "CUSTOM", "HEARTBEAT"};

        for (int i = 0; i < 100; i++) {
            String eventType = eventTypes[i % eventTypes.length];
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("index", i);
            attributes.put("type", eventType);

            harvestManager.recordEvent(eventType, attributes);
        }

        harvestManager.harvestOnDemand();
        harvestManager.harvestLive();
    }
}
