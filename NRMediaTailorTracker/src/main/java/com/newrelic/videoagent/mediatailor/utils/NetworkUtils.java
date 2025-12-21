package com.newrelic.videoagent.mediatailor.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Network Utilities for MediaTailor
 * Handles HTTP requests for manifest and tracking API fetching
 */
public class NetworkUtils {

    private static final String TAG = "MTNetworkUtils";

    /**
     * Callback interface for async fetch operations
     */
    public interface FetchCallback {
        void onSuccess(String response);
        void onError(Exception error);
    }

    /**
     * Fetch text content from a URL (synchronous)
     *
     * @param urlString The URL to fetch
     * @param timeoutMs Connection and read timeout in milliseconds
     * @return Response text, or null on error
     */
    public static String fetchText(String urlString, int timeoutMs) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
                );
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                reader.close();
                connection.disconnect();

                return response.toString();
            } else {
                Log.w(TAG, "HTTP request failed with code: " + responseCode + " for URL: " + urlString);
                connection.disconnect();
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL: " + urlString, e);
            return null;
        }
    }

    /**
     * Fetch text content from a URL (asynchronous)
     *
     * @param urlString The URL to fetch
     * @param timeoutMs Connection and read timeout in milliseconds
     * @param callback Callback to receive result
     */
    public static void fetchTextAsync(final String urlString, final int timeoutMs, final FetchCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String response = fetchText(urlString, timeoutMs);
                    if (response != null) {
                        callback.onSuccess(response);
                    } else {
                        callback.onError(new Exception("HTTP request failed"));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        }).start();
    }

    /**
     * Extract session ID from MediaTailor URL
     * Pattern: ?aws.sessionId=SESSION_ID or /v1/session/SESSION_ID/
     *
     * @param url The MediaTailor URL
     * @return Session ID, or null if not found
     */
    public static String extractSessionId(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // Try query parameter pattern: ?aws.sessionId=...
        if (url.contains("aws.sessionId=")) {
            int start = url.indexOf("aws.sessionId=") + "aws.sessionId=".length();
            int end = url.indexOf("&", start);
            if (end == -1) {
                end = url.length();
            }
            return url.substring(start, end);
        }

        // Try path pattern: /v1/session/SESSION_ID/
        if (url.contains("/v1/session/")) {
            int start = url.indexOf("/v1/session/") + "/v1/session/".length();
            int end = url.indexOf("/", start);
            if (end == -1) {
                end = url.length();
            }
            return url.substring(start, end);
        }

        return null;
    }

    /**
     * Build tracking API URL from MediaTailor session
     * Format: https://{domain}/v1/tracking/{sessionId}
     *
     * @param manifestUrl The MediaTailor manifest URL
     * @param sessionId The session ID
     * @return Tracking API URL, or null if cannot construct
     */
    public static String buildTrackingUrl(String manifestUrl, String sessionId) {
        if (manifestUrl == null || sessionId == null) {
            return null;
        }

        try {
            URL url = new URL(manifestUrl);
            String protocol = url.getProtocol();
            String host = url.getHost();

            return protocol + "://" + host + "/v1/tracking/" + sessionId;
        } catch (Exception e) {
            Log.e(TAG, "Error building tracking URL", e);
            return null;
        }
    }

    /**
     * Detect manifest type from URL
     *
     * @param url The manifest URL
     * @return "hls" for .m3u8, "dash" for .mpd, or null for unknown
     */
    public static String detectManifestType(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        url = url.toLowerCase();

        if (url.contains(".m3u8")) {
            return MediaTailorConstants.MANIFEST_TYPE_HLS;
        } else if (url.contains(".mpd")) {
            return MediaTailorConstants.MANIFEST_TYPE_DASH;
        }

        return null;
    }

    /**
     * Check if URL is a MediaTailor URL
     *
     * @param url The URL to check
     * @return true if URL contains ".mediatailor."
     */
    public static boolean isMediaTailorUrl(String url) {
        return url != null && url.contains(MediaTailorConstants.MT_URL_PATTERN);
    }
}
