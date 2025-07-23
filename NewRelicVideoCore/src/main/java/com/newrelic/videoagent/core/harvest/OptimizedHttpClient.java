package com.newrelic.videoagent.core.harvest;

import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.auth.TokenManager;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
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

    // Mobile/TV optimized timeouts
    private int connectionTimeoutMs = 8000;
    private int readTimeoutMs = 12000;

    // Circuit breaker settings
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 300000; // 5 minutes

    // UTF-8 charset constant for API 16+ compatibility
    private static final String UTF_8 = "UTF-8";

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

    @Override
    public boolean sendEvents(List<Map<String, Object>> events, String endpointType) {
        if (events == null || events.isEmpty()) {
            return true;
        }

        // Check and reset circuit breaker if enough time has passed
        if (shouldResetCircuitBreaker()) {
            resetToPrimary();
        }

        // Attempt to send with domain failover and immediate retries
        return sendEventsWithRetry(events, endpointType);
    }

    private boolean sendEventsWithRetry(List<Map<String, Object>> events, String endpointType) {
        final int maxRetryAttempts = 3;

        int attempt = 0;

        while (attempt < maxRetryAttempts) {
            try {
                String currentDomain = selectOptimalDomain();
                String endpoint = buildEndpointUrl(currentDomain);

                boolean result = performHttpRequest(events, endpoint);
                if (result) {
                    consecutiveFailures.set(0); // Reset on success
                    if (configuration.isDebugLoggingEnabled()) {
                        System.out.println("[OptimizedHttpClient] Successfully sent " + events.size() + " events on attempt " + (attempt + 1));
                    }
                    return true;
                }

                markDomainFailed();

            } catch (IOException e) {
                markDomainFailed();
                if (configuration.isDebugLoggingEnabled()) {
                    System.err.println("[OptimizedHttpClient] Attempt " + (attempt + 1) + " failed: " + e.getMessage());
                }
            }

            attempt++;
            if (attempt < maxRetryAttempts) {
                try {
                    long delay = calculateRetryDelay(attempt);
                    if (configuration.isDebugLoggingEnabled()) {
                        System.out.println("[OptimizedHttpClient] Retrying in " + delay + "ms (attempt " + (attempt + 1) + ")");
                    }
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // All immediate retries failed - let HarvestManager handle application-level retries
        if (configuration.isDebugLoggingEnabled()) {
            System.err.println("[OptimizedHttpClient] All " + maxRetryAttempts + " attempts failed for " + events.size() + " events. Will be queued for retry by HarvestManager.");
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
     * OPTIMIZATION: Stream JSON directly to OutputStream without creating intermediate strings
     * This saves memory and is faster for large event batches
     */
    private void streamJsonToOutputStream(List<Map<String, Object>> events, OutputStream outputStream) throws IOException {
        try {
            byte[] openBracket = "[".getBytes(UTF_8);
            byte[] closeBracket = "]".getBytes(UTF_8);
            byte[] comma = ",".getBytes(UTF_8);

            outputStream.write(openBracket);

            for (int i = 0; i < events.size(); i++) {
                if (i > 0) {
                    outputStream.write(comma);
                }
                streamMapToOutputStream(events.get(i), outputStream);
            }

            outputStream.write(closeBracket);
        } catch (UnsupportedEncodingException e) {
            throw new IOException("UTF-8 encoding not supported", e);
        }
    }

    /**
     * Stream individual map to OutputStream as JSON
     */
    private void streamMapToOutputStream(Map<String, Object> map, OutputStream outputStream) throws IOException {
        try {
            byte[] openBrace = "{".getBytes(UTF_8);
            byte[] closeBrace = "}".getBytes(UTF_8);
            byte[] comma = ",".getBytes(UTF_8);
            byte[] colon = ":".getBytes(UTF_8);
            byte[] quote = "\"".getBytes(UTF_8);

            outputStream.write(openBrace);

            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    outputStream.write(comma);
                }

                // Write key
                outputStream.write(quote);
                outputStream.write(escapeJsonString(entry.getKey()).getBytes(UTF_8));
                outputStream.write(quote);
                outputStream.write(colon);

                // Write value
                Object value = entry.getValue();
                if (value instanceof String) {
                    outputStream.write(quote);
                    outputStream.write(escapeJsonString((String) value).getBytes(UTF_8));
                    outputStream.write(quote);
                } else if (value instanceof Number || value instanceof Boolean) {
                    outputStream.write(value.toString().getBytes(UTF_8));
                } else {
                    outputStream.write(quote);
                    outputStream.write(escapeJsonString(value != null ? value.toString() : "null").getBytes(UTF_8));
                    outputStream.write(quote);
                }
                first = false;
            }

            outputStream.write(closeBrace);
        } catch (UnsupportedEncodingException e) {
            throw new IOException("UTF-8 encoding not supported", e);
        }
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

    private void markDomainFailed() {
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
        final long baseRetryDelayMs = 1000;
        final long maxRetryDelayMs = 30000;

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

    private String escapeJsonString(String str) {
        if (str == null) return "null";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}
