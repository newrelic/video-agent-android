package com.newrelic.videoagent.core.tracker;

import android.os.Handler;

import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.model.NRTimeSince;
import com.newrelic.videoagent.core.model.NRTrackerState;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.core.harvest.QoeProvider;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.newrelic.videoagent.core.NRDef.*;
import com.newrelic.videoagent.core.exception.ErrorExceptionHandler;

/**
 * `NRVideoTracker` defines the basic behaviour of a video tracker.
 * Implements QoeProvider for harvest-time QOE_AGGREGATE generation.
 */
public class NRVideoTracker extends NRTracker implements QoeProvider {


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
    private Boolean qoeHadStartupFailure;
    private Long qoeTotalRebufferingTime;
    private Long qoeBitrateSum;
    private Long qoeBitrateCount;
    private Long qoeLastTrackedBitrate;
    private Long qoeStartupTime; // Cached startup time, calculated once per view session
    private Long startupPeriodAdTime; // Ad time that occurred during startup period
    private Long startupPeriodPauseTime; // Pause time that occurred during startup period
    private boolean hasContentStarted; // Tracks whether content has successfully started (for buffer classification)
    private boolean initialBufferingHappened; // Tracks if initial buffering completed

    // Time-weighted bitrate calculation fields
    private Long qoeCurrentBitrate;
    private Long qoeLastRenditionChangeTime;
    private Long qoeTotalBitrateWeightedTime;
    private Long qoeTotalActiveTime;
    private boolean qoeBitrateTimerPaused;

    // QOE_AGGREGATE provider fields
    private boolean qoeProviderRegistered = false;
    private Map<String, Object> pendingQoeForNextHarvest = null; // For CONTENT_END

    /**
     * Create a new NRVideoTracker.
     */
    public NRVideoTracker(NRVideoConfiguration configuration) {
        super(configuration);
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

        // Initialize heartbeat components
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

        initializeTracker();
    }

    /**
     * Create a new NRVideoTracker (deprecated - use constructor with configuration).
     * @deprecated Use NRVideoTracker(NRVideoConfiguration) constructor instead
     */
    @Deprecated
    public NRVideoTracker() {
        super();
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

        // Initialize heartbeat components
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

        initializeTracker();
    }

    private void initializeTracker() {

        // Initialize QoE tracking fields
        qoePeakBitrate = 0L;
        qoeHadPlaybackFailure = false;
        qoeHadStartupFailure = false;
        qoeTotalRebufferingTime = 0L;
        qoeBitrateSum = 0L;
        qoeBitrateCount = 0L;
        qoeLastTrackedBitrate = null;
        qoeStartupTime = null; // Will be calculated during first QOE_AGGREGATE event

        // Initialize startup time calculation fields
        startupPeriodAdTime = 0L;
        startupPeriodPauseTime = 0L;
        hasContentStarted = false;
        initialBufferingHappened = false;

        // Initialize time-weighted bitrate tracking
        qoeCurrentBitrate = null;
        qoeLastRenditionChangeTime = null;
        qoeTotalBitrateWeightedTime = 0L;
        qoeTotalActiveTime = 0L;
        qoeBitrateTimerPaused = false;
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
            // Only add bitrate attributes after ad has started (first frame shown)
            if (state.isStarted) {
                attr.put("adBitrate", getBitrate());
                attr.put("adRenditionBitrate", getRenditionBitrate());
            }
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
            // Only add bitrate attributes after content has started (first frame shown)
            if (state.isStarted) {
                attr.put("contentBitrate", getActualBitrate());
                attr.put("contentRenditionBitrate", getRenditionBitrate());
            }
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
                    // Store ad time for startup calculation (covers pre-roll scenario)
                    startupPeriodAdTime = totalAdPlaytime;
                }
                numberOfVideos++;

                // QoE: Mark that content has successfully started (for buffer type classification)
                hasContentStarted = true;

                sendVideoEvent(CONTENT_START);

                // Calculate and cache startup time at CONTENT_START (for QOE reporting)
                calculateAndCacheStartupTime();

                // Resume bitrate timer when content starts playing
                resumeBitrateTimer();

