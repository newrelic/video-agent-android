package com.newrelic.videoagent.core.harvest;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.auth.TokenManager;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

/**
 * Optimized HTTP client for mobile/TV environments with domain resilience
 * Uses only Android built-in APIs to avoid external dependencies
 * Features:
 * - Automatic app token management with persistent caching
 * - Multiple primary domains with intelligent failover
 * - Backup domains for extreme network conditions
 * - Circuit breaker pattern for failed domains
 * - Mobile/TV specific optimizations (battery, bandwidth)
 * - Connection reuse where possible
 */
public class OptimizedHttpClient implements HttpClientInterface {

    private final NRVideoConfiguration configuration;
    private final TokenManager tokenManager;
    private DeadLetterHandler deadLetterHandler;

    // Domain resilience - integrated from ApiDomainSelector
    private static final String[] PRIMARY_DOMAINS = {
        "mobile-collector.newrelic.com",
        "mobile-collector-01.newrelic.com",
        "mobile-collector-02.newrelic.com"
    };

    private static final String[] BACKUP_DOMAINS = {
        "mobile-collector-backup.newrelic.com",
        "mobile-collector-fallback.newrelic.com"
    };

    // Circuit breaker state per domain
    private final AtomicInteger currentPrimaryIndex = new AtomicInteger(0);
    private final AtomicInteger currentBackupIndex = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile boolean usingBackupDomains = false;

    // Mobile/TV optimized timeouts and retry settings
    private int connectionTimeoutMs = 8000;
    private int readTimeoutMs = 12000;
    private final int maxRetryAttempts = 3;
    private final long baseRetryDelayMs = 1000;
    private final long maxRetryDelayMs = 30000;

    // Circuit breaker settings
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 300000; // 5 minutes

    public OptimizedHttpClient(NRVideoConfiguration configuration, android.content.Context context) {
        this.configuration = configuration;
        this.tokenManager = new TokenManager(context, configuration);

        if (configuration.isMemoryOptimized()) {
            connectionTimeoutMs = 6000;
            readTimeoutMs = 10000;
        }

        // Enable HTTP/2 and connection reuse where available (Android 5.0+)
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.keepAliveDuration", "300000"); // 5 minutes
        System.setProperty("http.maxConnections", "5");
    }

    public void setDeadLetterHandler(DeadLetterHandler deadLetterHandler) {
        this.deadLetterHandler = deadLetterHandler;
    }

    @Override
    public boolean sendEvents(List<Map<String, Object>> events, String endpointType) {
        if (events == null || events.isEmpty()) {
            return true;
        }

        // Check and reset circuit breaker if enough time has passed
        if (shouldResetCircuitBreaker()) {
            resetToPrimary();
        }

        // Attempt to send with domain failover
        boolean success = sendEventsWithDomainFailover(events, endpointType);

        if (!success && deadLetterHandler != null) {
            deadLetterHandler.handleFailedEvents(events, endpointType);
        }

        return success;
    }

