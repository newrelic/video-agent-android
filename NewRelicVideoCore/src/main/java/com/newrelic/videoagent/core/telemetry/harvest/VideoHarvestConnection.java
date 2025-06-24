package com.newrelic.videoagent.core.telemetry.harvest;

import com.newrelic.videoagent.core.telemetry.configuration.VideoAgentConfiguration;
import com.newrelic.videoagent.core.utils.NRLog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class VideoHarvestConnection {
    private static final int MAX_RETRIES = 3;
    private static final String TAG = "VideoHarvestConnection";
    private static final long RETRY_DELAY_MS = TimeUnit.SECONDS.toMillis(5); // 5 seconds

    private VideoAgentConfiguration configuration;

    // Direct endpoint URL as per the provided curl command
    private static final String COLLECTOR_ENDPOINT_URL = "https://staging-mobile-collector.newrelic.com/mobile/v3/data";

    public VideoHarvestConnection(VideoAgentConfiguration configuration) {
        this.configuration = configuration;
        NRLog.d("VideoHarvestConnection initialized. Collector endpoint: " + COLLECTOR_ENDPOINT_URL);
    }

    /**
     * Sends the provided harvest payload to the New Relic collector.
     * Implements a retry mechanism for transient network failures.
     * @param payload The JSON string payload to send.
     * @return true if data was successfully sent, false otherwise after retries.
     */
    public boolean sendData(String payload) {
        if (payload == null || payload.isEmpty()) {
            NRLog.d("Attempted to send empty payload.");
            return false;
        }
        NRLog.d("Attempting to send payload of size: " + payload.length() + " bytes.");

        for (int retryCount = 0; retryCount < MAX_RETRIES; retryCount++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(COLLECTOR_ENDPOINT_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json"); // Explicitly set as application/json
                conn.setRequestProperty("Accept", "application/json");

                // Add New Relic specific headers from the curl command
                conn.setRequestProperty("X-NewRelic-OS-Name", "Android");
                // Using the application token from the configuration (which is now hardcoded to match the curl's key)
                conn.setRequestProperty("X-App-License-Key", configuration.getApplicationToken());
                conn.setRequestProperty("X-NewRelic-App-Version", "1.0"); // From curl
                conn.setRequestProperty("X-NewRelic-ID", "VwEFU1NaABAGVFBRAQUHU1w="); // From curl

                conn.setDoOutput(true);
                conn.setConnectTimeout(10000); // 10 seconds
                conn.setReadTimeout(10000);    // 10 seconds

                // Write the payload to the output stream
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                NRLog.d("HTTP Response Code: " + responseCode);

                if (responseCode >= 200 && responseCode < 300) {
                    // NEW: Log successful data transmission
                    NRLog.d("Successfully sent data to New Relic Mobile Collector! Response Code: " + responseCode);
                    // Read response if necessary (e.g., for data tokens or harvest configuration)
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        NRLog.d("Server Response: " + response.toString());
                        // In a full implementation, you might parse this response for harvest settings.
                    }
                    return true; // Success
                } else if (responseCode >= 500 || responseCode == 408 || responseCode == 429) {
                    // Server error, request timeout, or too many requests - retry
                    NRLog.d("Server error or transient issue (" + responseCode + "). Retrying in " + RETRY_DELAY_MS + "ms.");
                    Thread.sleep(RETRY_DELAY_MS);
                } else {
                    // Client error or unrecoverable server error - do not retry
                    NRLog.e("Failed to send payload. HTTP Response Code: " + responseCode);
                    return false;
                }

            } catch (Exception e) {
                // Modified: NRLog.e no longer takes Throwable, logging message only
                NRLog.e("Network error during data transmission (retry " + (retryCount + 1) + "/" + MAX_RETRIES + "): " + e.getMessage());
                // For network-related exceptions (e.g., IOException, UnknownHostException), retry
                if (retryCount < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        NRLog.d("Retry delay interrupted.");
                        return false;
                    }
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        NRLog.e("Failed to send payload after " + MAX_RETRIES + " retries.");
        return false; // All retries failed
    }

    /**
     * Updates the connection configuration.
     * @param newConfiguration The new VideoAgentConfiguration to use.
     */
    public void updateConfiguration(VideoAgentConfiguration newConfiguration) {
        this.configuration = newConfiguration;
        NRLog.d("VideoHarvestConnection configuration updated (license key implicitly updated).");
    }
}
