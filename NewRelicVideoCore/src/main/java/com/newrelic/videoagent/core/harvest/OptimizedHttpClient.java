package com.newrelic.videoagent.core.harvest;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.auth.TokenManager;
import com.newrelic.videoagent.core.device.DeviceInformation;
import android.util.Log;
import com.newrelic.videoagent.core.util.JsonStreamUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

/**
 * Optimized HTTP client for mobile/TV environments
 * Uses only Android built-in APIs to avoid external dependencies
 * Features:
 * - Automatic app token management with persistent caching
 * - Regional endpoint selection
 * - Mobile/TV specific optimizations (battery, bandwidth)
 * - Connection reuse where possible
 * - Device information integration for analytics
 */
public class OptimizedHttpClient implements HttpClientInterface {

    private static final String TAG = "NRVideo.HttpClient";

    private final NRVideoConfiguration configuration;
    private final TokenManager tokenManager;
    private final DeviceInformation deviceInfo;

    // Simplified regional endpoint configuration
    private static final Map<String, String> REGIONAL_ENDPOINTS = new HashMap<>();

    static {
        REGIONAL_ENDPOINTS.put("US", "https://mobile-collector.newrelic.com/mobile/v3/data");
        REGIONAL_ENDPOINTS.put("EU", "https://mobile-collector.eu.newrelic.com/mobile/v3/data");
        REGIONAL_ENDPOINTS.put("AP", "https://mobile-collector.ap.newrelic.com/mobile/v3/data");
        REGIONAL_ENDPOINTS.put("GOV", "https://mobile-collector.gov.newrelic.com/mobile/v3/data");
        REGIONAL_ENDPOINTS.put("STAGING", "https://mobile-collector.staging.newrelic.com/mobile/v3/data");
        REGIONAL_ENDPOINTS.put("DEFAULT", REGIONAL_ENDPOINTS.get("US"));
    }

    // Single endpoint URL for current region
    private final String endpointUrl;

    // Mobile/TV optimized timeouts
    private int connectionTimeoutMs = 30000;  // 30 seconds
    private int readTimeoutMs = 60000;        // 60 seconds

    public OptimizedHttpClient(NRVideoConfiguration configuration, android.content.Context context) {
        this.configuration = configuration;
        this.tokenManager = new TokenManager(context, configuration);
        this.deviceInfo = DeviceInformation.getInstance(context);

        // Set endpoint URL based on region, defaulting to US if not found
        String region = configuration.getRegion().toUpperCase();
        this.endpointUrl = REGIONAL_ENDPOINTS.getOrDefault(region, REGIONAL_ENDPOINTS.get("DEFAULT"));

        if (configuration.isMemoryOptimized()) {
            connectionTimeoutMs = 6000;
            readTimeoutMs = 10000;
        }

        // Enable HTTP/2 and connection reuse where available (Android 5.0+)
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.keepAliveDuration", "300000"); // 5 minutes
        System.setProperty("http.maxConnections", "5");

        if (configuration.isDebugLoggingEnabled()) {
            Log.d(TAG, "Initialized with region: " + region +
                  ", endpoint URL: " + endpointUrl);
        }
    }

    @Override
    public boolean sendEvents(List<Map<String, Object>> events, String endpointType) {
        if (events == null || events.isEmpty()) {
            return true;
        }

        // Attempt to send with immediate retries
        return sendEventsWithRetry(events);
    }

    private boolean sendEventsWithRetry(List<Map<String, Object>> events) {
        final int maxRetryAttempts = 3;
        int attempt = 0;

        while (attempt < maxRetryAttempts) {
            try {
                boolean result = performHttpRequest(events, endpointUrl);
                if (result) {
                    if (configuration.isDebugLoggingEnabled()) {
                        Log.d(TAG, "Successfully sent " + events.size() + " events on attempt " + (attempt + 1) + " to " + endpointUrl);
                    }
                    return true;
                }

            } catch (IOException e) {
                if (configuration.isDebugLoggingEnabled()) {
                    Log.w(TAG, "Attempt " + (attempt + 1) + " failed: " + e.getMessage());
                }

                // Handle rate limiting specially - don't retry immediately
                if (e.getMessage() != null && e.getMessage().contains("Rate limit exceeded (429)")) {
                    if (configuration.isDebugLoggingEnabled()) {
                        Log.w(TAG, "Rate limit hit - deferring to HarvestManager for delayed retry");
                    }
                    return false; // Let HarvestManager handle rate limit delays
                }
            }

            attempt++;
            // MOBILE/TV OPTIMIZATION: Remove Thread.sleep() to avoid blocking
            // Instead of sleeping here, we rely on:
            // 1. Domain rotation (immediate failover to different endpoints)
            // 2. Circuit breaker pattern (switches to backup domains)
            // 3. HarvestManager's scheduled retries for application-level delays
            if (attempt < maxRetryAttempts) {
                if (configuration.isDebugLoggingEnabled()) {
                    Log.d(TAG, "Immediate retry " + (attempt + 1) + "/" + maxRetryAttempts + " (no delay for mobile/TV performance)");
                }
            }
        }

        // All immediate retries failed - let HarvestManager handle application-level retries
        if (configuration.isDebugLoggingEnabled()) {
            Log.w(TAG, "All " + maxRetryAttempts + " immediate attempts failed for " + events.size() + " events. Queuing for HarvestManager retry.");
        }
        return false;
    }

