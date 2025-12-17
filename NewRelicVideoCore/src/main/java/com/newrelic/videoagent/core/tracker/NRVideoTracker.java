package com.newrelic.videoagent.core.tracker;

import android.os.Handler;

import com.newrelic.videoagent.core.model.NRTimeSince;
import com.newrelic.videoagent.core.model.NRTrackerState;
import com.newrelic.videoagent.core.utils.NRLog;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.newrelic.videoagent.core.NRDef.*;
import com.newrelic.videoagent.core.exception.ErrorExceptionHandler;

/**
 * `NRVideoTracker` defines the basic behaviour of a video tracker.
 */
public class NRVideoTracker extends NRTracker {


    private static final int CONTENT_HEARTBEAT_INTERVAL_SEC = 30;
    private static final int AD_HEARTBEAT_INTERVAL_SEC = 2;
    public final NRTrackerState state;

    private final Handler heartbeatHandler;
    private final Runnable heartbeatRunnable;
    private Boolean isHeartbeatRunning;
    private Integer numberOfVideos;
    private Integer numberOfAds;
    private Integer numberOfErrors;
    private final String viewSessionId;
    private Integer viewIdIndex;
    private Integer adBreakIdIndex;
    private Long playtimeSinceLastEventTimestamp;
    private Long totalPlaytime;
    private Long totalAdPlaytime;
    private Long playtimeSinceLastEvent;
    private String bufferType;
    private NRTimeSince lastAdTimeSince;

    // QoE (Quality of Experience) tracking fields
    private Long qoePeakBitrate;
    private Boolean qoeHadPlaybackFailure;
    private Long qoeTotalRebufferingTime;
    private Long qoeBitrateSum;
    private Long qoeBitrateCount;
    private Long qoeLastTrackedBitrate;
    private Long qoeStartupTime; // Cached startup time, calculated once per view session

    // Startup time calculation fields - capture actual event timestamps
    private Long contentRequestTimestamp;
    private Long contentStartTimestamp;

    // Time-weighted bitrate calculation fields
    private Long qoeCurrentBitrate;
    private Long qoeLastRenditionChangeTime;
    private Long qoeTotalBitrateWeightedTime;
    private Long qoeTotalActiveTime;

    /**
     * Create a new NRVideoTracker.
     */
    public NRVideoTracker() {
        state = new NRTrackerState();
        numberOfAds = 0;
        numberOfErrors = 0;
        numberOfVideos = 0;
        viewIdIndex = 0;
        adBreakIdIndex = 0;
        viewSessionId = getAgentSession() + "-" + (System.currentTimeMillis() / 1000) + new Random().nextInt(10);
        playtimeSinceLastEventTimestamp = 0L;
        totalPlaytime = 0L;
        totalAdPlaytime = 0L;
        playtimeSinceLastEvent = 0L;
        bufferType = null;
        isHeartbeatRunning = false;

        // Initialize QoE tracking fields
        qoePeakBitrate = 0L;
        qoeHadPlaybackFailure = false;
        qoeTotalRebufferingTime = 0L;
        qoeBitrateSum = 0L;
        qoeBitrateCount = 0L;
        qoeLastTrackedBitrate = null;
        qoeStartupTime = null; // Will be calculated during first QOE_AGGREGATE event

        // Initialize startup time calculation fields
        contentRequestTimestamp = null;
        contentStartTimestamp = null;

        // Initialize time-weighted bitrate tracking
        qoeCurrentBitrate = null;
        qoeLastRenditionChangeTime = null;
        qoeTotalBitrateWeightedTime = 0L;
        qoeTotalActiveTime = 0L;
        heartbeatHandler = new Handler();
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHeartbeatRunning) {
                    sendHeartbeat();
                    heartbeatHandler.postDelayed(heartbeatRunnable, getHeartbeatIntervalMillis());
                }
            }
        };
    }

    /**
     * Dispose of the tracker.
     *
     * Stop heartbeats and call `super.dispose()`.
     */
    public void dispose() {
        super.dispose();
        stopHeartbeat();
    }

    /**
     * Set player.
     *
     * @param player Player instance.
     */
    public void setPlayer(Object player) {
        sendVideoEvent(PLAYER_READY);
        state.goPlayerReady();
    }

    /**
     * Start heartbeat timer.
     */
    public void startHeartbeat() {
        NRLog.d("START HEARTBEAT");
        isHeartbeatRunning = true;
        heartbeatHandler.postDelayed(heartbeatRunnable, getHeartbeatIntervalMillis());
    }

    /**
     * Stop heartbeat timer.
     */
    public void stopHeartbeat() {
        NRLog.d("STOP HEARTBEAT");
        isHeartbeatRunning = false;
        heartbeatHandler.removeCallbacks(heartbeatRunnable, null);
    }

    /**
     * Set number of ads.
     *
     * @param numberOfAds Number of ads.
     */
    public void setNumberOfAds(int numberOfAds) {
        this.numberOfAds = numberOfAds;
    }

    /**
     * Return tracker state.
     *
     * @return Tracker state..
     */
    public NRTrackerState getState() {
        return state;
    }

    /**
     * Generate attributes for a given action.
     *
     * Generate all video related attributes.
     *
     * @param action Action being generated.
     * @param attributes Specific attributes sent along the action.
     * @return Map of attributes.
     */
    @Override
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr;

        if (attributes != null) {
            attr = new HashMap<>(attributes);
        } else {
            attr = new HashMap<>();
        }

        if (action.endsWith("_BUFFER_START") || action.endsWith("_BUFFER_END")) {
            attr.put("bufferType", getBufferType());
        }

        attr.put("trackerName", getTrackerName());
        attr.put("trackerVersion", getTrackerVersion());
        attr.put("src", getTrackerSrc());
        attr.put("playerName", getPlayerName());
        attr.put("playerVersion", getPlayerVersion());
        attr.put("viewSession", getViewSession());
        attr.put("viewId", getViewId());
        attr.put("numberOfAds", numberOfAds);
        attr.put("numberOfVideos", numberOfVideos);
        attr.put("numberOfErrors", numberOfErrors);
