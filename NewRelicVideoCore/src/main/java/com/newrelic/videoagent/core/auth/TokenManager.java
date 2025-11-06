package com.newrelic.videoagent.core.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.device.DeviceInformation;
import com.newrelic.videoagent.core.util.JsonStreamUtil;
import com.newrelic.videoagent.core.utils.NRLog;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Optimized for Android TV and mobile with comprehensive error handling
 * Features:
 * - Thread-safe token generation and caching
 * - Persistent storage with SharedPreferences
 * - Automatic token refresh and validation
 * - Performance optimizations for mobile/TV environments
 * - Comprehensive error handling and fallback strategies
 */
public final class TokenManager {

    // Constants optimized for mobile/TV performance
    private static final String PREFS_NAME = "nr_video_tokens";
    private static final String KEY_APP_TOKEN = "app_token";
    private static final String KEY_TOKEN_TIMESTAMP = "token_timestamp";
    private static final long TOKEN_VALIDITY_MS = 14L * 24 * 60 * 60 * 1000; // 14 days for security
    private static final int CONNECT_TIMEOUT_MS = 15000; // 15 seconds for TV networks
    private static final int READ_TIMEOUT_MS = 30000;    // 30 seconds for TV networks
    private static final int BUFFER_SIZE = 8192;

    // Thread-safe fields
    private final Context context;
    private final NRVideoConfiguration configuration;
    private final AtomicReference<List<Long>> cachedToken = new AtomicReference<>();
    private final AtomicLong lastTokenTime = new AtomicLong(0);
    private final ReentrantReadWriteLock tokenLock = new ReentrantReadWriteLock();
    private final Object tokenGenerationLock = new Object();

    // Immutable fields for performance
    private final String tokenEndpoint;
    private final DeviceInformation deviceInfo;
    private final SharedPreferences prefs;