    private boolean sendEventsWithDomainFailover(List<Map<String, Object>> events, String endpointType) {
        int attempt = 0;
        String currentDomain = null;

        while (attempt < maxRetryAttempts) {
            try {
                currentDomain = selectOptimalDomain();
                String endpoint = buildEndpointUrl(currentDomain);

                boolean result = performHttpRequest(events, endpoint);
                if (result) {
                    consecutiveFailures.set(0); // Reset on success
                    return true;
                }

                markDomainFailed(currentDomain);

            } catch (IOException e) {
                if (currentDomain != null) {
                    markDomainFailed(currentDomain);
                }
            }

            attempt++;
            if (attempt < maxRetryAttempts) {
                try {
                    Thread.sleep(calculateRetryDelay(attempt));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return false;
    }

    private boolean performHttpRequest(List<Map<String, Object>> events, String endpoint) throws IOException {
        URL url = new URL(endpoint);
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
            String appToken;
            try {
                appToken = tokenManager.getAppToken();
                if (appToken == null) {
                    if (configuration.isDebugLoggingEnabled()) {
                        System.err.println("[OptimizedHttpClient] Failed to get app token");
                    }
                    return false;
                }
            } catch (IOException e) {
                if (configuration.isDebugLoggingEnabled()) {
                    System.err.println("[OptimizedHttpClient] Token generation failed: " + e.getMessage());
                }
                return false;
            }

            // Set headers with app token
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + appToken);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "NewRelic-VideoAgent-Android/1.0");
            connection.setRequestProperty("Connection", "keep-alive");

            if (useCompression) {
                connection.setRequestProperty("Content-Encoding", "gzip");
                connection.setRequestProperty("Accept-Encoding", "gzip");
            }

            // OPTIMIZATION: Stream JSON directly to output without intermediate string
            try (OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream())) {
                if (useCompression) {
                    try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream)) {
                        streamJsonToOutputStream(events, gzipStream);
                        gzipStream.finish();
                    }
                } else {
                    streamJsonToOutputStream(events, outputStream);
                }
                outputStream.flush();
            }

            // Check response
            int responseCode = connection.getResponseCode();
            boolean success = responseCode >= 200 && responseCode < 300;

            // Handle token expiration
            if (responseCode == 401 || responseCode == 403) {
                // Token might be expired, refresh and retry once
                try {
                    tokenManager.refreshToken();
                    if (configuration.isDebugLoggingEnabled()) {
                        System.out.println("[OptimizedHttpClient] Token refreshed due to auth failure");
                    }
                } catch (IOException tokenRefreshError) {
                    if (configuration.isDebugLoggingEnabled()) {
                        System.err.println("[OptimizedHttpClient] Token refresh failed: " + tokenRefreshError.getMessage());
                    }
                }
            }

            // OPTIMIZATION: More efficient response reading
            if (success || responseCode < 500) { // Only read response for non-server errors
                try (InputStream responseStream = success ? connection.getInputStream() : connection.getErrorStream()) {
                    if (responseStream != null) {
                        // Optimized response draining with larger buffer
                        byte[] buffer = new byte[4096]; // Larger buffer for better performance
                        while (responseStream.read(buffer) != -1) {
                            // Discard response data to enable connection reuse
                        }
                    }
                }
            }

            return success;

        } finally {
            // Don't call disconnect() immediately to allow connection reuse
            // The connection will be closed automatically after keep-alive timeout
        }
    }

    /**
     * OPTIMIZATION: Stream JSON directly to OutputStream without creating intermediate strings
     * This saves memory and is faster for large event batches
     */
    private void streamJsonToOutputStream(List<Map<String, Object>> events, OutputStream outputStream) throws IOException {
        byte[] openBracket = "[".getBytes("UTF-8");
        byte[] closeBracket = "]".getBytes("UTF-8");
        byte[] comma = ",".getBytes("UTF-8");

        outputStream.write(openBracket);

        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                outputStream.write(comma);
            }
            streamMapToOutputStream(events.get(i), outputStream);
        }

        outputStream.write(closeBracket);
    }

    /**
     * Stream individual map to OutputStream as JSON
     */
    private void streamMapToOutputStream(Map<String, Object> map, OutputStream outputStream) throws IOException {
        byte[] openBrace = "{".getBytes("UTF-8");
        byte[] closeBrace = "}".getBytes("UTF-8");
        byte[] comma = ",".getBytes("UTF-8");
        byte[] colon = ":".getBytes("UTF-8");
        byte[] quote = "\"".getBytes("UTF-8");

        outputStream.write(openBrace);

        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                outputStream.write(comma);
            }

            // Write key
            outputStream.write(quote);
            outputStream.write(escapeJsonString(entry.getKey()).getBytes("UTF-8"));
            outputStream.write(quote);
            outputStream.write(colon);

            // Write value
            Object value = entry.getValue();
            if (value instanceof String) {
                outputStream.write(quote);
                outputStream.write(escapeJsonString((String) value).getBytes("UTF-8"));
                outputStream.write(quote);
            } else if (value instanceof Number || value instanceof Boolean) {
                outputStream.write(value.toString().getBytes("UTF-8"));
            } else {
                outputStream.write(quote);
                outputStream.write(escapeJsonString(value != null ? value.toString() : "null").getBytes("UTF-8"));
                outputStream.write(quote);
            }
            first = false;
        }

        outputStream.write(closeBrace);
    }

    private String selectOptimalDomain() {
        if (usingBackupDomains) {
            return selectBackupDomain();
        } else {
            return selectPrimaryDomain();
        }
    }

    private String selectPrimaryDomain() {
        String region = configuration.getRegion().toUpperCase();

        if ("EU".equals(region)) {
            return "mobile-collector.eu.newrelic.com";
        }

        int index = currentPrimaryIndex.get();
        return PRIMARY_DOMAINS[index % PRIMARY_DOMAINS.length];
    }

    private String selectBackupDomain() {
        int index = currentBackupIndex.get();
        return BACKUP_DOMAINS[index % BACKUP_DOMAINS.length];
    }

    private String buildEndpointUrl(String domain) {
        // Allow custom endpoint URL override from configuration
        String configEndpointUrl = configuration.getEndpointUrl();
        if (configEndpointUrl != null && configEndpointUrl.startsWith("http")) {
            return configEndpointUrl;
        }

        return "https://" + domain + "/mobile/v1/events";
    }

    private void markDomainFailed(String failedDomain) {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            if (!usingBackupDomains) {
                usingBackupDomains = true;
                currentBackupIndex.set(0);
                if (configuration.isDebugLoggingEnabled()) {
                    System.out.println("[OptimizedHttpClient] Switching to backup domains");
                }
            } else {
                // Rotate to next backup domain
                int currentIndex = currentBackupIndex.get();
                int nextIndex = (currentIndex + 1) % BACKUP_DOMAINS.length;
                currentBackupIndex.set(nextIndex);
            }
        } else {
            if (!usingBackupDomains) {
                // Rotate to next primary domain
                int currentIndex = currentPrimaryIndex.get();
                int nextIndex = (currentIndex + 1) % PRIMARY_DOMAINS.length;
                currentPrimaryIndex.set(nextIndex);
            }
        }
    }

    private boolean shouldResetCircuitBreaker() {
        long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
        return timeSinceLastFailure > CIRCUIT_BREAKER_TIMEOUT;
    }

    private void resetToPrimary() {
        usingBackupDomains = false;
        consecutiveFailures.set(0);
        currentPrimaryIndex.set(0);
        currentBackupIndex.set(0);

        if (configuration.isDebugLoggingEnabled()) {
            System.out.println("[OptimizedHttpClient] Reset to primary domains");
        }
    }

    private long calculateRetryDelay(int attempt) {
        long exponentialDelay = baseRetryDelayMs * (1L << attempt);
        double jitter = 0.1 + (Math.random() * 0.1);
        long delayWithJitter = (long) (exponentialDelay * (1 + jitter));
        return Math.min(delayWithJitter, maxRetryDelayMs);
    }

    @Override
    public void setConnectionTimeout(int timeoutMs) {
        this.connectionTimeoutMs = timeoutMs;
    }

    @Override
    public void setReadTimeout(int timeoutMs) {
        this.readTimeoutMs = timeoutMs;
    }

    @Override
    public void cleanup() {
        resetToPrimary();
    }

    private String convertEventsToJson(List<Map<String, Object>> events) {
        StringBuilder json = new StringBuilder();
        json.append("[");

        for (int i = 0; i < events.size(); i++) {
            if (i > 0) json.append(",");
            json.append(mapToJson(events.get(i)));
        }

        json.append("]");
        return json.toString();
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(escapeJsonString(entry.getKey())).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escapeJsonString((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(escapeJsonString(value != null ? value.toString() : "null")).append("\"");
            }
            first = false;
        }

        json.append("}");
        return json.toString();
    }

    private String escapeJsonString(String str) {
        if (str == null) return "null";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}