//        attr.put("elapsedTime", playtimeSinceLastEvent);
        attr.put("totalPlaytime", totalPlaytime);

        if (state.isAd) {
            attr.put("adTitle", getTitle());
            attr.put("adBitrate", getBitrate());
            attr.put("adRenditionBitrate", getRenditionBitrate());
            attr.put("adRenditionWidth", getRenditionWidth());
            attr.put("adRenditionHeight", getRenditionHeight());
            attr.put("adDuration", getDuration());
            attr.put("adPlayhead", getPlayhead());
            attr.put("adLanguage", getLanguage());
            attr.put("adSrc", getSrc());
            attr.put("adIsMuted", getIsMuted());
            attr.put("adFps", getFps());
            attr.put("adId", getVideoId());
            attr.put("adCreativeId",getAdCreativeId());
            attr.put("adPosition", getAdPosition());
            attr.put("adQuartile", getAdQuartile());
            attr.put("adPartner", getAdPartner());
            attr.put("adBreakId", getAdBreakId());

            if (action.startsWith("AD_BREAK_")) {
                if (linkedTracker instanceof NRVideoTracker) {
                    Long playhead = ((NRVideoTracker) linkedTracker).getPlayhead();
                    if (playhead < 100) {
                        attr.put("adPosition", "pre");
                    }
                }
            }

            if (action.equals(AD_BREAK_END)) {
                attr.put("totalAdPlaytime", totalAdPlaytime);
            }
        }
        else {
            if (action.equals(CONTENT_START)) {
                attr.put("totalAdPlaytime", totalAdPlaytime);
            }

            attr.put("contentTitle", getTitle());
//            attr.put("contentBitrate", getBitrate());
            attr.put("contentBitrate", getActualBitrate());
            attr.put("contentRenditionBitrate", getRenditionBitrate());
            attr.put("contentRenditionWidth", getRenditionWidth());
            attr.put("contentRenditionHeight", getRenditionHeight());
            attr.put("contentDuration", getDuration());
            attr.put("contentPlayhead", getPlayhead());
            attr.put("contentLanguage", getLanguage());
            attr.put("contentSrc", getSrc());
            attr.put("contentIsMuted", getIsMuted());
            attr.put("contentFps", getFps());
            attr.put("contentId", getVideoId());
            attr.put("contentIsLive", getIsLive());
        }
        attr = super.getAttributes(action, attr);


        // QoE: Track bitrate after all attributes are processed (including contentBitrate)
        if (!state.isAd && !QOE_AGGREGATE.equals(action)) {
            trackBitrateFromProcessedAttributes(action, attr);
        }

        return attr;
    }

    public void updatePlaytime() {
        if (playtimeSinceLastEventTimestamp > 0) {
            playtimeSinceLastEvent = System.currentTimeMillis() - playtimeSinceLastEventTimestamp;
            totalPlaytime += playtimeSinceLastEvent;
            playtimeSinceLastEventTimestamp = System.currentTimeMillis();
        } else {
            playtimeSinceLastEvent = 0L;
        }
    }

    /**
     * Send request event.
     */
    public void sendRequest() {
        if (state.goRequest()) {
            playtimeSinceLastEventTimestamp = 0L;

            if (state.isAd) {
                sendVideoAdEvent(AD_REQUEST);
            } else {
                // QoE: Capture CONTENT_REQUEST timestamp for startup time calculation
                if (contentRequestTimestamp == null) {
                    contentRequestTimestamp = System.currentTimeMillis();
                }
                sendVideoEvent(CONTENT_REQUEST);
            }
        }
    }

    /**
     * Send start event.
     */
    public void sendStart() {
        if (state.goStart()) {
            startHeartbeat();
            if (state.isAd) {
                // Start ad chrono for precise ad duration tracking
                state.adChrono.start();
                numberOfAds++;
                ((NRVideoTracker) linkedTracker).sendPause ();
                if (linkedTracker instanceof NRVideoTracker) {
                    ((NRVideoTracker) linkedTracker).setNumberOfAds(numberOfAds);
                }
                sendVideoAdEvent(AD_START);
            } else {
                // Start content chrono for content watch time
                state.chrono.start();
                if (linkedTracker instanceof NRVideoTracker) {
                    totalAdPlaytime = ((NRVideoTracker)linkedTracker).getTotalAdPlaytime();
                }
                numberOfVideos++;

                // QoE: Capture CONTENT_START timestamp
                if (contentStartTimestamp == null) {
                    contentStartTimestamp = System.currentTimeMillis();
                }

                sendVideoEvent(CONTENT_START);
            }
            playtimeSinceLastEventTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Send pause event.
     */
    public void sendPause() {
        if (state.goPause()) {
            if(!state.isBuffering){
                if (state.isAd) {
                    // Accumulate ad watch time using ad chrono
                    state.accumulatedAdWatchTime += state.adChrono.getDeltaTime();
                } else {
                    state.accumulatedVideoWatchTime += state.chrono.getDeltaTime();
                }
            }
            if (state.isAd) {
                sendVideoAdEvent(AD_PAUSE);
            } else {
                sendVideoEvent(CONTENT_PAUSE);
            }
            playtimeSinceLastEventTimestamp = 0L;
        }
    }

    /**
     * Send resume event.
     */
    public void sendResume() {
        if (state.goResume()) {
            if(!state.isBuffering){
                if (state.isAd) {
                    // Resume ad chrono for ad duration tracking
                    state.adChrono.start();
                } else {
                    state.chrono.start();
                }
            }
            if (state.isAd) {
                sendVideoAdEvent(AD_RESUME);
            } else {
                sendVideoEvent(CONTENT_RESUME);
            }
            if (!state.isBuffering && !state.isSeeking) {
                playtimeSinceLastEventTimestamp = System.currentTimeMillis();
            }
        }
    }

    /**
     * Send end event.
     */
    public void sendEnd() {
        if (state.goEnd()) {
            if (state.isAd) {
                sendVideoAdEvent(AD_END);
                ((NRVideoTracker) linkedTracker).sendResume();
                if (linkedTracker instanceof NRVideoTracker) {
                    ((NRVideoTracker) linkedTracker).adHappened();
                }
                totalAdPlaytime = totalAdPlaytime + totalPlaytime;
            } else {
                sendVideoEvent(CONTENT_END);
            }

            stopHeartbeat();

            viewIdIndex++;
            numberOfErrors = 0;
            playtimeSinceLastEventTimestamp = 0L;
            playtimeSinceLastEvent = 0L;
            totalPlaytime = 0L;

            // Reset QoE metrics for new view session
            resetQoeMetrics();
        }
    }

    /**
     * Send seek start event.
     */
    public void sendSeekStart() {
        if (state.goSeekStart()) {
            if (state.isAd) {
                sendVideoAdEvent(AD_SEEK_START);
            } else {
                sendVideoEvent(CONTENT_SEEK_START);
            }
            playtimeSinceLastEventTimestamp = 0L;
        }
    }

    /**
     * Send seek end event.
     */
    public void sendSeekEnd() {
        if (state.goSeekEnd()) {
            if (state.isAd) {
                sendVideoAdEvent(AD_SEEK_END);
            } else {
                sendVideoEvent(CONTENT_SEEK_END);
            }
            if (!state.isBuffering && !state.isPaused) {
                playtimeSinceLastEventTimestamp = System.currentTimeMillis();
            }
        }
    }

    /**
     * Send buffer start event.
     */
    public void sendBufferStart() {
        if (state.goBufferStart()) {
            if(state.isPlaying){
                if (state.isAd) {
                    // Accumulate ad watch time using ad chrono
                    state.accumulatedAdWatchTime += state.adChrono.getDeltaTime();
                } else {
                    state.accumulatedVideoWatchTime += state.chrono.getDeltaTime();
                }
            }
            bufferType = calculateBufferType();
            if (state.isAd) {
                sendVideoAdEvent(AD_BUFFER_START);
            } else {
                sendVideoEvent(CONTENT_BUFFER_START);
            }
            playtimeSinceLastEventTimestamp = 0L;
        }
    }

    /**
     * Send buffer end event.
     */
    public void sendBufferEnd() {
        if (state.goBufferEnd()) {
            if(state.isPlaying){
                if (state.isAd) {
                    // Resume ad chrono after buffer ends
                    state.adChrono.start();
                } else {
                    state.chrono.start();
                }
            }
            if (bufferType == null) {
                bufferType = calculateBufferType();
            }
            if (state.isAd) {
                sendVideoAdEvent(AD_BUFFER_END);
            } else {
                // QoE: Calculate rebuffering time using timeSinceBufferBegin (excludes initial buffering)
                Map<String, Object> attributes = getAttributes(CONTENT_BUFFER_END, null);
                Object timeSinceBufferBegin = attributes.get("timeSinceBufferBegin");
                if (timeSinceBufferBegin instanceof Long && !bufferType.equals("initial")) {
                    qoeTotalRebufferingTime += (Long) timeSinceBufferBegin;
                }
                sendVideoEvent(CONTENT_BUFFER_END);
            }
            if (!state.isSeeking && !state.isPaused) {
                playtimeSinceLastEventTimestamp = System.currentTimeMillis();
            }
            bufferType = null;
        }
    }

    /**
     * Send heartbeat event.
     */
    public void sendHeartbeat() {
        long heartbeatInterval = getHeartbeatIntervalMillis();
        if(state.isPlaying){
            state.accumulatedVideoWatchTime += state.chrono.getDeltaTime();
        }
        state.accumulatedVideoWatchTime = (Math.abs(state.accumulatedVideoWatchTime - heartbeatInterval) <= 5 ? heartbeatInterval : state.accumulatedVideoWatchTime);
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("elapsedTime", state.accumulatedVideoWatchTime);
        if (state.accumulatedVideoWatchTime != null && state.accumulatedVideoWatchTime > 0L) {
            if (state.isAd) {
                sendVideoAdEvent(AD_HEARTBEAT,eventData);
            } else {
                sendVideoEvent(CONTENT_HEARTBEAT, eventData);
                sendQoeAggregate();
            }
        }
        state.chrono.start();
        state.accumulatedVideoWatchTime = 0L;
    }


    /**
     * Send rendition change event.
     */
    public void sendRenditionChange() {
        if (state.isAd) {
            sendVideoAdEvent(AD_RENDITION_CHANGE);
        } else {
            sendVideoEvent(CONTENT_RENDITION_CHANGE);
        }
    }

    /**
     * Send QOE aggregate event with calculated KPI attributes.
     * This method sends quality of experience metrics aggregated during each harvest cycle.
     * Note: QoE metrics are currently limited to content-related events only, not ad events.
     * This design choice focuses QoE measurement on the primary content viewing experience.
     */
    public void sendQoeAggregate() {
        if (!state.isAd) { // Only send for content, not ads
            Map<String, Object> kpiAttributes = calculateQOEKpiAttributes();
            sendVideoEvent(QOE_AGGREGATE, kpiAttributes);
        }
    }

    /**
     * Calculate QoE KPI attributes based on tracked metrics during playback.
     * @return Map containing the KPI attributes
     */
    private Map<String, Object> calculateQOEKpiAttributes() {
        Map<String, Object> kpiAttributes = new HashMap<>();

        // Add captured timestamps to QOE_AGGREGATE events
        if (contentRequestTimestamp != null) {
            kpiAttributes.put("timeSinceRequested", contentRequestTimestamp);
        }

        if (contentStartTimestamp != null) {
            kpiAttributes.put("timeSinceStarted", contentStartTimestamp);
        }


        // peakBitrate - Maximum contentBitrate observed during content playback
        if (qoePeakBitrate != null && qoePeakBitrate > 0) {
            kpiAttributes.put("peakBitrate", qoePeakBitrate);
        }

        // hadStartupFailure - Boolean indicating if CONTENT_ERROR occurred before CONTENT_START
        // Note: This metric is calculated by the built-in timing system based on timing entries
        // If timeSinceStarted is not available in the final event, it indicates startup failure
        kpiAttributes.put("hadStartupFailure", false); // Will be overridden if no timeSinceStarted in final event

        // hadPlaybackFailure - Boolean indicating if CONTENT_ERROR occurred at any time during content playback
        kpiAttributes.put("hadPlaybackFailure", qoeHadPlaybackFailure);

        // totalRebufferingTime - Total milliseconds spent rebuffering during content playback
        kpiAttributes.put("totalRebufferingTime", qoeTotalRebufferingTime);

        // rebufferingRatio - Rebuffering time as a percentage of total playtime
        if (totalPlaytime > 0) {
            double rebufferingRatio = ((double) qoeTotalRebufferingTime / totalPlaytime) * 100;
            kpiAttributes.put("rebufferingRatio", rebufferingRatio);
        } else {
            kpiAttributes.put("rebufferingRatio", 0.0);
        }

        // totalPlaytime - Total milliseconds user spent watching content
        kpiAttributes.put("totalPlaytime", totalPlaytime);

        // averageBitrate - Time-weighted average bitrate across all content playback
        Long timeWeightedAverage = calculateTimeWeightedAverageBitrate();
        if (timeWeightedAverage != null) {
            kpiAttributes.put("averageBitrate", timeWeightedAverage);
        } else if (qoeBitrateCount > 0) {
            // Fallback to simple average if time-weighted calculation is not available
            long averageBitrate = qoeBitrateSum / qoeBitrateCount;
            kpiAttributes.put("averageBitrate", averageBitrate);
        }

        return kpiAttributes;
    }

    /**
     * Update time-weighted bitrate calculation when bitrate changes.
     * This method accumulates the weighted time for each bitrate segment.
     *
     * @param newBitrate The new bitrate that just became active
     */
    private void updateTimeWeightedBitrate(Long newBitrate) {
        long currentTime = System.currentTimeMillis();

        // If we have a previous bitrate and timing, accumulate its weighted time
        if (qoeCurrentBitrate != null && qoeLastRenditionChangeTime != null) {
            long segmentDuration = currentTime - qoeLastRenditionChangeTime;
            if (segmentDuration > 0) {
                // Accumulate weighted bitrate-time (bitrate * duration)
                qoeTotalBitrateWeightedTime += qoeCurrentBitrate * segmentDuration;
                qoeTotalActiveTime += segmentDuration;
            }
        }

        // Update current tracking values
        qoeCurrentBitrate = newBitrate;
        qoeLastRenditionChangeTime = currentTime;
    }

    /**
     * Finalize time-weighted bitrate calculation by including the current segment.
     * Called during QoE calculation to include the time since the last rendition change.
     *
     * @return Time-weighted average bitrate, or null if no data available
     */
    private Long calculateTimeWeightedAverageBitrate() {
        // Include current segment in calculation
        if (qoeCurrentBitrate != null && qoeLastRenditionChangeTime != null) {
            long currentTime = System.currentTimeMillis();
            long currentSegmentDuration = currentTime - qoeLastRenditionChangeTime;

            if (currentSegmentDuration > 0 && qoeCurrentBitrate > 0) {
                long totalWeightedTime = qoeTotalBitrateWeightedTime + (qoeCurrentBitrate * currentSegmentDuration);
                long totalTime = qoeTotalActiveTime + currentSegmentDuration;

                if (totalTime > 0) {
                    return totalWeightedTime / totalTime;
                }
            }
        }

        // Fallback to accumulated data only
        if (qoeTotalActiveTime > 0) {
            return qoeTotalBitrateWeightedTime / qoeTotalActiveTime;
        }

        return null; // No time-weighted data available
    }

    /**
     * Reset QoE metrics when starting a new view session.
     * This ensures that QoE KPIs are isolated per view ID.
     */
    private void resetQoeMetrics() {
        qoePeakBitrate = null;
        qoeHadPlaybackFailure = false;
        qoeTotalRebufferingTime = 0L;
        qoeBitrateSum = 0L;
        qoeBitrateCount = 0L;
        qoeLastTrackedBitrate = null; // Reset cache
        qoeStartupTime = null; // Reset cached startup time for new view session

        // Reset startup time calculation fields
        contentRequestTimestamp = null;
        contentStartTimestamp = null;

        // Reset time-weighted bitrate fields
        qoeCurrentBitrate = null;
        qoeLastRenditionChangeTime = null;
        qoeTotalBitrateWeightedTime = 0L;
        qoeTotalActiveTime = 0L;
    }

    /**
     * Send request event.
     *
     * @param error Exception.
     */
    public void sendError(Exception error) {
        ErrorExceptionHandler exceptionHandler = new ErrorExceptionHandler(error);
        sendError(exceptionHandler.getErrorCode(), exceptionHandler.getErrorMessage());
    }

    /**
     * Send request event.
     *
     * @param errorMessage Error message.
     */
    public void sendError(int errorCode, String errorMessage) {
        if (errorMessage == null) {
            errorMessage = "<Unknown error>";
        }
        numberOfErrors++;
        Map<String, Object> errAttr = new HashMap<>();
        errAttr.put("errorMessage", errorMessage);
        errAttr.put("errorCode", errorCode);
//        generatePlayElapsedTime();
        String actionName = CONTENT_ERROR;
        if (state.isAd) {
            actionName = AD_ERROR;
        } else {
            // QoE: Track playback errors for content (errors after CONTENT_START)
            Map<String, Object> currentAttributes = getAttributes(actionName, null);
            Object timeSinceStarted = currentAttributes.get("timeSinceStarted");
            if (timeSinceStarted != null) {
                // Error occurred after CONTENT_START, so it's a playback failure
                qoeHadPlaybackFailure = true;
            }
            // Note: Startup failures cannot be reported via QoE aggregate since
            // no heartbeat events are sent after CONTENT_ERROR before CONTENT_START
        }
        sendVideoErrorEvent(actionName, errAttr);
    }

    /**
     * Send Ad Break Start event.
     */
    public void sendAdBreakStart() {
        if (state.isAd && state.goAdBreakStart()) {
            adBreakIdIndex++;
            totalAdPlaytime = 0L;
            sendVideoAdEvent(AD_BREAK_START);
        }
    }

    /**
     * Send Ad Break End event.
     */
    public void sendAdBreakEnd() {
        if (state.isAd && state.goAdBreakEnd()) {
            sendVideoAdEvent(AD_BREAK_END);
        }
    }

    /**
     * Send Ad Quartile event.
     */
    public void sendAdQuartile() {
        if (state.isAd) {
            sendVideoAdEvent(AD_QUARTILE);
        }
    }

    /**
     * Send Ad Click event.
     */
    public void sendAdClick() {
        if (state.isAd) {
            sendVideoAdEvent(AD_CLICK);
        }
    }

    /**
     * Tracker is for Ads or not. To be overwritten by a subclass that inplements an Ads tracker.
     *
     * @return True if tracker is for Ads. Default False.
     */
    public Boolean getIsAd() {
        return state.isAd;
    }

    /**
     * Get the tracker version.
     *
     * @return Attribute.
     */
    public String getTrackerVersion() {
        return null;
    }

    /**
     * Get the tracker src.
     *
     * @return Attribute.
     */
    public String getTrackerSrc() {
        return null;
    }

    /**
     * Get the tracker name.
     *
     * @return Attribute.
     */
    public String getTrackerName() {
        return null;
    }

    /**
     * Get the player version.
     *
     * @return Attribute.
     */
    public String getPlayerVersion() {
        return null;
    }

    /**
     * Get the player name.
     *
     * @return Attribute.
     */
    public String getPlayerName() {
        return null;
    }

    /**
     * Get the video title.
     *
     * @return Attribute.
     */
    public String getTitle() {
        return null;
    }

    /**
     * Get the video bitrate.
     *
     * @return Attribute.
     */
    public Long getBitrate() {
        return null;
    }

    public Long getActualBitrate(){
        return null;
    }

    /**
     * Get the video rendition bitrate.
     *
     * @return Attribute.
     */
    public Long getRenditionBitrate() {
        return null;
    }

    /**
     * Get the video rendition width.
     *
     * @return Attribute.
     */
    public Long getRenditionWidth() {
        return null;
    }

    /**
     * Get the video rendition height.
     *
     * @return Attribute.
     */
    public Long getRenditionHeight() {
        return null;
    }

    /**
     * Get the video duration.
     *
     * @return Attribute.
     */
    public Long getDuration() {
        return null;
    }

    /**
     * Get current playback position in milliseconds.
     *
     * @return Attribute.
     */
    public Long getPlayhead() {
        return null;
    }

    /**
     * Get video language.
     *
     * @return Attribute.
     */
    public String getLanguage() {
        return null;
    }

    /**
     * Get video source. Usually a URL.
     *
     * @return Attribute.
     */
    public String getSrc() {
        return null;
    }

    /**
     * Get whether video is muted or not.
     *
     * @return Attribute.
     */
    public Boolean getIsMuted() {
        return null;
    }

    /**
     * Get video frames per second.
     *
     * @return Attribute.
     */
    public Double getFps() {
        return null;
    }

    /**
     * Get whether video playback is live or not.
     *
     * @return Attribute.
     */
    public Boolean getIsLive() {
        return null;
    }

    /**
     * Get Ad creative ID.
     *
     * @return Attribute.
     */
    public String getAdCreativeId() {
        return null;
    }

    /**
     * Get Ad position, pre, mid or post.
     *
     * @return Attribute.
     */
    public String getAdPosition() {
        return null;
    }

    /**
     * Get Ad quartile.
     *
     * @return Attribute.
     */
    public Long getAdQuartile() {
        return null;
    }

    /**
     * Get ad partner name.
     *
     * @return Attribute.
     */
    public String getAdPartner() {
        return null;
    }

    /**
     * Get ad break id.
     *
     * @return Attribute.
     */
    public String getAdBreakId() {
        return getViewSession() + "-" + adBreakIdIndex;
    }

    /**
     * Get total ad playtime of the last ad break.
     *
     * @return Attribute.
     */
    public Long getTotalAdPlaytime() {
        return totalAdPlaytime;
    }

    /**
     * Get view session.
     *
     * @return Attribute.
     */
    public String getViewSession() {
        // If we are an Ad tracker, we use main tracker's viewSession
        if (this.state.isAd && this.linkedTracker instanceof NRVideoTracker) {
            return ((NRVideoTracker)this.linkedTracker).getViewSession();
        }
        else {
            return viewSessionId;
        }
    }

    /**
     * Get view ID.
     *
     * @return Attribute.
     */
    public String getViewId() {
        // If we are an Ad tracker, we use main tracker's viewId
        if (this.state.isAd && this.linkedTracker instanceof NRVideoTracker) {
            return ((NRVideoTracker)this.linkedTracker).getViewId();
        }
        else {
            return getViewSession() + "-" + viewIdIndex;
        }
    }

    /**
     * Get video ID.
     *
     * @return Attribute.
     */
    public String getVideoId() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            return "";
        }
        String src = "";
        if (getSrc() != null) {
            src = getSrc();
        }
        md.update(src.getBytes());
        byte[] result = md.digest();
        return String.format("%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X",
                result[0], result[1], result[2], result[3],
                result[4], result[5], result[6], result[7],
                result[8], result[9], result[10], result[11],
                result[12], result[13], result[14], result[15]);
    }

    /**
     * Get bufferType.
     *
     * @return Attribute.
     */
    public String getBufferType() {
        return bufferType;
    }

    /**
     * Notify that an Ad just ended.
     */
    public void adHappened() {
        // Create an NRTimeSince entry without action (won't by updated by any action) and force a "now" to set the current timestamp reference
        if (lastAdTimeSince == null) {
            NRTimeSince ts = new NRTimeSince("", "timeSinceLastAd", "^CONTENT_[A-Z_]+$");
            addTimeSinceEntry(ts);
            lastAdTimeSince = ts;
        }
        lastAdTimeSince.now();
    }

    /**
     * Generate table of timeSince attributes.
     *
     * Generate all video related timeSince attributes.
     */
    @Override
    public void generateTimeSinceTable() {
        super.generateTimeSinceTable();

        addTimeSinceEntry(CONTENT_HEARTBEAT, "timeSinceLastHeartbeat", "^CONTENT_[A-Z_]+$");
        addTimeSinceEntry(AD_HEARTBEAT, "timeSinceLastAdHeartbeat", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_REQUEST, "timeSinceRequested", "^CONTENT_[A-Z_]+$");
        addTimeSinceEntry(AD_REQUEST, "timeSinceAdRequested", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_START, "timeSinceStarted", "^CONTENT_[A-Z_]+$");
        addTimeSinceEntry(AD_START, "timeSinceAdStarted", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_PAUSE, "timeSincePaused", "^CONTENT_RESUME$");
        addTimeSinceEntry(AD_PAUSE, "timeSinceAdPaused", "^AD_RESUME$");

        addTimeSinceEntry(CONTENT_RESUME, "timeSinceResumed", "^CONTENT_BUFFER_(START|END)$");
        addTimeSinceEntry(AD_RESUME, "timeSinceAdResumed", "^AD_BUFFER_(START|END)$");

        addTimeSinceEntry(CONTENT_SEEK_START, "timeSinceSeekBegin", "^CONTENT_SEEK_END$");
        addTimeSinceEntry(AD_SEEK_START, "timeSinceAdSeekBegin", "^AD_SEEK_END$");

        addTimeSinceEntry(CONTENT_SEEK_END, "timeSinceSeekEnd", "^CONTENT_BUFFER_(START|END)$");
        addTimeSinceEntry(AD_SEEK_END, "timeSinceAdSeekEnd", "^AD_BUFFER_(START|END)$");

        addTimeSinceEntry(CONTENT_BUFFER_START, "timeSinceBufferBegin", "^CONTENT_BUFFER_END$");
        addTimeSinceEntry(AD_BUFFER_START, "timeSinceAdBufferBegin", "^AD_BUFFER_END$");

        addTimeSinceEntry(CONTENT_ERROR, "timeSinceLastError", "^CONTENT_[A-Z_]+$");
        addTimeSinceEntry(AD_ERROR, "timeSinceLastAdError", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_RENDITION_CHANGE, "timeSinceLastRenditionChange", "^CONTENT_RENDITION_CHANGE$");
        addTimeSinceEntry(AD_RENDITION_CHANGE, "timeSinceLastAdRenditionChange", "^AD_RENDITION_CHANGE$");

        addTimeSinceEntry(QOE_AGGREGATE, "timeSinceLastQoeAggregate", "^QOE_AGGREGATE$");

        addTimeSinceEntry(AD_BREAK_START, "timeSinceAdBreakBegin", "^AD_BREAK_END$");

        addTimeSinceEntry(AD_QUARTILE, "timeSinceLastAdQuartile", "^AD_QUARTILE$");
    }

    private String calculateBufferType() {
        Long playhead = getPlayhead();

        if (!this.state.isAd) {
            if (this.linkedTracker instanceof NRVideoTracker) {
                if (((NRVideoTracker)this.linkedTracker).state.isAdBreak) {
                    return "ad";
                }
            }
        }

        if (playhead == null) {
            playhead = 0L;
        }

        if (state.isSeeking) {
            return "seek";
        }

        if (state.isPaused) {
            return "pause";
        }

        //NOTE: the player starts counting contentPlayhead after buffering ends, and by the time we calculate BUFFER_END, playhead can be a bit higher than zero (few milliseconds).
        if (playhead < 10) {
            return "initial";
        }

        // If none of the above is true, it is a connection buffering
        return "connection";
    }

    /**
     * Returns the current heartbeat interval in milliseconds.
     */
    private long getHeartbeatIntervalMillis() {
        return state.isAd ? AD_HEARTBEAT_INTERVAL_SEC * 1000L : CONTENT_HEARTBEAT_INTERVAL_SEC * 1000L;
    }

    @Override
    public void sendEvent(String action, Map<String, Object> attributes) {
        updatePlaytime();
        super.sendEvent(action, attributes);
    }


    public void sendVideoAdEvent(String action) {
        updatePlaytime();
        super.sendVideoAdEvent(action);
    }

    public void sendVideoAdEvent(String action, Map<String, Object> attributes) {
        updatePlaytime();
        super.sendVideoAdEvent(action, attributes);
    }
    public void sendVideoEvent(String action) {
        updatePlaytime();
        super.sendVideoEvent(action);
    }
    public void sendVideoEvent(String action, Map<String, Object> attributes) {
        updatePlaytime();
        super.sendVideoEvent(action, attributes);
    }


    /**
     * Optimized bitrate tracking from processed attributes.
     * Efficiently handles bitrate extraction, duplicate detection, and metric updates.
     *
     * @param action The action being processed
     * @param processedAttributes Fully processed attributes including contentBitrate
     */
    private void trackBitrateFromProcessedAttributes(String action, Map<String, Object> processedAttributes) {
        // Fast filter: only track for events that can have bitrate information
        if (!isContentBitrateEvent(action)) {
            return;
        }

        Long currentBitrate = extractBitrateValue(processedAttributes.get("contentBitrate"));
        if (currentBitrate == null || currentBitrate <= 0) {
            return;
        }

        // Skip if this is the same bitrate we just tracked (avoid duplicate processing)
        if (qoeLastTrackedBitrate != null && qoeLastTrackedBitrate.equals(currentBitrate)) {
            return;
        }

        // Update cached value to prevent duplicate processing
        qoeLastTrackedBitrate = currentBitrate;

        // Update QoE metrics efficiently
        updateQoeBitrateMetrics(currentBitrate, action);
    }

    /**
     * Fast check if this action can contain bitrate information.
     */
    private static boolean isContentBitrateEvent(String action) {
        return CONTENT_HEARTBEAT.equals(action) || CONTENT_START.equals(action) ||
               CONTENT_RENDITION_CHANGE.equals(action) || CONTENT_RESUME.equals(action);
    }

    /**
     * Efficiently extract Long bitrate value from various numeric types.
     * @param bitrateObj Object that may contain bitrate value
     * @return Long bitrate value or null if invalid
     */
    private static Long extractBitrateValue(Object bitrateObj) {
        if (bitrateObj instanceof Long) {
            return (Long) bitrateObj;
        } else if (bitrateObj instanceof Integer) {
            return ((Integer) bitrateObj).longValue();
        } else if (bitrateObj instanceof Double) {
            return ((Double) bitrateObj).longValue();
        } else if (bitrateObj instanceof Float) {
            return ((Float) bitrateObj).longValue();
        }
        return null;
    }

    /**
     * Update all QoE bitrate metrics in one place for efficiency.
     * @param bitrate The validated bitrate value
     * @param action Action name for logging context
     */
    private void updateQoeBitrateMetrics(Long bitrate, String action) {
        // Update time-weighted average calculation
        updateTimeWeightedBitrate(bitrate);

        // Update peak bitrate
        if (qoePeakBitrate == null || bitrate > qoePeakBitrate) {
            qoePeakBitrate = bitrate;
        }

        // Update simple average (with overflow protection)
        if (qoeBitrateCount < Long.MAX_VALUE - 1) {
            qoeBitrateSum = Math.addExact(qoeBitrateSum, bitrate);
            qoeBitrateCount++;
        }

        // QoE bitrate tracking completed

    }


    public void sendVideoErrorEvent(String action, Map<String, Object> attributes) {
        updatePlaytime();
        super.sendVideoErrorEvent(action, attributes);
    }


}