    public TokenManager(Context context, NRVideoConfiguration configuration) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        this.context = context.getApplicationContext();
        this.configuration = configuration;
        this.tokenEndpoint = buildTokenEndpoint();
        this.deviceInfo = DeviceInformation.getInstance(this.context);
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load cached token on initialization
        loadCachedToken();
    }

    /**
     * Get valid app token with optimized thread-safe access
     * Uses read-write locks for better concurrent performance
     */
    public List<Long> getAppToken() throws IOException {
        // Fast path: read lock for checking cached token
        tokenLock.readLock().lock();
        try {
            List<Long> token = cachedToken.get();
            if (token != null && isTokenValid()) {
                return new ArrayList<>(token); // Return defensive copy
            }
        } finally {
            tokenLock.readLock().unlock();
        }

        // Slow path: generate new token with proper synchronization
        synchronized (tokenGenerationLock) {
            // Double-check pattern
            tokenLock.readLock().lock();
            try {
                List<Long> token = cachedToken.get();
                if (token != null && isTokenValid()) {
                    return new ArrayList<>(token);
                }
            } finally {
                tokenLock.readLock().unlock();
            }

            // Generate new token
            List<Long> newToken = generateAppToken();
            if (newToken != null && !newToken.isEmpty()) {
                tokenLock.writeLock().lock();
                try {
                    cacheToken(newToken);
                    cachedToken.set(newToken);
                    lastTokenTime.set(System.currentTimeMillis());
                    return new ArrayList<>(newToken);
                } finally {
                    tokenLock.writeLock().unlock();
                }
            } else {
                throw new IOException("Failed to generate valid app token");
            }
        }
    }

    /**
     * Refresh token - force generation of new token
     */
    public void refreshToken() throws IOException {
        synchronized (tokenGenerationLock) {
            tokenLock.writeLock().lock();
            try {
                // Clear cached token to force regeneration
                cachedToken.set(null);
                lastTokenTime.set(0);
                clearCachedToken();
                NRLog.d("Token cache cleared, forcing refresh");
            } finally {
                tokenLock.writeLock().unlock();
            }

            // Generate new token
            getAppToken();
        }
    }

    /**
     * Check if current token is still valid
     */
    private boolean isTokenValid() {
        long currentTime = System.currentTimeMillis();
        long tokenAge = currentTime - lastTokenTime.get();
        return tokenAge < TOKEN_VALIDITY_MS;
    }

    /**
     * Build token endpoint URL based on region
     */
    private String buildTokenEndpoint() {
        String region = configuration.getRegion().toUpperCase();
        switch (region) {
            case "EU":
                return "https://mobile-collector.eu.newrelic.com/mobile/v5/connect";
            case "AP":
                return "https://mobile-collector.ap.newrelic.com/mobile/v5/connect";
            case "GOV":
                return "https://mobile-collector.gov.newrelic.com/mobile/v5/connect";
            case "STAGING":
                return "https://mobile-collector.staging.newrelic.com/mobile/v5/connect";
            default:
                return "https://mobile-collector.newrelic.com/mobile/v5/connect";
        }
    }

    /**
     * Generate new app token from New Relic API
     */
    private List<Long> generateAppToken() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(tokenEndpoint);
            connection = (HttpURLConnection) url.openConnection();

            // Configure connection for mobile/TV optimization
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            // Set headers
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", deviceInfo.getUserAgent());
            connection.setRequestProperty("X-App-License-Key", configuration.getApplicationToken());

            // Build request payload
            List<Object> payload = buildTokenRequestPayload();

            // Send request
            try (OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream())) {
                JsonStreamUtil.streamJsonToOutputStream(payload, outputStream);
                outputStream.flush();
            }

            // Process response
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream inputStream = connection.getInputStream()) {
                    return parseTokenResponse(inputStream);
                }
            } else {
                String errorMessage = "Token generation failed with response code: " + responseCode;
                NRLog.e(errorMessage);
                return null;
            }

        } catch (Exception e) {
            NRLog.e("Failed to generate app token: " + e.getMessage(), e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Build comprehensive token request payload
     */
    private List<Object> buildTokenRequestPayload() {
        // Application information
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName(); //package name is application name
        String applicationVersion = "unknown";
        String appname = "unknown";
        try {

            PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
            ApplicationInfo appInfo = packageInfo.applicationInfo;
            appname = pm.getApplicationLabel(appInfo).toString();
            applicationVersion = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            NRLog.w("Failed to get package info: " + e.getMessage());
        }
        List<Object> payload = new ArrayList<>();
        // First array: App information [appName, appVersion, packageName]
        payload.add(Arrays.asList(
                appname,
                applicationVersion,
                packageName
        ));

        // Second array: Device and agent information
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

        return payload;

    }

    /**
     * Parse token response from New Relic API
     */
    private List<Long> parseTokenResponse(InputStream inputStream) {
        // Simple JSON parsing for token response
        // Expected format: {"data_token":[123456,789854],...}
        try {
            // Read response and extract token array
            // This is a simplified implementation - in production, use proper JSON parsing
            byte[] buffer = new byte[BUFFER_SIZE];
            StringBuilder response = new StringBuilder();
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.append(new String(buffer, 0, bytesRead, "UTF-8"));
            }

            String responseStr = response.toString().trim();
            // Expected format: {"data_token":[123456,789854],...}
            String dataTokenKey = "\"data_token\"";
            int dataTokenIndex = responseStr.indexOf(dataTokenKey);

            if (dataTokenIndex == -1) {
                NRLog.w("data_token field not found in response");
                return null;
            }

            // Find the start of the array after "data_token":
            int colonIndex = responseStr.indexOf(':', dataTokenIndex);
            if (colonIndex == -1) {
                return null;
            }

            int arrayStartIndex = responseStr.indexOf('[', colonIndex);
            if (arrayStartIndex == -1) {
                return null;
            }

            // Find the matching closing bracket, not just the first ']'
            int arrayEndIndex = findMatchingCloseBracket(responseStr, arrayStartIndex);
            if (arrayEndIndex == -1) {
                return null;
            }

            // Extract the content between brackets: 123456,789854
            String arrayContent = response.substring(arrayStartIndex + 1, arrayEndIndex).trim();

            List<Long> tokens = new ArrayList<>();
            for (String part: arrayContent.split(",")) {
                try {
                    tokens.add(Long.parseLong(part));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return tokens;

        } catch (Exception e) {
            NRLog.w("Error parsing data_token array in response " + e.getMessage());
            return null;
        }
    }

    /**
     * Find the index of the matching closing bracket for a given opening bracket index
     * Handles nested brackets and different data formats more robustly
     */
    private int findMatchingCloseBracket(String response, int openIndex) {
        int bracketCount = 0;

        for (int i = openIndex; i < response.length(); i++) {
            char c = response.charAt(i);

            if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
                if (bracketCount == 0) {
                    return i; // Matching closing bracket found
                }
            }
        }

        return -1; // No matching closing bracket found
    }

    /**
     * Load cached token from SharedPreferences
     */
    private void loadCachedToken() {
        try {
            String tokenStr = prefs.getString(KEY_APP_TOKEN, null);
            long timestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0);

            if (tokenStr != null && timestamp > 0) {
                List<Long> tokens = parseStoredToken(tokenStr);
                if (tokens != null && !tokens.isEmpty()) {
                    cachedToken.set(tokens);
                    lastTokenTime.set(timestamp);

                    NRLog.d("Loaded cached token from storage");
                }
            }
        } catch (Exception e) {
            NRLog.w("Failed to load cached token: " + e.getMessage());
            clearCachedToken();
        }
    }

    /**
     * Cache token in SharedPreferences
     */
    private void cacheToken(List<Long> tokens) {
        try {
            StringBuilder tokenStr = new StringBuilder();
            for (int i = 0; i < tokens.size(); i++) {
                if (i > 0) tokenStr.append(",");
                tokenStr.append(tokens.get(i));
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_APP_TOKEN, tokenStr.toString());
            editor.putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis());
            editor.apply();

            NRLog.d("Token cached to persistent storage");

        } catch (Exception e) {
            NRLog.e("Failed to cache token: " + e.getMessage(), e);
        }
    }

    /**
     * Parse stored token string back to List<Long>
     */
    private List<Long> parseStoredToken(String tokenStr) {
        try {
            String[] parts = tokenStr.split(",");
            List<Long> tokens = new ArrayList<>();

            for (String part : parts) {
                tokens.add(Long.parseLong(part.trim()));
            }

            return tokens;
        } catch (Exception e) {
            NRLog.w("Failed to parse stored token: " + e.getMessage());
            return null;
        }
    }

    /**
     * Clear cached token from SharedPreferences
     */
    private void clearCachedToken() {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(KEY_APP_TOKEN);
            editor.remove(KEY_TOKEN_TIMESTAMP);
            editor.apply();
        } catch (Exception e) {
            NRLog.w("Failed to clear cached token: " + e.getMessage());
        }
    }

}
