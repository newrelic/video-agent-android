package com.newrelic.videoagent.core.auth;

import android.content.Context;
import android.content.SharedPreferences;
import com.newrelic.videoagent.core.NRVideoConfiguration;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages New Relic app token generation and persistent caching
 * - Generates app token from application token via New Relic API
 * - Caches token persistently in SharedPreferences
 * - Handles token refresh when expired
 * - Thread-safe operations for concurrent access
 * - Optimized for performance and resource usage
 */
public class TokenManager {

    private static final String PREFS_NAME = "nr_video_tokens";
    private static final String KEY_APP_TOKEN = "app_token";
    private static final String KEY_TOKEN_TIMESTAMP = "token_timestamp";
    private static final long TOKEN_VALIDITY_MS = 30L * 24 * 60 * 60 * 1000; // 30 days
    private static final int CONNECT_TIMEOUT_MS = 10000; // 10 seconds
    private static final int READ_TIMEOUT_MS = 15000; // 15 seconds
    private static final int BUFFER_SIZE = 8192; // Increased buffer size for better I/O performance
    private static final String UTF_8 = "UTF-8"; // Compatibility with API level 16

    private final Context context;
    private final NRVideoConfiguration configuration;
    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private final ReentrantReadWriteLock tokenLock = new ReentrantReadWriteLock();
    private final Object tokenGenerationLock = new Object(); // Separate lock for token generation
    private volatile long lastTokenTime = 0;
    private final String cachedTokenEndpoint; // Cache endpoint URL

    public TokenManager(Context context, NRVideoConfiguration configuration) {
        this.context = context;
        this.configuration = configuration;
        this.cachedTokenEndpoint = buildTokenEndpoint(); // Cache endpoint on initialization
        loadCachedToken();
    }

    /**
     * Get valid app token - returns cached token or generates new one if needed
     * Optimized with read-write locks for better concurrent access
     */
    public String getAppToken() throws IOException {
        // Fast path: use read lock for checking cached token
        tokenLock.readLock().lock();
        try {
            String token = cachedToken.get();
            if (token != null && isTokenValid()) {
                return token;
            }
        } finally {
            tokenLock.readLock().unlock();
        }

        // Slow path: generate new token with separate synchronization
        synchronized (tokenGenerationLock) {
            // Double-check pattern with read lock
            tokenLock.readLock().lock();
            try {
                String token = cachedToken.get();
                if (token != null && isTokenValid()) {
                    return token;
                }
            } finally {
                tokenLock.readLock().unlock();
            }

            // Generate and cache new token
            String newToken = generateAppToken();
            if (newToken != null) {
                tokenLock.writeLock().lock();
                try {
                    cacheToken(newToken);
                    cachedToken.set(newToken);
                    lastTokenTime = System.currentTimeMillis();
                } finally {
                    tokenLock.writeLock().unlock();
                }
            }

            return newToken;
        }
    }

    /**
     * Force token refresh - useful when token becomes invalid
     */
    public void refreshToken() throws IOException {
        synchronized (tokenGenerationLock) {
            String newToken = generateAppToken();
            if (newToken != null) {
                tokenLock.writeLock().lock();
                try {
                    cacheToken(newToken);
                    cachedToken.set(newToken);
                    lastTokenTime = System.currentTimeMillis();
                } finally {
                    tokenLock.writeLock().unlock();
                }
            }
        }
    }

    /**
     * Generate app token from application token via New Relic API
     * Optimized with better resource management and error handling
     */
    private String generateAppToken() throws IOException {
        String payload = buildTokenRequestPayload();
        URL url = new URL(cachedTokenEndpoint); // Use cached endpoint
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();

            // Configure connection for token request
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false); // Disable caching for auth requests

