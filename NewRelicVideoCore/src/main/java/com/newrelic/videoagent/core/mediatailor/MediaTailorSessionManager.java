package com.newrelic.videoagent.core.mediatailor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.newrelic.videoagent.core.mediatailor.model.Ad;
import com.newrelic.videoagent.core.mediatailor.model.Avail;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorAdBreak;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorSession;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorTrackingData;
import com.newrelic.videoagent.core.mediatailor.model.TrackingEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages MediaTailor session initialization and tracking data fetching.
 * Handles HTTP communication with MediaTailor API and parsing of responses.
 * All network operations run on background threads.
 */
public class MediaTailorSessionManager {
    private static final String TAG = "MediaTailor.Session";
    private static final int CONNECTION_TIMEOUT_MS = 30000; // 30 seconds
    private static final int READ_TIMEOUT_MS = 30000; // 30 seconds
    private static final int TRACKING_FETCH_DELAY_MS = 3000; // 500ms delay before fetching tracking data

    private final DefaultAdsMapper adsMapper;
    private final Handler handler;
    private final ExecutorService executor;

    public MediaTailorSessionManager() {
        this.adsMapper = new DefaultAdsMapper();
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        Log.d(TAG, "MediaTailorSessionManager initialized with background executor");
    }

    /**
     * Initialize MediaTailor session asynchronously on a background thread.
     *
     * @param sessionEndpoint Full session URL
     * @param callback Callback to receive session or null on error
     */
    public void initializeSessionAsync(String sessionEndpoint, SessionCallback callback) {
        Log.d(TAG, "Scheduling session initialization on background thread: " + sessionEndpoint);

        executor.execute(() -> {
            // Network operation runs on background thread
            MediaTailorSession session = initializeSessionSync(sessionEndpoint);

            // Post result back to main thread
            handler.post(() -> callback.onSessionInitialized(session));
        });
    }