                // Register QOE provider with HarvestManager (harvest-time generation)
                if (!qoeProviderRegistered && configuration != null && configuration.isQoeAggregateEnabled()) {
                    if (NRVideo.getInstance() != null && NRVideo.getInstance().getHarvestManager() != null) {
                        NRVideo.getInstance().getHarvestManager().registerQoeProvider(this);
                        qoeProviderRegistered = true;
                        NRLog.d("QOE provider registered at CONTENT_START");
                    }
                }
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
                if (!state.isAd) {
                    state.accumulatedVideoWatchTime += state.chrono.getDeltaTime();
                }
            }
            if (state.isAd) {
                sendVideoAdEvent(AD_PAUSE);
            } else {
                sendVideoEvent(CONTENT_PAUSE);
                // Pause bitrate timer during pause to exclude paused time from average
                pauseBitrateTimer();
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

            // QoE: Calculate pause time during startup period for content events
            if (!state.isAd && !hasContentStarted) {
                // Content hasn't started yet, so this pause time should be excluded from startup calculation
                Map<String, Object> resumeAttributes = getAttributes(CONTENT_RESUME, null);
                Object timeSincePaused = resumeAttributes.get("timeSincePaused");

                if (timeSincePaused instanceof Long && ((Long) timeSincePaused) > 0) {
                    try {
                        startupPeriodPauseTime = safeAdd(startupPeriodPauseTime, (Long) timeSincePaused);
                    } catch (ArithmeticException e) {
                        NRLog.w("QoE startup pause time accumulation overflow");
                        startupPeriodPauseTime = (Long) timeSincePaused;
                    }
                }
            }

            if (state.isAd) {
                sendVideoAdEvent(AD_RESUME);
            } else {
                sendVideoEvent(CONTENT_RESUME);
                // Resume bitrate timer when playback resumes (only if not buffering or seeking)
                if (!state.isBuffering && !state.isSeeking) {
                    resumeBitrateTimer();
                }
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
                // Build QOE eagerly for CONTENT_END and enqueue for next harvest
                if (configuration != null && configuration.isQoeAggregateEnabled()) {
                    // Build QOE with standard attributes (reuse the new helper method)
                    Map<String, Object> qoeEvent = buildQoeEventWithStandardAttributes();

                    // Mark as final QOE so HarvestManager always sends it (even on empty batches)
                    qoeEvent.put("isFinalQoe", true);

                    // Enqueue for next harvest to avoid race conditions
                    pendingQoeForNextHarvest = qoeEvent;
                    NRLog.d("QOE_AGGREGATE built eagerly at CONTENT_END, enqueued for next harvest");
                }

                sendVideoEvent(CONTENT_END);

                // NOTE: Provider unregistration is delayed until after final QOE is sent
                // This happens automatically in HarvestManager when it detects "isFinalQoe" flag
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
                // Pause bitrate timer during seeking to exclude seek time from average
                pauseBitrateTimer();
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
                // Resume bitrate timer when seeking ends (only if not buffering or paused)
                if (!state.isBuffering && !state.isPaused) {
                    resumeBitrateTimer();
                }
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
                sendVideoEvent(CONTENT_BUFFER_START);
                // Pause bitrate timer during buffering to exclude buffering time from average
                pauseBitrateTimer();
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
                sendVideoEvent(CONTENT_BUFFER_END);
                // Resume bitrate timer when buffering ends (only if not seeking or paused)
                if (!state.isSeeking && !state.isPaused) {
                    resumeBitrateTimer();
                }
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
        if (state.accumulatedVideoWatchTime != null) {
            if (state.isAd) {
                sendVideoAdEvent(AD_HEARTBEAT,eventData);
            } else {
                sendVideoEvent(CONTENT_HEARTBEAT, eventData);
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
    /**
     * Implementation of QoeProvider interface.
     * Called by HarvestManager during harvest to generate QOE_AGGREGATE events.
     *
     * @param batch The current harvest batch containing VideoAction events
     * @param harvestCycleNumber The current harvest cycle number (1-based)
     * @return QOE_AGGREGATE event map if it should be generated, null otherwise
     */
    @Override
    public Map<String, Object> generateQoeIfNeeded(List<Map<String, Object>> batch, int harvestCycleNumber) {
        // Check if QOE is enabled
        if (!state.isAd && configuration != null && configuration.isQoeAggregateEnabled()) {
            // Check if this harvest cycle should send based on interval multiplier
            int intervalMultiplier = configuration.getQoeAggregateIntervalMultiplier();

            // Formula matches iOS: (harvestCycleNumber - 1) % multiplier == 0
            // Examples:
            //   multiplier=1: cycles 1,2,3,4... (every harvest)
            //   multiplier=2: cycles 1,3,5,7... (every other)
            //   multiplier=3: cycles 1,4,7,10... (every third)
            boolean shouldSend = (harvestCycleNumber - 1) % intervalMultiplier == 0;

            if (shouldSend) {
                // Build QOE event with standard attributes (reuse existing attribute generation)
                Map<String, Object> qoeEvent = buildQoeEventWithStandardAttributes();

                NRLog.d("QOE_AGGREGATE generated for harvest cycle " + harvestCycleNumber);
                return qoeEvent;
            }
        }

        // Check if there's a pending QOE from CONTENT_END
        if (pendingQoeForNextHarvest != null) {
            Map<String, Object> qoe = pendingQoeForNextHarvest;
            pendingQoeForNextHarvest = null; // Clear after use
            NRLog.d("Pending QOE_AGGREGATE from CONTENT_END injected into harvest");
            return qoe;
        }

        return null; // Don't generate QOE for this cycle
    }

    /**
     * Build QOE event with all standard video tracking attributes.
     * Reuses the existing attribute generation pipeline to ensure consistency.
     *
     * @return Complete QOE event map with standard attributes
     */
    private Map<String, Object> buildQoeEventWithStandardAttributes() {
        // Start with QOE KPI attributes
        Map<String, Object> qoeEvent = calculateQOEKpiAttributes();

        // Add standard video tracking attributes (reuse getAttributes pipeline)
        qoeEvent = getAttributes(QOE_AGGREGATE, qoeEvent);

        // Add instrumentation attributes (same as NRTracker.sendEvent())
        qoeEvent.put("agentSession", getAgentSession());
        qoeEvent.put("instrumentation.provider", "newrelic");
        qoeEvent.put("instrumentation.name", getInstrumentationName());
        qoeEvent.put("instrumentation.version", getCoreVersion());

        // Add core attributes
        qoeEvent.put("eventType", "VideoAction");
        qoeEvent.put("actionName", QOE_AGGREGATE);
        qoeEvent.put("timestamp", System.currentTimeMillis());
        qoeEvent.put("sessionId", getAgentSession());
        qoeEvent.put("viewId", getViewId());

        // Remove null and empty values (same as NRTracker.sendEvent())
        Iterator<Object> it = qoeEvent.values().iterator();
        while (it.hasNext()) {
            Object v = it.next();
            if (v == null || "".equals(v)) {
                it.remove();
            }
        }

        return qoeEvent;
    }

    /**
     * Implementation of QoeProvider interface.
     * Called when provider should be unregistered.
     */
    @Override
    public void unregister() {
        qoeProviderRegistered = false;
        NRLog.d("QOE provider unregistered for tracker");
    }

    /**
     * Calculate QoE KPI attributes based on tracked metrics during playback.
     * @return Map containing the KPI attributes
     */
    private Map<String, Object> calculateQOEKpiAttributes() {
        Map<String, Object> kpiAttributes = new HashMap<>();

        // peakBitrate - Maximum contentBitrate observed during content playback
        if (qoePeakBitrate != null && qoePeakBitrate > 0) {
            kpiAttributes.put("peakBitrate", qoePeakBitrate);
        }

        // hadPlaybackFailure - Boolean indicating if CONTENT_ERROR occurred at any time during content playback
        if (qoeHadPlaybackFailure == null) {
            qoeHadPlaybackFailure = false;
        }
        kpiAttributes.put("hadPlaybackFailure", qoeHadPlaybackFailure);

        // totalRebufferingTime - Total milliseconds spent rebuffering during content playback
        if (qoeTotalRebufferingTime == null) {
            qoeTotalRebufferingTime = 0L;
        }
        kpiAttributes.put("totalRebufferingTime", qoeTotalRebufferingTime);

        // Use elapsedTime (accumulatedVideoWatchTime) instead of totalPlaytime for QOE
        Long elapsedTime = state.accumulatedVideoWatchTime;
        if (elapsedTime == null) {
            elapsedTime = 0L;
        }

        // rebufferingRatio - Rebuffering time as a percentage of elapsed watch time
        if (elapsedTime > 0) {
            double rebufferingRatio = ((double) qoeTotalRebufferingTime / elapsedTime) * 100;
            kpiAttributes.put("rebufferingRatio", rebufferingRatio);
        } else {
            kpiAttributes.put("rebufferingRatio", 0.0);
        }

        // totalPlaytime - Use elapsedTime (accumulated video watch time) instead of totalPlaytime
        kpiAttributes.put("totalPlaytime", elapsedTime);

        // averageBitrate - Time-weighted average bitrate across all content playback
        Long timeWeightedAverage = calculateTimeWeightedAverageBitrate();
        if (timeWeightedAverage != null) {
            kpiAttributes.put("averageBitrate", timeWeightedAverage);
        } else if (qoeBitrateCount != null && qoeBitrateCount > 0 && qoeBitrateSum != null) {
            // Fallback to simple average if time-weighted calculation is not available
            long averageBitrate = Math.round((double) qoeBitrateSum / qoeBitrateCount);
            kpiAttributes.put("averageBitrate", averageBitrate);
        }

        // qoeAggregateVersion - Version identifier for QOE calculation algorithm
        kpiAttributes.put("qoeAggregateVersion", "1.0.0");

        // startupTime - Cached value calculated at CONTENT_START
        if (qoeStartupTime != null && qoeStartupTime >= 0) {
            kpiAttributes.put("startupTime", qoeStartupTime);
        } else {
            kpiAttributes.put("startupTime", 0L);
        }

        // hadStartupFailure - Boolean indicating if error occurred before CONTENT_START
        if (qoeHadStartupFailure == null) {
            qoeHadStartupFailure = false;
        }
        kpiAttributes.put("hadStartupFailure", qoeHadStartupFailure);

        return kpiAttributes;
    }

    /**
     * Calculate rebuffering time from CONTENT_BUFFER_END events.
     * Called from NRTracker.processBufferEndEvent() where timing attributes are already available.
     *
     * @param attributes The CONTENT_BUFFER_END attributes map (timing attributes already applied)
     */
    public void calculateRebufferingTime(Map<String, Object> attributes) {
        // Extract timeSinceBufferBegin which is now available after applyAttributes()
        Object timeSinceBufferBegin = attributes.get("timeSinceBufferBegin");
        if (timeSinceBufferBegin instanceof Long &&
                initialBufferingHappened) { // Only count if initial buffering already completed
            try {
                long previousTotal = qoeTotalRebufferingTime;
                qoeTotalRebufferingTime = safeAdd(
                        qoeTotalRebufferingTime,
                        (Long) timeSinceBufferBegin
                );
            } catch (ArithmeticException e) {
                NRLog.w("QoE rebuffering time accumulation overflow");
                qoeTotalRebufferingTime = (Long) timeSinceBufferBegin;
            }
        }
        // Set initialBufferingHappened flag after processing this buffer event
        if (!initialBufferingHappened) {
            initialBufferingHappened = true;
        }
    }

    /**
     * Calculate and cache startup time at CONTENT_START.
     * This method calculates startup time once and caches it for all subsequent QOE reports.
     * Called immediately after CONTENT_START event is sent.
     */
    private void calculateAndCacheStartupTime() {
        if (qoeStartupTime != null) {
            return; // Already calculated
        }

        // Get the CONTENT_START attributes to extract timing values
        Map<String, Object> startAttributes = getAttributes(CONTENT_START, null);
        timeSinceTable.applyAttributes(CONTENT_START, startAttributes);

        Long timeSinceRequested = (Long) startAttributes.get("timeSinceRequested");

        if (timeSinceRequested != null && timeSinceRequested >= 0) {
            Long totalExclusionTime = 0L;

            // Exclude ad time that occurred during startup period
            if (startupPeriodAdTime != null && startupPeriodAdTime > 0) {
                totalExclusionTime += startupPeriodAdTime;
            }

            // Exclude pause time that occurred during startup period
            if (startupPeriodPauseTime != null && startupPeriodPauseTime > 0) {
                totalExclusionTime += startupPeriodPauseTime;
            }

            // Calculate: startupTime = timeSinceRequested - adTime - pauseTime
            // Ensure result is non-negative
            qoeStartupTime = Math.max(timeSinceRequested - totalExclusionTime, 0L);

            NRLog.d("Startup time calculated and cached: " + qoeStartupTime + "ms");
        } else {
            qoeStartupTime = 0L;
        }
    }

    /**
     * Calculate and add startup time to QOE_AGGREGATE attributes.
     * Called from NRTracker.sendEvent() where timing attributes are already available.
     * @deprecated This method is kept for backward compatibility but is no longer used
     * in the harvest-time QOE generation architecture. Use calculateAndCacheStartupTime() instead.
     *
     * @param attributes The QOE_AGGREGATE attributes map to add startup time to
     * @param timeSinceRequested Time since CONTENT_REQUEST event
     * @param timeSinceStarted Time since CONTENT_START event
     * @param timeSinceLastError Time since CONTENT_ERROR event
     */
    @Deprecated
    public void calculateAndAddStartupTime(Map<String, Object> attributes,
                                           Long timeSinceRequested,
                                           Long timeSinceStarted,
                                           Long timeSinceLastError) {
        // startupTime - Calculate once during first QOE_AGGREGATE event and cache for reuse
        if (qoeStartupTime == null && timeSinceRequested != null) {
            Long rawStartupTime = null;

            // Calculate startup time using timeSince values
            if (timeSinceStarted != null) {
                // Normal startup success: rawStartupTime = timeSinceRequested - timeSinceStarted
                rawStartupTime = timeSinceRequested - timeSinceStarted;
            } else if (timeSinceLastError != null) {
                // Startup failure: rawStartupTime = timeSinceRequested - timeSinceLastError
                rawStartupTime = timeSinceRequested - timeSinceLastError;
            }

            if (rawStartupTime != null && rawStartupTime >= 0) {
                // For content trackers only - exclude ad time and pause time from startup calculation
                if (!state.isAd) {
                    Long totalExclusionTime = 0L;

                    // Exclude ad time that occurred during startup period
                    if (startupPeriodAdTime != null && startupPeriodAdTime > 0) {
                        totalExclusionTime += startupPeriodAdTime;
                    }
                    // Exclude pause time that occurred during startup period
                    if (startupPeriodPauseTime != null && startupPeriodPauseTime > 0) {
                        totalExclusionTime += startupPeriodPauseTime;
                    }
                    // Apply enhanced exclusion pattern: max(rawTime - adTime - pauseTime, 0)
                    qoeStartupTime = Math.max(rawStartupTime - totalExclusionTime, 0L);
                } else {
                    // Ad tracker itself - use raw calculation
                    qoeStartupTime = rawStartupTime;
                }
            }
        }
        // Include cached startup time if available (including zero for instant startup)
        if (qoeStartupTime != null && qoeStartupTime >= 0) {
            attributes.put("startupTime", qoeStartupTime);
        } else {
            attributes.put("startupTime", 0L);
        }

        // Use the tracked startup failure flag (set during error handling)
        if (qoeHadStartupFailure == null) {
            qoeHadStartupFailure = false;
        }
        attributes.put("hadStartupFailure", qoeHadStartupFailure);
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
        if (qoeCurrentBitrate != null && qoeLastRenditionChangeTime != null && qoeCurrentBitrate > 0) {
            // Ensure valid timestamps
            if (qoeLastRenditionChangeTime > 0 && currentTime >= qoeLastRenditionChangeTime) {
                long segmentDuration = currentTime - qoeLastRenditionChangeTime;
                if (segmentDuration > 0) {
                    // Prevent overflow in multiplication
                    if (qoeCurrentBitrate <= Long.MAX_VALUE / segmentDuration) {
                        try {
                            qoeTotalBitrateWeightedTime = safeAdd(qoeTotalBitrateWeightedTime, qoeCurrentBitrate * segmentDuration);
                            qoeTotalActiveTime = safeAdd(qoeTotalActiveTime, segmentDuration);
                        } catch (ArithmeticException e) {
                            // Overflow in time-weighted bitrate accumulation - reset to prevent future overflows
                            NRLog.w("QoE bitrate accumulation overflow - resetting accumulated values to start fresh");
                            qoeTotalBitrateWeightedTime = 0L;
                            qoeTotalActiveTime = 0L;
                        }
                    }
                }
            }
        }

        // Update current tracking values (accept null/zero values for reset scenarios)
        qoeCurrentBitrate = newBitrate;
        qoeLastRenditionChangeTime = currentTime;
    }

    /**
     * Pause the bitrate timer during non-play states (pause, buffering, seeking).
     * This ensures that time spent in non-play states is not counted in the
     * time-weighted average bitrate calculation.
     */
    private void pauseBitrateTimer() {
        if (qoeBitrateTimerPaused) {
            return; // Already paused, nothing to do
        }

        // Save the current segment before pausing
        if (qoeCurrentBitrate != null && qoeLastRenditionChangeTime != null && qoeCurrentBitrate > 0) {
            long currentTime = System.currentTimeMillis();

            // Ensure valid timestamps
            if (qoeLastRenditionChangeTime > 0 && currentTime >= qoeLastRenditionChangeTime) {
                long segmentDuration = currentTime - qoeLastRenditionChangeTime;

                if (segmentDuration > 0) {
                    // Prevent overflow in multiplication
                    if (qoeCurrentBitrate <= Long.MAX_VALUE / segmentDuration) {
                        try {
                            qoeTotalBitrateWeightedTime = safeAdd(qoeTotalBitrateWeightedTime, qoeCurrentBitrate * segmentDuration);
                            qoeTotalActiveTime = safeAdd(qoeTotalActiveTime, segmentDuration);
                        } catch (ArithmeticException e) {
                            NRLog.w("QoE bitrate accumulation overflow during pause - resetting");
                            qoeTotalBitrateWeightedTime = 0L;
                            qoeTotalActiveTime = 0L;
                        }
                    }
                }
            }
        }

        qoeBitrateTimerPaused = true;
    }

    /**
     * Resume the bitrate timer after exiting non-play states.
     * Restarts timing from the current moment so paused time is excluded.
     */
    private void resumeBitrateTimer() {
        if (!qoeBitrateTimerPaused) {
            return; // Already running, nothing to do
        }

        // Restart the timer from now (exclude the paused time)
        qoeLastRenditionChangeTime = System.currentTimeMillis();
        qoeBitrateTimerPaused = false;
    }

    /**
     * Finalize time-weighted bitrate calculation by including the current segment.
     * Called during QoE calculation to include the time since the last rendition change.
     *
     * @return Time-weighted average bitrate, or null if no data available
     */
    private Long calculateTimeWeightedAverageBitrate() {
        // Include current segment in calculation (only if timer is not paused)
        if (!qoeBitrateTimerPaused && qoeCurrentBitrate != null && qoeLastRenditionChangeTime != null && qoeCurrentBitrate > 0) {
            long currentTime = System.currentTimeMillis();

            // Safety check: ensure valid timestamp
            if (qoeLastRenditionChangeTime > 0 && currentTime >= qoeLastRenditionChangeTime) {
                long currentSegmentDuration = currentTime - qoeLastRenditionChangeTime;

                // Include current segment if it has meaningful duration
                if (currentSegmentDuration > 0) {
                    // Prevent overflow in multiplication
                    if (qoeCurrentBitrate <= Long.MAX_VALUE / currentSegmentDuration) {
                        try {
                            long totalWeightedTime = safeAdd(qoeTotalBitrateWeightedTime, qoeCurrentBitrate * currentSegmentDuration);
                            long totalTime = safeAdd(qoeTotalActiveTime, currentSegmentDuration);

                            if (totalTime > 0) {
                                return Math.round((double) totalWeightedTime / totalTime);
                            }
                        } catch (ArithmeticException e) {
                            // Overflow in time-weighted bitrate calculation - reset to prevent future overflows
                            NRLog.w("QoE bitrate calculation overflow - resetting accumulated values to start fresh");
                            qoeTotalBitrateWeightedTime = 0L;
                            qoeTotalActiveTime = 0L;
                        }
                    }
                }
                // If current segment has zero duration, check if we have accumulated data
                else if (qoeTotalActiveTime > 0) {
                    return Math.round((double) qoeTotalBitrateWeightedTime / qoeTotalActiveTime);
                }
                // If we have current bitrate but no accumulated time and zero segment duration,
                // return current bitrate as the average (single point average)
                else if (qoeTotalActiveTime == 0 && currentSegmentDuration == 0) {
                    return qoeCurrentBitrate;
                }
            }
        }

        // Fallback to accumulated data only
        if (qoeTotalActiveTime != null && qoeTotalActiveTime > 0 && qoeTotalBitrateWeightedTime != null) {
            return Math.round((double) qoeTotalBitrateWeightedTime / qoeTotalActiveTime);
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
        qoeHadStartupFailure = false;
        qoeTotalRebufferingTime = 0L;
        qoeBitrateSum = 0L;
        qoeBitrateCount = 0L;
        qoeLastTrackedBitrate = null; // Reset cache
        qoeStartupTime = null; // Reset cached startup time for new view session
        startupPeriodAdTime = null;
        startupPeriodPauseTime = 0L; // Reset pause time tracking for new view session
        hasContentStarted = false;
        initialBufferingHappened = false; // Reset initial buffering flag for new view session

        // Reset time-weighted bitrate fields
        qoeCurrentBitrate = null;
        qoeLastRenditionChangeTime = null;
        qoeTotalBitrateWeightedTime = 0L;
        qoeTotalActiveTime = 0L;
        qoeBitrateTimerPaused = false;

        // Reset QOE provider fields for new view session
        pendingQoeForNextHarvest = null;
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
            if (totalPlaytime != null && totalPlaytime > 0) {
                // Error occurred after content started playing, so it's a playback failure
                qoeHadPlaybackFailure = true;
            } else {
                // Error occurred before content started playing, so it's a startup failure
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

        addTimeSinceEntry(CONTENT_HEARTBEAT, "timeSinceLastHeartbeat", "^(CONTENT_[A-Z_]+|QOE_[A-Z_]+)$");
        addTimeSinceEntry(AD_HEARTBEAT, "timeSinceLastAdHeartbeat", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_REQUEST, "timeSinceRequested", "^(CONTENT_[A-Z_]+|QOE_[A-Z_]+)$");
        addTimeSinceEntry(AD_REQUEST, "timeSinceAdRequested", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_START, "timeSinceStarted", "^(CONTENT_[A-Z_]+|QOE_[A-Z_]+)$");
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

        addTimeSinceEntry(CONTENT_ERROR, "timeSinceLastError", "^(CONTENT_[A-Z_]+|QOE_[A-Z_]+)$");
        addTimeSinceEntry(AD_ERROR, "timeSinceLastAdError", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_RENDITION_CHANGE, "timeSinceLastRenditionChange", "^CONTENT_RENDITION_CHANGE$");
        addTimeSinceEntry(AD_RENDITION_CHANGE, "timeSinceLastAdRenditionChange", "^AD_RENDITION_CHANGE$");

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
            qoeBitrateSum = safeAdd(qoeBitrateSum, bitrate);
            qoeBitrateCount++;
        }
        // QoE bitrate tracking completed
    }


    public void sendVideoErrorEvent(String action, Map<String, Object> attributes) {
        updatePlaytime();
        super.sendVideoErrorEvent(action, attributes);
    }

    /**
     * Safe addition with overflow detection, compatible with Android API 16+
     * Equivalent to Math.addExact but works on older Android versions
     *
     * @param a first operand
     * @param b second operand
     * @return the sum
     * @throws ArithmeticException if the result overflows a long
     */
    private static long safeAdd(long a, long b) {
        long result = a + b;

        // Check for overflow using the same logic as Math.addExact
        if (((a ^ result) & (b ^ result)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return result;
    }
}