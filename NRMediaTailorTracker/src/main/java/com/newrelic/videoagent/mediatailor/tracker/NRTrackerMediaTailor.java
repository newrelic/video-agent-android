package com.newrelic.videoagent.mediatailor.tracker;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsManifest;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;

import com.newrelic.videoagent.core.tracker.NRVideoTracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AWS MediaTailor Ad Tracker for ExoPlayer
 *
 * Tracks ads from AWS MediaTailor SSAI streams (HLS/DASH)
 *
 * Features:
 * - Client-side ad detection from manifest markers (CUE-OUT/CUE-IN)
 * - Pod-level tracking (multiple ads within one break)
 * - VOD and Live stream support
 * - Tracking API metadata enrichment
 */
public class NRTrackerMediaTailor extends NRVideoTracker implements Player.Listener {

    private static final String TAG = "NRMediaTailorTracker";

    // Stream types
    private static final String STREAM_TYPE_VOD = "vod";
    private static final String STREAM_TYPE_LIVE = "live";

    // Manifest types
    private static final String MANIFEST_TYPE_HLS = "hls";
    private static final String MANIFEST_TYPE_DASH = "dash";

    // Configuration
    private static final long LIVE_POLL_INTERVAL_MS = 10000; // 10 seconds
    private static final int TRACKING_TIMEOUT_MS = 5000;

    protected ExoPlayer player;

    // Stream properties
    private String streamType;
    private String manifestType;
    private String mediaTailorEndpoint;
    private String trackingUrl;
    private String sessionId;

    // Ad tracking state
    private List<AdBreak> adSchedule = new ArrayList<>();
    private AdBreak currentAdBreak;
    private AdPod currentAdPod;
    private boolean hasEndedContent = false;

    // Configuration
    private boolean enableManifestParsing = true;
    private boolean enableTrackingAPI = true;

    // Handlers and timers
    private Handler handler;
    private Runnable pollManifestRunnable;
    private Runnable pollTrackingRunnable;
    private Runnable timeUpdateRunnable;

    // Time update monitoring (equivalent to videoJS timeupdate event)
    private static final long TIME_UPDATE_INTERVAL_MS = 250; // Check every 250ms like videoJS

    /**
     * Checks if tracker should be used for this player source
     */
    public static boolean isUsing(ExoPlayer player) {
        if (player == null || player.getCurrentMediaItem() == null) {
            return false;
        }
        String uri = player.getCurrentMediaItem().localConfiguration.uri.toString();
        return uri != null && uri.contains(".mediatailor.");
    }

