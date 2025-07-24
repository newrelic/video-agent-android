package com.newrelic.videoagent.core.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.device.DeviceInformation;
import com.newrelic.videoagent.core.util.JsonStreamUtil;

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
    private static final long TOKEN_VALIDITY_MS = 14L * 24 * 60 * 60 * 1000; // 30 days
    private static final int CONNECT_TIMEOUT_MS = 10000; // 10 seconds
    private static final int READ_TIMEOUT_MS = 15000; // 15 seconds
    private static final int BUFFER_SIZE = 8192; // Increased buffer size for better I/O performance
    private static final String UTF_8 = "UTF-8"; // Compatibility with API level 16

    private final Context context;
    private final NRVideoConfiguration configuration;
    private final AtomicReference<List<Long>> cachedToken = new AtomicReference<>();
    private final ReentrantReadWriteLock tokenLock = new ReentrantReadWriteLock();
    private final Object tokenGenerationLock = new Object(); // Separate lock for token generation
    private volatile long lastTokenTime = 0;
    private final String cachedTokenEndpoint; // Cache endpoint URL
    private final DeviceInformation deviceInfo;

    public TokenManager(Context context, NRVideoConfiguration configuration) {
        this.context = context;
        this.configuration = configuration;
        this.cachedTokenEndpoint = buildTokenEndpoint(); // Cache endpoint on initialization
        this.deviceInfo = DeviceInformation.getInstance(context);
        loadCachedToken();
    }

    /**
     * Get valid app token - returns cached token or generates new one if needed
     * Optimized with read-write locks for better concurrent access
     */
    public List<Long> getAppToken() throws IOException {
        // Fast path: use read lock for checking cached token
        tokenLock.readLock().lock();
        try {
            List<Long> token = cachedToken.get();
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
                List<Long> token = cachedToken.get();
                if (token != null && isTokenValid()) {
                    return token;
                }
            } finally {
                tokenLock.readLock().unlock();
            }

            // Generate and cache new token
            List<Long> newToken = generateAppToken();
            if (newToken != null && !newToken.isEmpty()) {
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
            List<Long> newToken = generateAppToken();
            if (newToken != null && !newToken.isEmpty()) {
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
    private List<Long> generateAppToken() throws IOException {
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
            connection.setRequestProperty("User-Agent", deviceInfo.getUserAgent());
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length()));
            connection.setRequestProperty("X-App-License-Key", configuration.getApplicationToken());

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
                    Log.e("TokenManager", "Token generation failed with code: " + responseCode +
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
            return "https://mobile-collector.eu.newrelic.com/mobile/v5/connect";
        } else {
            return "https://mobile-collector.newrelic.com/mobile/v5/connect";
        }
    }

    /**
     * Build JSON payload for token request using actual app information from Context
     */
    private String buildTokenRequestPayload() throws IOException {
        // Get actual app information from Context
        AppInfo appInfo = getAppInfoFromContext();

        List<Object> payload = new ArrayList<>();

        // First array: App information [appName, appVersion, packageName]
        payload.add(Arrays.asList(
                appInfo.appName,
                appInfo.appVersion,
                appInfo.packageName
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

        // Use the new JsonStreamUtil for optimized JSON generation
        return JsonStreamUtil.streamJsonToString(payload);
    }

    /**
     * Extract actual application information from Android Context
     */
    private AppInfo getAppInfoFromContext() {
        try {
            String packageName = context.getPackageName();
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            } else {
                packageInfo = pm.getPackageInfo(packageName, 0);
            }

            String appVersion = packageInfo.versionName != null ? packageInfo.versionName : "unknown";

            // Get app name from application label
            ApplicationInfo appInfo = packageInfo.applicationInfo;
            String appName = pm.getApplicationLabel(appInfo).toString();

            return new AppInfo(appName, appVersion, packageName);

        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                Log.e("TokenManager", "Failed to get app info from context: " + e.getMessage());
            }
            // Return fallback values
            return new AppInfo("unknown", "unknown", context.getPackageName());
        }
    }

    /**
     * Simple data holder for app information
     */
    private static class AppInfo {
        final String appName;
        final String appVersion;
        final String packageName;

        AppInfo(String appName, String appVersion, String packageName) {
            this.appName = appName;
            this.appVersion = appVersion;
            this.packageName = packageName;
        }
    }

    /**
     * Extract app token from JSON response and return as List of Long
     * Optimized lightweight JSON parsing for mobile/TV performance
     * Avoids JSONObject/JSONArray overhead for better resource usage
     */
    private List<Long> extractTokenFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        // Expected format: {"data_token":[123456,789854],...}
        String dataTokenKey = "\"data_token\"";
        int dataTokenIndex = response.indexOf(dataTokenKey);

        if (dataTokenIndex == -1) {
            if (configuration.isDebugLoggingEnabled()) {
                Log.w("TokenManager", "data_token field not found in response");
            }
            return null;
        }

        // Find the start of the array after "data_token":
        int colonIndex = response.indexOf(':', dataTokenIndex);
        if (colonIndex == -1) {
            return null;
        }

        int arrayStartIndex = response.indexOf('[', colonIndex);
        if (arrayStartIndex == -1) {
            return null;
        }

        int arrayEndIndex = response.indexOf(']', arrayStartIndex);
        if (arrayEndIndex == -1) {
            return null;
        }

        // Extract the content between brackets: 123456,789854
        String arrayContent = response.substring(arrayStartIndex + 1, arrayEndIndex).trim();

        if (arrayContent.isEmpty()) {
            if (configuration.isDebugLoggingEnabled()) {
                Log.w("TokenManager", "Empty data_token array in response");
            }
            return new ArrayList<>();
        }

        return parseTokenArray(arrayContent, "data_token");
    }

    /**
     * Parse comma-separated token array content into List<Long>
     * Optimized for mobile/TV performance with minimal object allocation
     */
    private List<Long> parseTokenArray(String arrayContent, String context) {
        // Pre-allocate ArrayList with estimated capacity to reduce memory allocations
        List<Long> tokenList = new ArrayList<>(4); // Most tokens have 2-4 elements

        // Use StringBuilder for better memory efficiency when parsing tokens
        StringBuilder tokenBuilder = new StringBuilder(16); // Typical long is ~10-15 chars

        // Parse character by character to avoid split() overhead and String[] allocation
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);

            if (c == ',') {
                // End of token - parse accumulated token
                parseAndAddToken(tokenBuilder, tokenList, context);
            } else if (!Character.isWhitespace(c)) {
                // Append non-whitespace characters (digits, etc.)
                tokenBuilder.append(c);
            }
        }

        // Handle the last token (after the final comma or if no commas exist)
        parseAndAddToken(tokenBuilder, tokenList, context);

        return tokenList;
    }

    /**
     * Parse and add a single token from StringBuilder to the token list
     * Handles the common parsing logic to eliminate duplication
     */
    private void parseAndAddToken(StringBuilder tokenBuilder, List<Long> tokenList, String context) {
        if (tokenBuilder.length() > 0) {
            String tokenStr = tokenBuilder.toString().trim();
            if (!tokenStr.isEmpty()) {
                try {
                    long value = Long.parseLong(tokenStr);
                    tokenList.add(value);
                } catch (NumberFormatException e) {
                    if (configuration.isDebugLoggingEnabled()) {
                        Log.w("TokenManager", "Invalid number format in " + context + ": " + tokenStr);
                    }
                }
            }
            tokenBuilder.setLength(0); // Reset StringBuilder for reuse
        }
    }

    /**
     * Load cached token from SharedPreferences
     * Enhanced error handling and validation
     */
    private void loadCachedToken() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String tokenString = prefs.getString(KEY_APP_TOKEN, null);
            long timestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0);

            if (tokenString != null && !tokenString.isEmpty() && timestamp > 0) {
                // Validate timestamp is reasonable (not in future, not too old)
                long currentTime = System.currentTimeMillis();
                if (timestamp <= currentTime && (currentTime - timestamp) <= TOKEN_VALIDITY_MS * 2) {
                    // Parse the cached token string back to List<Long>
                    List<Long> parsedToken = parseTokenFromString(tokenString);
                    if (parsedToken != null && !parsedToken.isEmpty()) {
                        cachedToken.set(parsedToken);
                        lastTokenTime = timestamp;
                    }
                } else if (configuration.isDebugLoggingEnabled()) {
                    Log.w("TokenManager", "Cached token timestamp invalid, ignoring cache");
                }
            }
        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                Log.e("TokenManager", "Failed to load cached token: " + e.getMessage());
            }
        }
    }

    /**
     * Parse token string back to List<Long> for loading from cache
     * Optimized with character-by-character parsing for better TV/mobile performance
     */
    private List<Long> parseTokenFromString(String tokenString) {
        try {
            // Remove brackets and parse content: "[123456, 789854]" -> "123456, 789854"
            if (tokenString.startsWith("[") && tokenString.endsWith("]")) {
                String content = tokenString.substring(1, tokenString.length() - 1).trim();
                if (content.isEmpty()) {
                    return new ArrayList<>();
                }

                return parseTokenArray(content, "cached token");
            }
        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                Log.w("TokenManager", "Failed to parse cached token: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Cache token persistently to SharedPreferences
     * Enhanced with atomic commit operation
     */
    private void cacheToken(List<Long> token) {
        if (token == null || token.isEmpty()) {
            return; // Don't cache invalid tokens
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long currentTime = System.currentTimeMillis();

            // Use commit() instead of apply() for synchronous write to ensure consistency
            boolean success = prefs.edit()
                .putString(KEY_APP_TOKEN, token.toString()) // Convert List<Long> to String
                .putLong(KEY_TOKEN_TIMESTAMP, currentTime)
                .commit();

            if (!success && configuration.isDebugLoggingEnabled()) {
                Log.w("TokenManager", "Failed to commit token to SharedPreferences");
            }

        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                Log.e("TokenManager", "Failed to cache token: " + e.getMessage());
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

}
