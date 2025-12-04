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
    private Long qoeStartupTimeStart;
    private Long qoeStartupTime;
    private Long qoePeakBitrate;
    private Boolean qoeHadStartupFailure;
    private Boolean qoeHadPlaybackFailure;
    private Long qoeTotalRebufferingTime;
    private Long qoeRebufferingStartTime;
    private Long qoeTotalContentPlaytime;
    private Long qoeBitrateSum;
    private Long qoeBitrateCount;

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
        qoeStartupTimeStart = null;
        qoeStartupTime = null;
        qoePeakBitrate = 0L;
        qoeHadStartupFailure = false;
        qoeHadPlaybackFailure = false;
        qoeTotalRebufferingTime = 0L;
        qoeRebufferingStartTime = null;
        qoeTotalContentPlaytime = 0L;
        qoeBitrateSum = 0L;
        qoeBitrateCount = 0L;
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
                // QoE: Start tracking startup time for content
                qoeStartupTimeStart = System.currentTimeMillis();
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
            state.chrono.start();
            if (state.isAd) {
                numberOfAds++;
                ((NRVideoTracker) linkedTracker).sendPause ();
                if (linkedTracker instanceof NRVideoTracker) {
                    ((NRVideoTracker) linkedTracker).setNumberOfAds(numberOfAds);
                }
                sendVideoAdEvent(AD_START);
            } else {
                if (linkedTracker instanceof NRVideoTracker) {
                    totalAdPlaytime = ((NRVideoTracker)linkedTracker).getTotalAdPlaytime();
                }
                numberOfVideos++;

                // QoE: Calculate startup time for content
                if (qoeStartupTimeStart != null) {
                    qoeStartupTime = System.currentTimeMillis() - qoeStartupTimeStart;
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
                state.accumulatedVideoWatchTime +=state. chrono.getDeltaTime();
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
                state.chrono.start();
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
                state.accumulatedVideoWatchTime += state.chrono.getDeltaTime();
            }
            bufferType = calculateBufferType();
            if (state.isAd) {
                sendVideoAdEvent(AD_BUFFER_START);
            } else {
                // QoE: Start tracking rebuffering time (excludes initial buffering)
                if (!calculateBufferType().equals("initial")) {
                    qoeRebufferingStartTime = System.currentTimeMillis();
                }
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
                state.chrono.start();
            }
            if (bufferType == null) {
                bufferType = calculateBufferType();
            }
            if (state.isAd) {
                sendVideoAdEvent(AD_BUFFER_END);
            } else {
                // QoE: Calculate rebuffering time (excludes initial buffering)
                if (qoeRebufferingStartTime != null) {
                    qoeTotalRebufferingTime += System.currentTimeMillis() - qoeRebufferingStartTime;
                    qoeRebufferingStartTime = null;
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
            // QoE: Track bitrate changes for peak and average calculations
            Long currentBitrate = getActualBitrate();
            if (currentBitrate != null && currentBitrate > 0) {
                if (currentBitrate > qoePeakBitrate) {
                    qoePeakBitrate = currentBitrate;
                }
                qoeBitrateSum += currentBitrate;
                qoeBitrateCount++;
            }
            sendVideoEvent(CONTENT_RENDITION_CHANGE);
        }
    }

    /**
     * Send QOE aggregate event with calculated KPI attributes.
     * This method sends quality of experience metrics aggregated during each harvest cycle.
     */
    public void sendQoeAggregate() {
        if (!state.isAd) { // Only send for content, not ads
            // Update content playtime before calculating KPIs
            qoeTotalContentPlaytime = totalPlaytime;

            Map<String, Object> kpiAttributes = calculateQoeKpiAttributes();
            sendVideoEvent(VIEW_QOE_AGGREGATE, kpiAttributes);
        }
    }

    /**
     * Calculate QoE KPI attributes based on tracked metrics during playback.
     * @return Map containing the KPI attributes
     */
    private Map<String, Object> calculateQoeKpiAttributes() {
        Map<String, Object> kpiAttributes = new HashMap<>();

        // kpi.startupTime - Time from CONTENT_REQUEST to CONTENT_START in milliseconds
        if (qoeStartupTime != null) {
            kpiAttributes.put("kpi.startupTime", qoeStartupTime);
        }

        // kpi.peakBitrate - Maximum contentBitrate observed during content playback
        if (qoePeakBitrate > 0) {
            kpiAttributes.put("kpi.peakBitrate", qoePeakBitrate);
        }

        // kpi.hadStartupFailure - Boolean indicating if CONTENT_ERROR occurred before CONTENT_START
        kpiAttributes.put("kpi.hadStartupFailure", qoeHadStartupFailure);

        // kpi.hadPlaybackFailure - Boolean indicating if CONTENT_ERROR occurred at any time during content playback
        kpiAttributes.put("kpi.hadPlaybackFailure", qoeHadPlaybackFailure);

        // kpi.totalRebufferingTime - Total milliseconds spent rebuffering during content playback
        kpiAttributes.put("kpi.totalRebufferingTime", qoeTotalRebufferingTime);

        // kpi.rebufferingRatio - Rebuffering time as a percentage of total playtime
        if (qoeTotalContentPlaytime > 0) {
            double rebufferingRatio = ((double) qoeTotalRebufferingTime / qoeTotalContentPlaytime) * 100;
            kpiAttributes.put("kpi.rebufferingRatio", rebufferingRatio);
        } else {
            kpiAttributes.put("kpi.rebufferingRatio", 0.0);
        }

        // kpi.totalPlaytime - Total milliseconds user spent watching content
        kpiAttributes.put("kpi.totalPlaytime", qoeTotalContentPlaytime);

        // kpi.averageBitrate - Average bitrate across all content playback weighted by playtime
        if (qoeBitrateCount > 0) {
            long averageBitrate = qoeBitrateSum / qoeBitrateCount;
            kpiAttributes.put("kpi.averageBitrate", averageBitrate);
        }

        return kpiAttributes;
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
            // QoE: Track error types for content
            qoeHadPlaybackFailure = true;

            // Check if error occurred before content start (startup failure)
            if (qoeStartupTime == null && qoeStartupTimeStart != null) {
                qoeHadStartupFailure = true;
            }
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

        addTimeSinceEntry(VIEW_QOE_AGGREGATE, "timeSinceLastQoeAggregate", "^VIEW_QOE_AGGREGATE$");

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
    public void sendVideoErrorEvent(String action, Map<String, Object> attributes) {
        updatePlaytime();
        super.sendVideoErrorEvent(action, attributes);
    }


}
