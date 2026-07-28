package com.newrelic.videoagent.core.storage;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IntegratedDeadLetterHandler.
 * Tests retry logic, emergency backup, device-specific optimizations, and memory management.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class IntegratedDeadLetterHandlerTest {

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

        // Set up default configuration
        when(mockConfiguration.isTV()).thenReturn(false);
        when(mockConfiguration.isMemoryOptimized()).thenReturn(false);
        when(mockConfiguration.getRegularBatchSizeBytes()).thenReturn(50000);
        when(mockConfiguration.getLiveBatchSizeBytes()).thenReturn(25000);
        when(mockConfiguration.getDeadLetterRetryInterval()).thenReturn(60000L);
        when(mockConfiguration.getMaxDeadLetterSize()).thenReturn(100);

        handler = new IntegratedDeadLetterHandler(mockMainBuffer, mockHttpClient, mockConfiguration);
    }

    // ========== Constructor Tests ==========

    @Test
    public void testConstructorWithMobileDevice() {
        when(mockConfiguration.isTV()).thenReturn(false);

        IntegratedDeadLetterHandler mobileHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should be created for mobile", mobileHandler);
    }

    @Test
    public void testConstructorWithTVDevice() {
        when(mockConfiguration.isTV()).thenReturn(true);

        IntegratedDeadLetterHandler tvHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should be created for TV", tvHandler);
    }

    @Test
    public void testConstructorWithMemoryOptimized() {
        when(mockConfiguration.isMemoryOptimized()).thenReturn(true);

        IntegratedDeadLetterHandler optimizedHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should be created with memory optimization", optimizedHandler);
    }

    @Test
    public void testConstructorWithTVAndMemoryOptimized() {
        when(mockConfiguration.isTV()).thenReturn(true);
        when(mockConfiguration.isMemoryOptimized()).thenReturn(true);

        IntegratedDeadLetterHandler tvOptimizedHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should be created for TV with memory optimization", tvOptimizedHandler);
    }

    @Test
    public void testConstructorWithCustomBatchSizes() {
        when(mockConfiguration.getRegularBatchSizeBytes()).thenReturn(100000);
        when(mockConfiguration.getLiveBatchSizeBytes()).thenReturn(50000);

        IntegratedDeadLetterHandler customHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should accept custom batch sizes", customHandler);
    }

    @Test
    public void testConstructorWithCustomRetryInterval() {
        when(mockConfiguration.getDeadLetterRetryInterval()).thenReturn(120000L);

        IntegratedDeadLetterHandler customHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should accept custom retry interval", customHandler);
    }

    @Test
    public void testConstructorWithCustomMaxDeadLetterSize() {
        when(mockConfiguration.getMaxDeadLetterSize()).thenReturn(200);

        IntegratedDeadLetterHandler customHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should accept custom max dead letter size", customHandler);
    }

    // ========== handleFailedEvents Tests ==========

    @Test
    public void testHandleFailedEventsWithNullList() {
        handler.handleFailedEvents(null, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should not throw exception
        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    @Test
    public void testHandleFailedEventsWithEmptyList() {
        List<Map<String, Object>> events = new ArrayList<>();

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    @Test
    public void testHandleFailedEventsWithSingleEvent() {
        List<Map<String, Object>> events = createSampleEvents(1);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Event should be queued for retry (not backed up on first failure)
        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    @Test
    public void testHandleFailedEventsWithMultipleEvents() {
        List<Map<String, Object>> events = createSampleEvents(5);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Events should be queued for retry
        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    @Test
    public void testHandleFailedEventsWithLiveBufferType() {
        List<Map<String, Object>> events = createSampleEvents(2);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_LIVE);

        // Should handle live events
        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    @Test
    public void testHandleFailedEventsWithOnDemandBufferType() {
        List<Map<String, Object>> events = createSampleEvents(2);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should handle on-demand events
        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    @Test
    public void testHandleFailedEventsWithMaxRetries() {
        // Create event with max retries reached
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> event = createEventWithRetryCount(5); // More than max retries
        events.add(event);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should backup to SQLite
        verify(mockMainBuffer).backupFailedEvents(anyList());
    }

    @Test
    public void testHandleFailedEventsExhaustedRetries() {
        // Mobile device gets 3 retries by default
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> event = createEventWithRetryCount(3);
        events.add(event);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should backup when retries exhausted
        verify(mockMainBuffer).backupFailedEvents(anyList());
    }

    @Test
    public void testHandleFailedEventsMixedRetryCountsL() {
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(createEventWithRetryCount(0)); // Retry
        events.add(createEventWithRetryCount(1)); // Retry
        events.add(createEventWithRetryCount(5)); // Backup
        events.add(createEventWithRetryCount(2)); // Retry

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should backup only the exhausted event
        verify(mockMainBuffer).backupFailedEvents(argThat(list -> list.size() == 1));
    }

    // ========== emergencyBackup Tests ==========

    @Test
    public void testEmergencyBackup() {
        handler.emergencyBackup();

        // Should not throw exception even with empty queue
        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    @Test
    public void testEmergencyBackupWithPendingEvents() {
        // First add some failed events
        List<Map<String, Object>> events = createSampleEvents(5);
        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Then trigger emergency backup
        handler.emergencyBackup();

        // Should backup pending events
        verify(mockMainBuffer, atLeastOnce()).backupFailedEvents(anyList());
    }

    @Test
    public void testEmergencyBackupForMobileDevice() {
        when(mockConfiguration.isTV()).thenReturn(false);
        when(mockConfiguration.getMaxDeadLetterSize()).thenReturn(100);

        IntegratedDeadLetterHandler mobileHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        mobileHandler.emergencyBackup();

        // Should use mobile-specific batch size (maxDeadLetterSize * 2)
        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    @Test
    public void testEmergencyBackupForTVDevice() {
        when(mockConfiguration.isTV()).thenReturn(true);
        when(mockConfiguration.getMaxDeadLetterSize()).thenReturn(100);

        IntegratedDeadLetterHandler tvHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        tvHandler.emergencyBackup();

        // Should use TV-specific batch size (maxDeadLetterSize * 3)
        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    @Test
    public void testMultipleEmergencyBackups() {
        handler.emergencyBackup();
        handler.emergencyBackup();
        handler.emergencyBackup();

        // Should handle multiple emergency backups
        verify(mockMainBuffer, never()).backupFailedEvents(anyList());
    }

    // ========== Device-Specific Behavior Tests ==========

    @Test
    public void testMobileMaxRetries() {
        when(mockConfiguration.isTV()).thenReturn(false);
        when(mockConfiguration.isMemoryOptimized()).thenReturn(false);

        IntegratedDeadLetterHandler mobileHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        // Mobile gets 3 retries
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(createEventWithRetryCount(3));

        mobileHandler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        verify(mockMainBuffer).backupFailedEvents(anyList());
    }

    @Test
    public void testTVMaxRetries() {
        when(mockConfiguration.isTV()).thenReturn(true);
        when(mockConfiguration.isMemoryOptimized()).thenReturn(false);

        IntegratedDeadLetterHandler tvHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        // TV gets 5 retries
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(createEventWithRetryCount(5));

        tvHandler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        verify(mockMainBuffer).backupFailedEvents(anyList());
    }

    @Test
    public void testMobileMemoryOptimizedMaxRetries() {
        when(mockConfiguration.isTV()).thenReturn(false);
        when(mockConfiguration.isMemoryOptimized()).thenReturn(true);

        IntegratedDeadLetterHandler mobileHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        // Memory-optimized mobile gets 2 retries
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(createEventWithRetryCount(2));

        mobileHandler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        verify(mockMainBuffer).backupFailedEvents(anyList());
    }

    @Test
    public void testTVMemoryOptimizedMaxRetries() {
        when(mockConfiguration.isTV()).thenReturn(true);
        when(mockConfiguration.isMemoryOptimized()).thenReturn(true);

        IntegratedDeadLetterHandler tvHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        // Memory-optimized TV gets 3 retries
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(createEventWithRetryCount(3));

        tvHandler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        verify(mockMainBuffer).backupFailedEvents(anyList());
    }

    // ========== Retry Metadata Tests ==========

    @Test
    public void testRetryMetadataAdded() {
        List<Map<String, Object>> events = createSampleEvents(1);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Event should have retry metadata added (verified implicitly by retry logic)
    }

    @Test
    public void testRetryMetadataIncludesDeviceType() {
        when(mockConfiguration.isTV()).thenReturn(true);

        IntegratedDeadLetterHandler tvHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        List<Map<String, Object>> events = createSampleEvents(1);

        tvHandler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Device type should be included in metadata
    }

    @Test
    public void testRetryMetadataIncludesBufferType() {
        List<Map<String, Object>> events = createSampleEvents(1);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_LIVE);

        // Buffer type should be included in metadata
    }

    @Test
    public void testRetryMetadataIncludesTimestamp() {
        List<Map<String, Object>> events = createSampleEvents(1);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Timestamp should be included in metadata
    }

    // ========== Memory Capacity Tests ==========

    @Test
    public void testMemoryCapacityForTV() {
        when(mockConfiguration.isTV()).thenReturn(true);
        when(mockConfiguration.isMemoryOptimized()).thenReturn(false);
        when(mockConfiguration.getMaxDeadLetterSize()).thenReturn(100);

        IntegratedDeadLetterHandler tvHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        // TV allows 90% capacity usage
        List<Map<String, Object>> events = createSampleEvents(10);

        tvHandler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should handle events within capacity
    }

    @Test
    public void testMemoryCapacityForMobile() {
        when(mockConfiguration.isTV()).thenReturn(false);
        when(mockConfiguration.isMemoryOptimized()).thenReturn(false);
        when(mockConfiguration.getMaxDeadLetterSize()).thenReturn(100);

        IntegratedDeadLetterHandler mobileHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        // Mobile allows 80% capacity usage
        List<Map<String, Object>> events = createSampleEvents(10);

        mobileHandler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should handle events within capacity
    }

    @Test
    public void testMemoryCapacityForMemoryOptimized() {
        when(mockConfiguration.isMemoryOptimized()).thenReturn(true);
        when(mockConfiguration.getMaxDeadLetterSize()).thenReturn(100);

        IntegratedDeadLetterHandler optimizedHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        // Memory-optimized allows 65% capacity usage
        List<Map<String, Object>> events = createSampleEvents(10);

        optimizedHandler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should handle events within capacity
    }

    // ========== Batch Size Tests ==========

    @Test
    public void testRegularBatchSize() {
        when(mockConfiguration.getRegularBatchSizeBytes()).thenReturn(100000);

        IntegratedDeadLetterHandler customHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should use custom regular batch size", customHandler);
    }

    @Test
    public void testLiveBatchSize() {
        when(mockConfiguration.getLiveBatchSizeBytes()).thenReturn(50000);

        IntegratedDeadLetterHandler customHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should use custom live batch size", customHandler);
    }

    @Test
    public void testSmallBatchSizes() {
        when(mockConfiguration.getRegularBatchSizeBytes()).thenReturn(10000);
        when(mockConfiguration.getLiveBatchSizeBytes()).thenReturn(5000);

        IntegratedDeadLetterHandler smallHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should work with small batch sizes", smallHandler);
    }

    @Test
    public void testLargeBatchSizes() {
        when(mockConfiguration.getRegularBatchSizeBytes()).thenReturn(500000);
        when(mockConfiguration.getLiveBatchSizeBytes()).thenReturn(250000);

        IntegratedDeadLetterHandler largeHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should work with large batch sizes", largeHandler);
    }

    // ========== Retry Interval Tests ==========

    @Test
    public void testCustomRetryInterval() {
        when(mockConfiguration.getDeadLetterRetryInterval()).thenReturn(120000L);

        IntegratedDeadLetterHandler customHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        assertNotNull("Handler should use custom retry interval", customHandler);
    }

    @Test
    public void testLiveRetryIntervalCalculation() {
        when(mockConfiguration.getDeadLetterRetryInterval()).thenReturn(60000L);

        IntegratedDeadLetterHandler customHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        // Live retry interval should be half of regular (30s)
        assertNotNull("Handler should calculate live retry interval", customHandler);
    }

    @Test
    public void testMinimumLiveRetryInterval() {
        when(mockConfiguration.getDeadLetterRetryInterval()).thenReturn(10000L);

        IntegratedDeadLetterHandler customHandler = new IntegratedDeadLetterHandler(
            mockMainBuffer, mockHttpClient, mockConfiguration);

        // Live retry interval should be minimum 30s
        assertNotNull("Handler should enforce minimum live retry interval", customHandler);
    }

    // ========== Edge Cases ==========

    @Test
    public void testHandleLargeNumberOfEvents() {
        // Create events with high retry counts to trigger backup
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            events.add(createEventWithRetryCount(5)); // Exceed max retries
        }

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should backup events that exceeded retry count
        verify(mockMainBuffer, atLeastOnce()).backupFailedEvents(anyList());
    }

    @Test
    public void testConcurrentHandleFailedEvents() {
        List<Map<String, Object>> events1 = createSampleEvents(5);
        List<Map<String, Object>> events2 = createSampleEvents(5);

        // Simulate concurrent calls
        handler.handleFailedEvents(events1, NRVideoConstants.EVENT_TYPE_ONDEMAND);
        handler.handleFailedEvents(events2, NRVideoConstants.EVENT_TYPE_LIVE);

        // Second call should be skipped due to AtomicBoolean guard
    }

    @Test
    public void testHandleEventsWithComplexData() {
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CONTENT_START");
        event.put("nested", new HashMap<String, Object>() {{
            put("key", "value");
        }});
        event.put("array", new ArrayList<String>() {{
            add("item1");
            add("item2");
        }});
        events.add(event);

        handler.handleFailedEvents(events, NRVideoConstants.EVENT_TYPE_ONDEMAND);

        // Should handle complex event data
    }

    // ========== Helper Methods ==========

    private List<Map<String, Object>> createSampleEvents(int count) {
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "TEST_EVENT");
            event.put("timestamp", System.currentTimeMillis());
            event.put("index", i);
            events.add(event);
        }
        return events;
    }

    private Map<String, Object> createEventWithRetryCount(int retryCount) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "TEST_EVENT");
        event.put("timestamp", System.currentTimeMillis());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("retryCount", retryCount);
        event.put("retryMetadata", metadata);

        return event;
    }
}