    /**
     * Constructor
     */
    public NRTrackerMediaTailor() {
        handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Constructor with player
     */
    public NRTrackerMediaTailor(ExoPlayer player) {
        this();
        setPlayer(player);
    }

    /**
     * Set player and initialize tracking
     */
    @Override
    public void setPlayer(Object player) {
        Log.d(TAG, "setPlayer() called");
        this.player = (ExoPlayer) player;

        if (this.player.getCurrentMediaItem() != null) {
            this.mediaTailorEndpoint = this.player.getCurrentMediaItem().localConfiguration.uri.toString();
            this.manifestType = detectManifestType(this.mediaTailorEndpoint);
            this.trackingUrl = extractTrackingUrl(this.mediaTailorEndpoint);
            this.sessionId = extractSessionId(this.mediaTailorEndpoint);

            Log.d(TAG, "MediaTailor tracker initialized");
            Log.d(TAG, "Manifest type: " + manifestType);
            Log.d(TAG, "Session ID: " + sessionId);
            Log.d(TAG, "Tracking URL: " + trackingUrl);
            Log.d(TAG, "Current player state: " + this.player.getPlaybackState());

            registerListeners();

            // If player is already ready, initialize tracking immediately
            if (this.player.getPlaybackState() == Player.STATE_READY && streamType == null) {
                Log.d(TAG, "Player already in READY state, initializing tracking now");
                long duration = this.player.getDuration();
                streamType = (duration == androidx.media3.common.C.TIME_UNSET) ? STREAM_TYPE_LIVE : STREAM_TYPE_VOD;
                Log.d(TAG, "Stream type detected: " + streamType);
                initializeTracking();
            }
        } else {
            Log.w(TAG, "No current media item available when setPlayer() called");
        }

        super.setPlayer(player);
    }

    /**
     * Register player event listeners
     */
    @Override
    public void registerListeners() {
        if (player != null) {
            player.addListener(this);
            Log.d(TAG, "Event listeners registered");

            // Start time update monitoring (equivalent to videoJS timeupdate event)
            startTimeUpdateMonitoring();
        }
    }

    /**
     * Unregister player event listeners
     */
    @Override
    public void unregisterListeners() {
        if (player != null) {
            player.removeListener(this);
            stopTimeUpdateMonitoring();
            stopLivePolling();
        }
    }

    /**
     * Detect manifest type from URL
     */
    private String detectManifestType(String url) {
        if (url.contains(".m3u8")) {
            return MANIFEST_TYPE_HLS;
        } else if (url.contains(".mpd") || url.contains("/dash")) {
            return MANIFEST_TYPE_DASH;
        }
        return MANIFEST_TYPE_HLS; // Default to HLS
    }

    /**
     * Extract tracking URL from sessionized MediaTailor URL
     */
    private String extractTrackingUrl(String url) {
        // Extract sessionId from URL
        Pattern pattern = Pattern.compile("aws\\.sessionId=([^&]+)");
        Matcher matcher = pattern.matcher(url);

        if (matcher.find()) {
            String sessionId = matcher.group(1);

            // Extract base URL and construct tracking URL
            // Format: https://{cloudfront-id}.mediatailor.{region}.amazonaws.com/v1/tracking/{account-id}/{config}/{sessionId}
            Pattern urlPattern = Pattern.compile("(https://[^/]+)/v1/(?:master|dash)/([^/]+)/([^/]+)/");
            Matcher urlMatcher = urlPattern.matcher(url);

            if (urlMatcher.find()) {
                String baseUrl = urlMatcher.group(1);
                String accountId = urlMatcher.group(2);
                String config = urlMatcher.group(3);

                return baseUrl + "/v1/tracking/" + accountId + "/" + config + "/" + sessionId;
            }
        }

        return null;
    }

    /**
     * Extract sessionId from URL
     */
    private String extractSessionId(String url) {
        Pattern pattern = Pattern.compile("aws\\.sessionId=([^&]+)");
        Matcher matcher = pattern.matcher(url);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Player listener: On playback state changed
     */
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        Log.d(TAG, "onPlaybackStateChanged: " + playbackState + " (streamType=" + streamType + ")");

        if (playbackState == Player.STATE_READY && streamType == null) {
            // Detect stream type based on duration
            long duration = player.getDuration();
            streamType = (duration == androidx.media3.common.C.TIME_UNSET) ? STREAM_TYPE_LIVE : STREAM_TYPE_VOD;

            Log.d(TAG, "Stream type detected: " + streamType + " (duration=" + duration + ")");

            // Initialize tracking based on stream type
            initializeTracking();
        } else if (playbackState == Player.STATE_ENDED) {
            // Player reached end - ad break should have already been exited in onTimeUpdate
            // This is a safety fallback in case the timing check missed it
            if (currentAdBreak != null) {
                Log.d(TAG, "Player ended with active ad break (fallback) - exiting now");
                exitAdBreak();
            }
        }
    }

    /**
     * Initialize tracking based on stream type
     */
    private void initializeTracking() {
        Log.d(TAG, "Initializing " + manifestType.toUpperCase() + " " + streamType.toUpperCase() + " tracking");

        if (streamType.equals(STREAM_TYPE_VOD)) {
            setupVODTracking();
        } else {
            setupLiveTracking();
        }
    }

    /**
     * Setup VOD tracking (single parse, no polling)
     */
    private void setupVODTracking() {
        Log.d(TAG, "VOD mode: Single manifest parse");

        // Parse manifest for ad breaks
        parseManifestForAds();

        // Fetch tracking metadata if available
        if (trackingUrl != null && enableTrackingAPI) {
            fetchTrackingMetadata();
        }
    }

    /**
     * Setup Live tracking (continuous polling)
     */
    private void setupLiveTracking() {
        Log.d(TAG, "Live mode: Continuous polling");

        // Initial parse
        parseManifestForAds();

        // Start polling for new ads
        pollManifestRunnable = new Runnable() {
            @Override
            public void run() {
                parseManifestForAds();
                handler.postDelayed(this, LIVE_POLL_INTERVAL_MS);
            }
        };
        handler.postDelayed(pollManifestRunnable, LIVE_POLL_INTERVAL_MS);

        // Poll tracking API if available
        if (trackingUrl != null && enableTrackingAPI) {
            pollTrackingRunnable = new Runnable() {
                @Override
                public void run() {
                    fetchTrackingMetadata();
                    handler.postDelayed(this, LIVE_POLL_INTERVAL_MS);
                }
            };
            handler.postDelayed(pollTrackingRunnable, LIVE_POLL_INTERVAL_MS);
        }
    }

    /**
     * Stop live polling timers
     */
    private void stopLivePolling() {
        if (pollManifestRunnable != null) {
            handler.removeCallbacks(pollManifestRunnable);
            pollManifestRunnable = null;
        }

        if (pollTrackingRunnable != null) {
            handler.removeCallbacks(pollTrackingRunnable);
            pollTrackingRunnable = null;
        }
    }

    /**
     * Start time update monitoring (equivalent to videoJS timeupdate event)
     * This is the PRIMARY ad detection mechanism - checks currentTime vs ad schedule
     */
    private void startTimeUpdateMonitoring() {
        if (timeUpdateRunnable != null) {
            return; // Already started
        }

        Log.d(TAG, "Starting time update monitoring (checking every " + TIME_UPDATE_INTERVAL_MS + "ms)");

        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isPlaying()) {
                    onTimeUpdate();
                }
                handler.postDelayed(this, TIME_UPDATE_INTERVAL_MS);
            }
        };

        handler.postDelayed(timeUpdateRunnable, TIME_UPDATE_INTERVAL_MS);
    }

    /**
     * Stop time update monitoring
     */
    private void stopTimeUpdateMonitoring() {
        if (timeUpdateRunnable != null) {
            handler.removeCallbacks(timeUpdateRunnable);
            timeUpdateRunnable = null;
            Log.d(TAG, "Stopped time update monitoring");
        }
    }

    /**
     * Called periodically to check for ad break transitions (like videoJS timeupdate)
     * This is the CORE ad detection logic
     */
    private void onTimeUpdate() {
        double currentTime = player.getCurrentPosition() / 1000.0;
        AdBreak activeBreak = findActiveAdBreak(currentTime);

        // Debug logging every 5 seconds
        if (adSchedule.size() > 0 && Math.floor(currentTime) % 5 == 0 && Math.floor(currentTime * 10) % 10 == 0) {
            Log.d(TAG, String.format("TimeUpdate: %.2fs, Active break: %s, Schedule count: %d",
                    currentTime,
                    activeBreak != null ? activeBreak.id : "none",
                    adSchedule.size()));
        }

        // Check if we're near the end of the stream and in an ad break
        // Exit ad break BEFORE content ends to maintain correct viewId
        if (currentAdBreak != null && streamType.equals(STREAM_TYPE_VOD)) {
            long duration = player.getDuration();
            long currentPosition = player.getCurrentPosition();
            // If we're within 100ms of the end, exit the ad break now
            if (duration != androidx.media3.common.C.TIME_UNSET &&
                currentPosition >= duration - 100) {
                Log.d(TAG, "Near end of stream - exiting ad break before CONTENT_END");
                exitAdBreak();
                return;
            }
        }

        if (activeBreak != null) {
            // === INSIDE AD BREAK ===
            if (!activeBreak.hasFiredStart) {
                enterAdBreak(activeBreak);
            }

            // Check for pod transitions within break
            if (currentAdBreak != null) {
                checkPodTransition(activeBreak, currentTime);
            }
        } else if (currentAdBreak != null) {
            // === EXITING AD BREAK ===
            exitAdBreak();
        }
    }

    /**
     * Parse manifest for ad breaks
     * Note: ExoPlayer Media3 doesn't expose HLS playlist directly,
     * so we'll need to track via timeupdate and player events
     */
    private void parseManifestForAds() {
        // TODO: Implement manifest parsing
        // For now, rely on tracking API and timeupdate detection
        Log.d(TAG, "Manifest parsing - relying on tracking API");
    }

    /**
     * Fetch tracking metadata from MediaTailor Tracking API
     */
    private void fetchTrackingMetadata() {
        if (trackingUrl == null) {
            Log.d(TAG, "No tracking URL available");
            return;
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "Fetching tracking metadata from: " + trackingUrl);

                URL url = new URL(trackingUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(TRACKING_TIMEOUT_MS);
                connection.setReadTimeout(TRACKING_TIMEOUT_MS);

                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // Parse tracking data
                    processTrackingMetadata(response.toString());
                } else {
                    Log.w(TAG, "Tracking API returned code: " + responseCode);
                }

                connection.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Error fetching tracking metadata", e);
            }
        }).start();
    }

    /**
     * Process tracking metadata from API
     */
    private void processTrackingMetadata(String jsonData) {
        try {
            JSONObject data = new JSONObject(jsonData);

            if (data.has("avails")) {
                JSONArray avails = data.getJSONArray("avails");
                Log.d(TAG, "Received " + avails.length() + " avail(s) from tracking API");

                for (int i = 0; i < avails.length(); i++) {
                    JSONObject avail = avails.getJSONObject(i);

                    // Parse avail data
                    double startTime = avail.optDouble("startTimeInSeconds", 0);
                    double duration = avail.optDouble("durationInSeconds", 0);
                    String availId = avail.optString("availId", "");

                    Log.d(TAG, String.format("Parsing avail %d: startTime=%.2fs, duration=%.2fs",
                            i, startTime, duration));

                    // Create ad break
                    AdBreak adBreak = new AdBreak();
                    adBreak.id = availId;
                    adBreak.startTime = startTime;
                    adBreak.duration = duration;
                    adBreak.endTime = startTime + duration;
                    adBreak.source = "tracking-api";

                    // Parse ads within avail
                    if (avail.has("ads")) {
                        JSONArray ads = avail.getJSONArray("ads");

                        for (int j = 0; j < ads.length(); j++) {
                            JSONObject ad = ads.getJSONObject(j);

                            double adStartTime = ad.optDouble("startTimeInSeconds", 0);  // Absolute time from stream start
                            double adDuration = ad.optDouble("durationInSeconds", 0);

                            Log.d(TAG, String.format("  Parsing ad %d: startTime=%.2fs, duration=%.2fs, title=%s",
                                    j, adStartTime, adDuration, ad.optString("adTitle", "")));

                            AdPod pod = new AdPod();
                            pod.title = ad.optString("adTitle", "");
                            pod.creativeId = ad.optString("creativeId", "");
                            pod.startTime = adStartTime;  // Use absolute time directly (not offset!)
                            pod.duration = adDuration;
                            pod.endTime = pod.startTime + pod.duration;

                            adBreak.pods.add(pod);
                        }
                    }

                    // Add to schedule
                    adSchedule.add(adBreak);
                }

                // Sort by start time
                adSchedule.sort((a, b) -> Double.compare(a.startTime, b.startTime));

                Log.d(TAG, "Ad schedule updated: " + adSchedule.size() + " ad break(s)");

                // Log detailed schedule for debugging
                for (int i = 0; i < adSchedule.size(); i++) {
                    AdBreak ab = adSchedule.get(i);
                    Log.d(TAG, String.format("  Break %d: %.2fs-%.2fs (%.2fs) - %d pods",
                            i, ab.startTime, ab.endTime, ab.duration, ab.pods.size()));
                    for (int j = 0; j < ab.pods.size(); j++) {
                        AdPod pod = ab.pods.get(j);
                        Log.d(TAG, String.format("    Pod %d: '%s' %.2fs-%.2fs (%.2fs)",
                                j, pod.title, pod.startTime, pod.endTime, pod.duration));
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing tracking metadata", e);
        }
    }

    /**
     * Note: We don't use onPositionDiscontinuity for SSAI ad detection
     *
     * SSAI (Server-Side Ad Insertion) streams have ads seamlessly stitched into the manifest,
     * so there are no position discontinuities. Instead, we use time-based detection:
     * 1. Parse manifest for SCTE-35 markers (CUE-OUT/CUE-IN) to build ad schedule
     * 2. Poll currentTime periodically (every 250ms via onTimeUpdate)
     * 3. Compare currentTime against ad schedule to detect ad breaks
     *
     * This matches the videoJS MediaTailor implementation approach.
     */

    /**
     * Find active ad break for current time
     */
    private AdBreak findActiveAdBreak(double currentTime) {
        for (AdBreak adBreak : adSchedule) {
            if (currentTime >= adBreak.startTime && currentTime < adBreak.endTime) {
                return adBreak;
            }
        }
        return null;
    }

    /**
     * Enter ad break
     */
    private void enterAdBreak(AdBreak adBreak) {
        currentAdBreak = adBreak;
        getState().isAd = true;

        // Determine ad position
        int breakIndex = adSchedule.indexOf(adBreak);
        String adPosition = determineAdPosition(breakIndex, adSchedule.size());
        currentAdBreak.adPosition = adPosition;

        Log.d(TAG, "Entering ad break: " + adBreak.id + " at position " + adPosition);

        // Send AD_BREAK_START
        sendAdBreakStart();

        adBreak.hasFiredStart = true;
    }

    /**
     * Exit ad break
     */
    private void exitAdBreak() {
        if (currentAdPod != null) {
            sendEnd();
            currentAdPod = null;
        }

        Log.d(TAG, "Exiting ad break: " + currentAdBreak.id);

        // Send AD_BREAK_END
        sendAdBreakEnd();
        currentAdBreak.hasFiredEnd = true;
        currentAdBreak = null;

        getState().isAd = false;
    }

    /**
     * Check for pod transitions within ad break and track quartiles
     */
    private void checkPodTransition(AdBreak adBreak, double currentTime) {
        // Debug logging
        if (Math.floor(currentTime * 4) % 4 == 0) { // Log every 250ms aligned
            Log.d(TAG, String.format("checkPodTransition: time=%.2fs, break=%s (%.2fs-%.2fs), %d pods, currentPod=%s",
                    currentTime, adBreak.id, adBreak.startTime, adBreak.endTime,
                    adBreak.pods.size(), currentAdPod != null ? currentAdPod.title : "null"));
        }

        if (adBreak.pods.isEmpty()) {
            // No pods - treat entire break as single ad
            if (!adBreak.hasFiredAdStart) {
                Log.d(TAG, "→ AD_START (no pods)");
                sendRequest();
                sendStart();
                adBreak.hasFiredAdStart = true;
            }

            // Track quartiles for entire break
            double adProgress = currentTime - adBreak.startTime;
            trackQuartiles(adBreak, adProgress);
            return;
        }

        // Find active pod
        AdPod activePod = null;
        for (AdPod pod : adBreak.pods) {
            if (currentTime >= pod.startTime && currentTime < pod.endTime) {
                activePod = pod;
                break;
            }
        }

        if (activePod != null && currentAdPod != activePod) {
            // Transition to new pod
            if (currentAdPod != null) {
                Log.d(TAG, "→ AD_END (pod transition)");
                sendEnd();
            }

            currentAdPod = activePod;
            Log.d(TAG, "→ AD_START (new pod): " + activePod.title);

            sendRequest();
            sendStart();
            activePod.hasFiredStart = true;
        }

        // Track quartiles for active pod
        if (currentAdPod != null) {
            double podProgress = currentTime - currentAdPod.startTime;
            trackQuartiles(currentAdPod, podProgress);
        }
    }

    /**
     * Track quartile events for active ad/pod (like videoJS)
     */
    private void trackQuartiles(Object adObject, double progress) {
        double duration;
        boolean[] fired = new boolean[3];

        if (adObject instanceof AdPod) {
            AdPod pod = (AdPod) adObject;
            duration = pod.duration;
            fired[0] = pod.hasFiredQ1;
            fired[1] = pod.hasFiredQ2;
            fired[2] = pod.hasFiredQ3;
        } else {
            AdBreak adBreak = (AdBreak) adObject;
            duration = adBreak.duration;
            fired[0] = adBreak.hasFiredQ1;
            fired[1] = adBreak.hasFiredQ2;
            fired[2] = adBreak.hasFiredQ3;
        }

        if (duration <= 0) return;

        // Q1 - 25%
        if (!fired[0] && progress >= duration * 0.25) {
            Log.d(TAG, "→ AD_QUARTILE 25%");
            sendAdQuartile();
            if (adObject instanceof AdPod) {
                ((AdPod) adObject).hasFiredQ1 = true;
            } else {
                ((AdBreak) adObject).hasFiredQ1 = true;
            }
        }

        // Q2 - 50%
        if (!fired[1] && progress >= duration * 0.50) {
            Log.d(TAG, "→ AD_QUARTILE 50%");
            sendAdQuartile();
            if (adObject instanceof AdPod) {
                ((AdPod) adObject).hasFiredQ2 = true;
            } else {
                ((AdBreak) adObject).hasFiredQ2 = true;
            }
        }

        // Q3 - 75%
        if (!fired[2] && progress >= duration * 0.75) {
            Log.d(TAG, "→ AD_QUARTILE 75%");
            sendAdQuartile();
            if (adObject instanceof AdPod) {
                ((AdPod) adObject).hasFiredQ3 = true;
            } else {
                ((AdBreak) adObject).hasFiredQ3 = true;
            }
        }
    }

    /**
     * Determine ad position (pre-roll, mid-roll, post-roll)
     */
    private String determineAdPosition(int index, int total) {
        if (index == 0) {
            return "pre-roll";
        } else if (index == total - 1 && streamType.equals(STREAM_TYPE_VOD)) {
            return "post-roll";
        } else {
            return "mid-roll";
        }
    }

    // Tracker metadata overrides

    @Override
    public String getTrackerName() {
        return "aws-media-tailor";
    }

    @Override
    public String getTrackerVersion() {
        return "1.0.0";
    }

    @Override
    public String getPlayerName() {
        return "ExoPlayer";
    }

    @Override
    public String getPlayerVersion() {
        return androidx.media3.common.MediaLibraryInfo.VERSION;
    }

    @Override
    public String getTitle() {
        if (currentAdPod != null && currentAdPod.title != null) {
            return currentAdPod.title;
        }
        if (currentAdBreak != null && currentAdBreak.id != null) {
            return currentAdBreak.id;
        }
        return null;
    }

    @Override
    public String getSrc() {
        return mediaTailorEndpoint;
    }

    @Override
    public Long getDuration() {
        if (currentAdPod != null) {
            return (long) (currentAdPod.duration * 1000);
        }
        if (currentAdBreak != null) {
            return (long) (currentAdBreak.duration * 1000);
        }
        return null;
    }

    @Override
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr = super.getAttributes(action, attributes);

        // Add MediaTailor-specific attributes
        if (sessionId != null) {
            attr.put("sessionId", sessionId);
        }

        if (currentAdBreak != null && currentAdBreak.adPosition != null) {
            attr.put("adPosition", currentAdBreak.adPosition);
        }

        attr.put("adIntegration", "AWS MediaTailor");
        attr.put("streamType", streamType);

        return attr;
    }

    /**
     * Cleanup
     */
    public void dispose() {
        Log.d(TAG, "Disposing MediaTailorAdsTracker");
        stopLivePolling();
        unregisterListeners();
    }

    // Inner classes for ad scheduling

    /**
     * Represents an ad break (avail)
     */
    public static class AdBreak {
        String id;
        double startTime;
        double duration;
        double endTime;
        String source;
        String adPosition;
        boolean hasFiredStart = false;
        boolean hasFiredEnd = false;
        boolean hasFiredAdStart = false;
        boolean hasFiredQ1 = false;
        boolean hasFiredQ2 = false;
        boolean hasFiredQ3 = false;
        List<AdPod> pods = new ArrayList<>();
    }

    /**
     * Represents an individual ad within a break (pod)
     */
    public static class AdPod {
        String title;
        String creativeId;
        double startTime;
        double duration;
        double endTime;
        boolean hasFiredStart = false;
        boolean hasFiredQ1 = false;
        boolean hasFiredQ2 = false;
        boolean hasFiredQ3 = false;
    }
}