            // Set headers
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "NewRelic-VideoAgent-Android/1.0");
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length()));

            // Send request with proper resource management
            try (OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream(), BUFFER_SIZE)) {
                outputStream.write(payload.getBytes(UTF_8));
                outputStream.flush();
            }

            // Read response
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream responseStream = connection.getInputStream()) {
                    String response = readInputStream(responseStream);
                    return extractTokenFromResponse(response);
                }
            } else {
                // Read error stream for better debugging
                String errorMessage = null;
                try (InputStream errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        errorMessage = readInputStream(errorStream);
                    }
                } catch (IOException ignored) {
                    // Ignore errors reading error stream
                }

                if (configuration.isDebugLoggingEnabled()) {
                    System.err.println("[TokenManager] Token generation failed with code: " + responseCode +
                                     (errorMessage != null ? ", error: " + errorMessage : ""));
                }

                // Throw specific exception for better error handling upstream
                throw new IOException("Token generation failed with HTTP " + responseCode +
                                    (errorMessage != null ? ": " + errorMessage : ""));
            }

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Build token endpoint URL based on region (cached for performance)
     */
    private String buildTokenEndpoint() {
        String region = configuration.getRegion().toUpperCase();

        if ("EU".equals(region)) {
            return "https://mobile-collector.eu.newrelic.com/mobile/v1/token";
        } else {
            return "https://mobile-collector.newrelic.com/mobile/v1/token";
        }
    }

    /**
     * Build JSON payload for token request
     */
    private String buildTokenRequestPayload() {
        return String.format(
            "{\"applicationToken\":\"%s\",\"platform\":\"android\",\"agent\":\"video\"}",
            escapeJsonString(configuration.getApplicationToken())
        );
    }

    /**
     * Extract app token from JSON response
     * Optimized simple JSON parsing to avoid external dependencies
     */
    private String extractTokenFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        // Look for "appToken":"..." pattern in JSON response
        String tokenKey = "\"appToken\":\"";
        int startIndex = response.indexOf(tokenKey);
        if (startIndex == -1) {
            return null;
        }

        startIndex += tokenKey.length();
        int endIndex = response.indexOf('"', startIndex);
        if (endIndex == -1) {
            return null;
        }

        String token = response.substring(startIndex, endIndex);
        return token.isEmpty() ? null : token; // Validate token is not empty
    }

    /**
     * Load cached token from SharedPreferences
     * Enhanced error handling and validation
     */
    private void loadCachedToken() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String token = prefs.getString(KEY_APP_TOKEN, null);
            long timestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0);

            if (token != null && !token.isEmpty() && timestamp > 0) {
                // Validate timestamp is reasonable (not in future, not too old)
                long currentTime = System.currentTimeMillis();
                if (timestamp <= currentTime && (currentTime - timestamp) <= TOKEN_VALIDITY_MS * 2) {
                    cachedToken.set(token);
                    lastTokenTime = timestamp;
                } else if (configuration.isDebugLoggingEnabled()) {
                    System.err.println("[TokenManager] Cached token timestamp invalid, ignoring cache");
                }
            }
        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                System.err.println("[TokenManager] Failed to load cached token: " + e.getMessage());
            }
        }
    }

    /**
     * Cache token persistently to SharedPreferences
     * Enhanced with atomic commit operation
     */
    private void cacheToken(String token) {
        if (token == null || token.isEmpty()) {
            return; // Don't cache invalid tokens
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long currentTime = System.currentTimeMillis();

            // Use commit() instead of apply() for synchronous write to ensure consistency
            boolean success = prefs.edit()
                .putString(KEY_APP_TOKEN, token)
                .putLong(KEY_TOKEN_TIMESTAMP, currentTime)
                .commit();

            if (!success && configuration.isDebugLoggingEnabled()) {
                System.err.println("[TokenManager] Failed to commit token to SharedPreferences");
            }

        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                System.err.println("[TokenManager] Failed to cache token: " + e.getMessage());
            }
        }
    }

    /**
     * Check if current token is valid (not expired)
     */
    private boolean isTokenValid() {
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastTokenTime) <= TOKEN_VALIDITY_MS;
    }

    /**
     * Read InputStream to String with optimized buffer size
     */
    private String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder result = new StringBuilder();
        byte[] buffer = new byte[BUFFER_SIZE]; // Increased buffer size
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            result.append(new String(buffer, 0, bytesRead, UTF_8));
        }

        return result.toString();
    }

    /**
     * Escape JSON string values with StringBuilder for better performance
     */
    private String escapeJsonString(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        // Pre-allocate StringBuilder with estimated capacity
        StringBuilder result = new StringBuilder(str.length() + 16);

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\':
                    result.append("\\\\");
                    break;
                case '"':
                    result.append("\\\"");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    result.append(c);
                    break;
            }
        }

        return result.toString();
    }

    /**
     * Clear cached token (useful for logout/reset scenarios)
     * Enhanced with proper locking
     */
    public void clearToken() {
        tokenLock.writeLock().lock();
        try {
            cachedToken.set(null);
            lastTokenTime = 0;

            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean success = prefs.edit().clear().commit();

                if (!success && configuration.isDebugLoggingEnabled()) {
                    System.err.println("[TokenManager] Failed to clear SharedPreferences");
                }
            } catch (Exception e) {
                if (configuration.isDebugLoggingEnabled()) {
                    System.err.println("[TokenManager] Failed to clear token: " + e.getMessage());
                }
            }
        } finally {
            tokenLock.writeLock().unlock();
        }
    }

    /**
     * Check if token manager has a valid cached token without network call
     */
    public boolean hasValidToken() {
        tokenLock.readLock().lock();
        try {
            return cachedToken.get() != null && isTokenValid();
        } finally {
            tokenLock.readLock().unlock();
        }
    }
}