    /**
     * Initialize MediaTailor session synchronously (internal use - runs on background thread).
     *
     * @param sessionEndpoint Full session URL
     * @return Session with manifest and tracking URLs, or null on error
     */
    private MediaTailorSession initializeSessionSync(String sessionEndpoint) {
        Log.d(TAG, "Initializing MediaTailor session: " + sessionEndpoint);

        HttpURLConnection connection = null;
        try {
            URL url = new URL(sessionEndpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);

            // Create request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("adsParams", new JSONObject());
            requestBody.put("reportingMode", "client");

            Log.d(TAG, "Request body: " + requestBody.toString());

            // Write request body
            OutputStream os = connection.getOutputStream();
            os.write(requestBody.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            // Read response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String responseBody = response.toString();
                Log.d(TAG, "Response body: " + responseBody);

                // Parse response
                MediaTailorSession session = parseSessionResponse(responseBody);
                Log.d(TAG, "Session initialized successfully: " + session);
                return session;
            } else {
                Log.e(TAG, "Session initialization failed with response code: " + responseCode);
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Network error during session initialization", e);
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "JSON error during session initialization", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Fetch tracking data from MediaTailor asynchronously on a background thread.
     * Waits 500ms before making the request as per MediaTailor requirement.
     *
     * @param trackingUrl Tracking URL from session
     * @param callback Callback to receive ad breaks or empty list on error
     */
    public void fetchTrackingDataAsync(String trackingUrl, TrackingDataCallback callback) {
        Log.d(TAG, "Scheduling tracking data fetch with 500ms delay on background thread");

        executor.execute(() -> {
            try {
                Log.i(TAG, "wait start");
                // Wait 500ms as per MediaTailor requirement
                Thread.sleep(TRACKING_FETCH_DELAY_MS);
                Log.i(TAG, "wait end");

                // Network operation runs on background thread
                List<MediaTailorAdBreak> adBreaks = fetchTrackingDataSync("https://2d271f758c9940e882092aed7e4451c4.mediatailor.ap-southeast-2.amazonaws.com/v1/tracking/54ad5836d26bc9938d9793caa9fe55e611da7d60/my-playback-config/5c565a6f-410a-4d7e-8031-3f986e3104f4?t=1764137423710");
//                List<MediaTailorAdBreak> adBreaks = fetchTrackingDataSync(trackingUrl);

                // Post result back to main thread
                handler.post(() -> callback.onTrackingDataReceived(adBreaks));
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting to fetch tracking data", e);
                handler.post(() -> callback.onTrackingDataReceived(new ArrayList<>()));
            }
        });
    }

    /**
     * Fetch tracking data from MediaTailor synchronously (internal use - runs on background thread).
     *
     * @param trackingUrl Tracking URL from session
     * @return List of mapped ad breaks, or empty list on error
     */
    private List<MediaTailorAdBreak> fetchTrackingDataSync(String trackingUrl) {
        Log.d(TAG, "Fetching tracking data from: " + trackingUrl);

        HttpURLConnection connection = null;
        try {
            // Append timestamp to prevent caching
            String urlWithTimestamp = trackingUrl + (trackingUrl.contains("?") ? "&" : "?") +
                    "t=" + System.currentTimeMillis();

            Log.d(TAG, "Request URL with timestamp: " + urlWithTimestamp);

            URL url = new URL(urlWithTimestamp);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            // Add headers to match MediaTailor API requirements
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("User-Agent", "NewRelic-VideoAgent-Android/1.0");

            // Read response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String responseBody = response.toString();
                Log.d(TAG, "Response body length: " + responseBody.length() + " characters");
                Log.d(TAG, "Response body: " + responseBody);

                // Parse response
                MediaTailorTrackingData trackingData = parseTrackingResponse(responseBody);
                List<MediaTailorAdBreak> adBreaks = adsMapper.mapAdBreaks(trackingData.getAvails());

                Log.d(TAG, "Successfully fetched and mapped " + adBreaks.size() + " ad breaks");
                return adBreaks;
            } else {
                Log.e(TAG, "Tracking data fetch failed with response code: " + responseCode);
                return new ArrayList<>();
            }
        } catch (IOException e) {
            Log.e(TAG, "Network error during tracking data fetch", e);
            return new ArrayList<>();
        } catch (JSONException e) {
            Log.e(TAG, "JSON error during tracking data fetch", e);
            return new ArrayList<>();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Parse session initialization response.
     *
     * @param jsonResponse JSON response string
     * @return MediaTailorSession object
     * @throws JSONException if parsing fails
     */
    private MediaTailorSession parseSessionResponse(String jsonResponse) throws JSONException {
        JSONObject json = new JSONObject(jsonResponse);
        String manifestUrl = json.getString("manifestUrl");
        String trackingUrl = json.getString("trackingUrl");
        return new MediaTailorSession(manifestUrl, trackingUrl);
    }

    /**
     * Parse tracking data response.
     *
     * @param jsonResponse JSON response string
     * @return MediaTailorTrackingData object
     * @throws JSONException if parsing fails
     */
    private MediaTailorTrackingData parseTrackingResponse(String jsonResponse) throws JSONException {
        JSONObject json = new JSONObject(jsonResponse);
        JSONArray availsArray = json.getJSONArray("avails");

        List<Avail> avails = new ArrayList<>();
        for (int i = 0; i < availsArray.length(); i++) {
            JSONObject availJson = availsArray.getJSONObject(i);
            Avail avail = parseAvail(availJson);
            avails.add(avail);
        }

        return new MediaTailorTrackingData(avails);
    }

    /**
     * Parse an Avail from JSON.
     *
     * @param json JSON object representing an avail
     * @return Avail object
     * @throws JSONException if parsing fails
     */
    private Avail parseAvail(JSONObject json) throws JSONException {
        String availId = json.getString("availId");
        double startTimeInSeconds = json.getDouble("startTimeInSeconds");
        double durationInSeconds = json.getDouble("durationInSeconds");
        String duration = json.optString("duration", "");
        double adMarkerDuration = json.optDouble("adMarkerDuration", 0.0);

        List<Ad> ads = new ArrayList<>();
        if (json.has("ads")) {
            JSONArray adsArray = json.getJSONArray("ads");
            for (int i = 0; i < adsArray.length(); i++) {
                JSONObject adJson = adsArray.getJSONObject(i);
                Ad ad = parseAd(adJson);
                ads.add(ad);
            }
        }

        return new Avail(availId, startTimeInSeconds, durationInSeconds, duration, adMarkerDuration, ads);
    }

    /**
     * Parse an Ad from JSON.
     *
     * @param json JSON object representing an ad
     * @return Ad object
     * @throws JSONException if parsing fails
     */
    private Ad parseAd(JSONObject json) throws JSONException {
        String adId = json.getString("adId");
        double startTimeInSeconds = json.getDouble("startTimeInSeconds");
        double durationInSeconds = json.getDouble("durationInSeconds");
        String duration = json.optString("duration", "");

        List<TrackingEvent> trackingEvents = new ArrayList<>();
        if (json.has("trackingEvents")) {
            JSONArray eventsArray = json.getJSONArray("trackingEvents");
            for (int i = 0; i < eventsArray.length(); i++) {
                JSONObject eventJson = eventsArray.getJSONObject(i);
                TrackingEvent event = parseTrackingEvent(eventJson);
                trackingEvents.add(event);
            }
        }

        return new Ad(adId, startTimeInSeconds, durationInSeconds, duration, trackingEvents);
    }

    /**
     * Parse a TrackingEvent from JSON.
     *
     * @param json JSON object representing a tracking event
     * @return TrackingEvent object
     * @throws JSONException if parsing fails
     */
    private TrackingEvent parseTrackingEvent(JSONObject json) throws JSONException {
        String eventId = json.getString("eventId");
        double startTimeInSeconds = json.getDouble("startTimeInSeconds");
        double durationInSeconds = json.optDouble("durationInSeconds", 0.0);
        String eventType = json.getString("eventType");

        List<String> beaconUrls = new ArrayList<>();
        if (json.has("beaconUrls")) {
            JSONArray urlsArray = json.getJSONArray("beaconUrls");
            for (int i = 0; i < urlsArray.length(); i++) {
                beaconUrls.add(urlsArray.getString(i));
            }
        }

        return new TrackingEvent(eventId, startTimeInSeconds, durationInSeconds, eventType, beaconUrls);
    }

    /**
     * Shutdown the executor service and clean up resources.
     * Call this when the session manager is no longer needed.
     */
    public void shutdown() {
        Log.d(TAG, "Shutting down MediaTailorSessionManager");
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    /**
     * Callback interface for asynchronous session initialization.
     */
    public interface SessionCallback {
        void onSessionInitialized(MediaTailorSession session);
    }

    /**
     * Callback interface for asynchronous tracking data fetching.
     */
    public interface TrackingDataCallback {
        void onTrackingDataReceived(List<MediaTailorAdBreak> adBreaks);
    }
}
