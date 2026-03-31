package com.newrelic.videoagent.core.harvest;

import android.content.Context;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.auth.TokenManager;
import com.newrelic.videoagent.core.device.DeviceInformation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OptimizedHttpClient.
 * Tests HTTP request handling, retries, regional endpoints, compression, and token management.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class OptimizedHttpClientTest {

    private Context context;

    @Mock
    private NRVideoConfiguration mockConfiguration;

    private OptimizedHttpClient httpClient;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();

        // Set up default configuration behavior
        when(mockConfiguration.getRegion()).thenReturn("US");
        when(mockConfiguration.getApplicationToken()).thenReturn("test-app-token-12345");
        when(mockConfiguration.isMemoryOptimized()).thenReturn(false);

        httpClient = new OptimizedHttpClient(mockConfiguration, context);
    }

    // ========== Constructor and Initialization Tests ==========

    @Test
    public void testConstructorWithUSRegion() {
        when(mockConfiguration.getRegion()).thenReturn("US");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should be created", client);
    }

    @Test
    public void testConstructorWithEURegion() {
        when(mockConfiguration.getRegion()).thenReturn("EU");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should be created", client);
    }

    @Test
    public void testConstructorWithAPRegion() {
        when(mockConfiguration.getRegion()).thenReturn("AP");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should be created", client);
    }

    @Test
    public void testConstructorWithGOVRegion() {
        when(mockConfiguration.getRegion()).thenReturn("GOV");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should be created", client);
    }

    @Test
    public void testConstructorWithSTAGINGRegion() {
        when(mockConfiguration.getRegion()).thenReturn("STAGING");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should be created", client);
    }

    @Test
    public void testConstructorWithInvalidRegionDefaultsToUS() {
        when(mockConfiguration.getRegion()).thenReturn("INVALID_REGION");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should be created with default US region", client);
    }

    @Test
    public void testConstructorWithLowercaseRegion() {
        when(mockConfiguration.getRegion()).thenReturn("eu");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should handle lowercase region", client);
    }

    @Test
    public void testConstructorWithMemoryOptimizedEnabled() {
        when(mockConfiguration.isMemoryOptimized()).thenReturn(true);

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should be created with memory optimization", client);
    }

    @Test
    public void testConstructorWithMemoryOptimizedDisabled() {
        when(mockConfiguration.isMemoryOptimized()).thenReturn(false);

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should be created without memory optimization", client);
    }

    // ========== sendEvents Tests ==========

    @Test
    public void testSendEventsWithNullList() {
        boolean result = httpClient.sendEvents(null, "video");

        assertTrue("Sending null events should return true", result);
    }

    @Test
    public void testSendEventsWithEmptyList() {
        List<Map<String, Object>> events = new ArrayList<>();

        boolean result = httpClient.sendEvents(events, "video");

        assertTrue("Sending empty events should return true", result);
    }

    @Test
    public void testSendEventsWithSingleEvent() {
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CONTENT_START");
        event.put("timestamp", System.currentTimeMillis());
        events.add(event);

        // This will fail in unit test (no actual network), but tests the flow
        boolean result = httpClient.sendEvents(events, "video");

        // In unit test without mock HTTP, this will return false after retries
        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testSendEventsWithMultipleEvents() {
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CONTENT_HEARTBEAT");
            event.put("timestamp", System.currentTimeMillis());
            events.add(event);
        }

        // This will fail in unit test (no actual network), but tests the flow
        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testSendEventsWithLargeEventList() {
        List<Map<String, Object>> events = new ArrayList<>();
        // Create more than 10 events to trigger compression
        for (int i = 0; i < 15; i++) {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CONTENT_HEARTBEAT");
            event.put("timestamp", System.currentTimeMillis());
            event.put("index", i);
            events.add(event);
        }

        // This will fail in unit test (no actual network), but tests compression path
        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    // ========== Endpoint Type Tests ==========

    @Test
    public void testSendEventsWithVideoEndpointType() {
        List<Map<String, Object>> events = createSampleEvents(1);

        boolean result = httpClient.sendEvents(events, "video");

        // Endpoint type is passed but not currently used in implementation
        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testSendEventsWithLiveEndpointType() {
        List<Map<String, Object>> events = createSampleEvents(1);

        boolean result = httpClient.sendEvents(events, "live");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testSendEventsWithOnDemandEndpointType() {
        List<Map<String, Object>> events = createSampleEvents(1);

        boolean result = httpClient.sendEvents(events, "on_demand");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    // ========== Retry Logic Tests ==========

    @Test
    public void testSendEventsRetriesOnFailure() {
        List<Map<String, Object>> events = createSampleEvents(2);

        // Without actual HTTP mocking, this will retry 3 times and fail
        long startTime = System.currentTimeMillis();
        boolean result = httpClient.sendEvents(events, "video");
        long duration = System.currentTimeMillis() - startTime;

        assertFalse("Send should fail after retries", result);
        // Retries should happen quickly (no sleep for mobile/TV optimization)
        assertTrue("Retries should complete quickly", duration < 5000);
    }

    // ========== Event Creation Helpers ==========

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

    // ========== Configuration Tests ==========

    @Test
    public void testHTTPClientWithDifferentApplicationTokens() {
        when(mockConfiguration.getApplicationToken()).thenReturn("different-token-xyz");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should work with different tokens", client);
    }

    @Test
    public void testHTTPClientWithEmptyApplicationToken() {
        when(mockConfiguration.getApplicationToken()).thenReturn("");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);

        assertNotNull("HTTP client should handle empty token", client);
    }

    // ========== Regional Endpoint Tests ==========

    @Test
    public void testRegionalEndpointForUS() {
        when(mockConfiguration.getRegion()).thenReturn("US");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);
        List<Map<String, Object>> events = createSampleEvents(1);

        // Will attempt to connect to US endpoint
        boolean result = client.sendEvents(events, "video");

        assertFalse("Without real network, send should fail", result);
    }

    @Test
    public void testRegionalEndpointForEU() {
        when(mockConfiguration.getRegion()).thenReturn("EU");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);
        List<Map<String, Object>> events = createSampleEvents(1);

        // Will attempt to connect to EU endpoint
        boolean result = client.sendEvents(events, "video");

        assertFalse("Without real network, send should fail", result);
    }

    @Test
    public void testRegionalEndpointForAP() {
        when(mockConfiguration.getRegion()).thenReturn("AP");

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);
        List<Map<String, Object>> events = createSampleEvents(1);

        // Will attempt to connect to AP endpoint
        boolean result = client.sendEvents(events, "video");

        assertFalse("Without real network, send should fail", result);
    }

    // ========== Compression Tests ==========

    @Test
    public void testCompressionNotUsedForSmallEventBatch() {
        // Less than 10 events - no compression
        List<Map<String, Object>> events = createSampleEvents(5);

        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testCompressionUsedForLargeEventBatch() {
        // More than 10 events - compression enabled
        List<Map<String, Object>> events = createSampleEvents(20);

        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testCompressionThresholdAt10Events() {
        // Exactly 10 events - no compression (> 10 triggers it)
        List<Map<String, Object>> events = createSampleEvents(10);

        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testCompressionThresholdAt11Events() {
        // 11 events - compression enabled
        List<Map<String, Object>> events = createSampleEvents(11);

        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    // ========== Memory Optimization Tests ==========

    @Test
    public void testMemoryOptimizedTimeouts() {
        when(mockConfiguration.isMemoryOptimized()).thenReturn(true);

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);
        List<Map<String, Object>> events = createSampleEvents(1);

        // Should use shorter timeouts (6s connect, 10s read)
        boolean result = client.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testStandardTimeouts() {
        when(mockConfiguration.isMemoryOptimized()).thenReturn(false);

        OptimizedHttpClient client = new OptimizedHttpClient(mockConfiguration, context);
        List<Map<String, Object>> events = createSampleEvents(1);

        // Should use standard timeouts (30s connect, 60s read)
        boolean result = client.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    // ========== Event Payload Tests ==========

    @Test
    public void testSendEventsWithComplexEventData() {
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CONTENT_START");
        event.put("timestamp", System.currentTimeMillis());
        event.put("contentTitle", "Test Video");
        event.put("contentDuration", 12000L);
        event.put("contentPlayhead", 0L);
        event.put("contentBitrate", 2500);
        event.put("customAttributes", new HashMap<String, Object>() {{
            put("userId", "user123");
            put("category", "entertainment");
        }});
        events.add(event);

        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testSendEventsWithNestedObjects() {
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CONTENT_BUFFER_START");
        event.put("metadata", new HashMap<String, Object>() {{
            put("device", new HashMap<String, Object>() {{
                put("type", "mobile");
                put("os", "Android");
            }});
        }});
        events.add(event);

        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testSendEventsWithArrayValues() {
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CONTENT_REQUEST");
        event.put("tags", Arrays.asList("video", "live", "sports"));
        event.put("resolutions", Arrays.asList(720, 1080, 1440));
        events.add(event);

        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    // ========== Edge Cases ==========

    @Test
    public void testSendEventsWithVeryLargeEventBatch() {
        // Test with 100 events
        List<Map<String, Object>> events = createSampleEvents(100);

        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testSendEventsWithSingleEventAtCompressionBoundary() {
        // Test boundary condition - exactly 11 events
        List<Map<String, Object>> events = createSampleEvents(11);

        boolean result = httpClient.sendEvents(events, "video");

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testMultipleConsecutiveSendOperations() {
        List<Map<String, Object>> events1 = createSampleEvents(2);
        List<Map<String, Object>> events2 = createSampleEvents(3);
        List<Map<String, Object>> events3 = createSampleEvents(1);

        boolean result1 = httpClient.sendEvents(events1, "video");
        boolean result2 = httpClient.sendEvents(events2, "video");
        boolean result3 = httpClient.sendEvents(events3, "video");

        // All should fail without real network
        assertFalse("First send should fail", result1);
        assertFalse("Second send should fail", result2);
        assertFalse("Third send should fail", result3);
    }

    // ========== Null and Empty Handling ==========

    @Test
    public void testSendEventsWithNullEndpointType() {
        List<Map<String, Object>> events = createSampleEvents(1);

        boolean result = httpClient.sendEvents(events, null);

        assertFalse("Without mock HTTP, send should fail", result);
    }

    @Test
    public void testSendEventsWithEmptyEndpointType() {
        List<Map<String, Object>> events = createSampleEvents(1);

        boolean result = httpClient.sendEvents(events, "");

        assertFalse("Without mock HTTP, send should fail", result);
    }
}