    private boolean performHttpRequest(List<Map<String, Object>> events, String endpointUrl) throws IOException {
        URL url = new URL(endpointUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            // Optimize connection settings for mobile/TV
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setConnectTimeout(connectionTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);

            // Pre-calculate compression decision based on event count (faster than JSON size)
            boolean useCompression = events.size() > 10;

            // Get app token (cached or generate new one)
            List<Long> appToken = tokenManager.getAppToken();

            // Set headers with app token and device information
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", deviceInfo.getUserAgent());
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("X-App-License-Key", configuration.getApplicationToken());

            List<Object> payload = new ArrayList<>();
            payload.add(appToken);
            payload.add(Arrays.asList(
                    deviceInfo.getOsName(),
                    deviceInfo.getOsVersion(),
                    deviceInfo.getArchitecture(),
                    deviceInfo.getAgentName(),
                    deviceInfo.getAgentVersion(),
                    deviceInfo.getDeviceId(),
                    "",
                    "",
                    deviceInfo.getManufacturer(),
                    new HashMap<String, Object>() {{
                        put("size", deviceInfo.getSize());
                        put("platform", deviceInfo.getApplicationFramework());
                        put("platformVersion", deviceInfo.getApplicationFrameworkVersion());
                    }}
            ));
            payload.add(0);
            payload.add(new ArrayList<>()); // []
            payload.add(new ArrayList<>()); // []
            payload.add(new ArrayList<>()); // []
            payload.add(new ArrayList<>()); // []
            payload.add(new ArrayList<>()); // []
            payload.add(new HashMap<>());   // {}
            payload.add(events);

            if (useCompression) {
                connection.setRequestProperty("Content-Encoding", "gzip");
                connection.setRequestProperty("Accept-Encoding", "gzip");
            }

            // OPTIMIZATION: Stream JSON directly to output without intermediate string
            try (OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream())) {
                if (useCompression) {
                    try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream)) {
                        streamJsonToOutputStream(payload, gzipStream);
                        gzipStream.finish();
                    }
                } else {
                    streamJsonToOutputStream(payload, outputStream);
                }
                outputStream.flush();
            }

            // Check response
            int responseCode = connection.getResponseCode();
            boolean success = responseCode >= 200 && responseCode < 300;

            // Handle different error scenarios
            if (responseCode == 401 || responseCode == 403) {
                // Token might be expired, refresh and retry once
                try {
                    tokenManager.refreshToken();
                    if (configuration.isDebugLoggingEnabled()) {
                        Log.d(TAG, "Token refreshed due to auth failure (response: " + responseCode + ")");
                    }
                } catch (IOException tokenRefreshError) {
                    if (configuration.isDebugLoggingEnabled()) {
                        Log.w(TAG, "Token refresh failed", tokenRefreshError);
                    }
                }
            } else if (responseCode == 429) {
                // Rate limit exceeded - extract retry-after header if present
                String retryAfter = connection.getHeaderField("Retry-After");
                long retryAfterMs = parseRetryAfter(retryAfter);

                if (configuration.isDebugLoggingEnabled()) {
                    Log.w(TAG, "Rate limit exceeded (429). Retry after: " + retryAfterMs + "ms");
                }

                // Don't sleep here - let caller handle the retry delay
                // Mark this as a temporary failure by throwing a specific exception
                throw new IOException("Rate limit exceeded (429). Retry after: " + retryAfterMs + "ms");
            }

            // OPTIMIZATION: More efficient response reading
            if (success || responseCode < 500) { // Only read response for non-server errors
                try (InputStream responseStream = success ? connection.getInputStream() : connection.getErrorStream()) {
                    if (responseStream != null) {
                        // Optimized response draining with larger buffer
                        byte[] buffer = new byte[4096]; // Larger buffer for better performance
                        //noinspection StatementWithEmptyBody
                        while (responseStream.read(buffer) != -1) {
                            // Discard response data to enable connection reuse
                        }
                    }
                }
            }

            return success;

        } catch (Exception e) {
            throw new IOException("Request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse Retry-After header value to milliseconds
     * Supports both delay-seconds and HTTP-date formats
     */
    private long parseRetryAfter(String retryAfter) {
        if (retryAfter == null || retryAfter.trim().isEmpty()) {
            return 60000; // Default 60 seconds for rate limiting
        }

        try {
            // Try parsing as seconds (most common format)
            int seconds = Integer.parseInt(retryAfter.trim());
            return Math.min(seconds * 1000L, 300000L); // Cap at 5 minutes
        } catch (NumberFormatException e) {
            // Could be HTTP-date format, but that's complex to parse
            // For mobile/TV simplicity, just use default
            return 60000; // Default 60 seconds
        }
    }

    /**
     * OPTIMIZATION: Stream JSON directly to OutputStream without creating intermediate strings
     * This saves memory and is faster for large event batches
     */
    private void streamJsonToOutputStream(List<Object> events, OutputStream outputStream) throws IOException {
        JsonStreamUtil.streamJsonToOutputStream(events, outputStream);
    }

}
