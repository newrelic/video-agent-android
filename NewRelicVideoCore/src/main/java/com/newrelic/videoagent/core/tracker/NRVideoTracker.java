package com.newrelic.videoagent.core.tracker;

import android.os.Handler;

import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.model.NRTimeSince;
import com.newrelic.videoagent.core.model.NRTrackerState;
import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.core.harvest.QoeProvider;
import com.newrelic.videoagent.core.qoe.NRQoEAggregator;
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

    // QoE (Quality of Experience) aggregator — owns all QoE KPI state + math (thread-safe).
    // Startup ad time is still computed by the tracker (from totalAdPlaytime) and pushed in.
    private final NRQoEAggregator qoeAggregator = new NRQoEAggregator();
    private Long startupPeriodAdTime; // Ad time during startup; pushed into the aggregator at CONTENT_START

    // QOE_AGGREGATE provider fields
    private boolean qoeProviderRegistered = false;
    private volatile Map<String, Object> pendingQoeForNextHarvest = null; // For CONTENT_END (volatile for thread safety)
    private Map<String, Object> lastSentQoeKpis = null; // Snapshot of last sent QoE KPIs for dirty check
    private volatile Map<String, Object> cachedStandardAttributes = null; // Cached attributes for thread-safe access

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
        // QoE KPI state lives in the aggregator; only the tracker-side startup ad time is reset here.
        startupPeriodAdTime = 0L;
    }

    /**
     * Dispose of the tracker.
     *
     * Stop heartbeats and call `super.dispose()`.
     */
    public void dispose() {
        super.dispose();
        stopHeartbeat();
        // Clean up pending QOE to prevent memory leaks
        pendingQoeForNextHarvest = null;
        // Unregister the QOE provider so a disposed tracker is no longer polled at harvest.
        if (qoeProviderRegistered && NRVideo.getInstance() != null
                && NRVideo.getInstance().getHarvestManager() != null) {
            NRVideo.getInstance().getHarvestManager().unregisterQoeProvider(this);
        }
        qoeProviderRegistered = false;
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
                attr.put("contentManifestBitrate", getManifestBitrate());
                attr.put("contentSegmentDownloadBitrate", getSegmentDownloadBitrate());
                attr.put("contentNetworkDownloadBitrate", getNetworkDownloadBitrate());
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
        // QoE feed + attribute caching now happen in onQoeEvent(), after timeSince attributes
        // are applied (NRTracker.sendEvent), so the aggregator always sees a fully-assembled map.
        return attr;
    }

    /**
     * QoE hook — fires from NRTracker.sendEvent after attributes are fully assembled
     * (post getAttributes + timeSinceTable.applyAttributes). Feeds the aggregator and caches the
     * fully-assembled snapshot used to build the QOE_AGGREGATE envelope at harvest time.
     */
    @Override
    protected void onQoeEvent(String action, Map<String, Object> attributes) {
        if (state.isAd || QOE_AGGREGATE.equals(action) || action == null || !action.startsWith("CONTENT_")) {
            return;
        }
        // Push the startup ad time before CONTENT_START is processed by the aggregator.
        if (CONTENT_START.equals(action)) {
            qoeAggregator.setStartupAdTime(startupPeriodAdTime == null ? 0L : startupPeriodAdTime);
        }
        // Phase 1: adBreakActive is inert (false); the bitrate timer is still driven by the
        // explicit sender call-sites. isPlaying is reserved for a later phase.
        qoeAggregator.processAction(action, attributes, state.isPlaying, false);
        // Cache the fully-assembled snapshot for the harvest-time QOE envelope.
        cachedStandardAttributes = new HashMap<>(attributes);
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
                // Register QOE provider at CONTENT_REQUEST (like iOS) so QoE is captured
                // even if CONTENT_START never happens (e.g., startup error)
                if (!qoeProviderRegistered && configuration != null && configuration.isQoeAggregateEnabled()) {
                    if (NRVideo.getInstance() != null && NRVideo.getInstance().getHarvestManager() != null) {
                        NRVideo.getInstance().getHarvestManager().registerQoeProvider(this);
                        qoeProviderRegistered = true;
                        NRLog.d("QOE provider registered at CONTENT_REQUEST");
                    }
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

                // startupPeriodAdTime (set above) is pushed into the aggregator by onQoeEvent
                // when it processes CONTENT_START; the aggregator caches startup time there too.
                sendVideoEvent(CONTENT_START);

                // Resume bitrate timer when content starts playing
                qoeAggregator.resumeBitrateTimer();
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
                qoeAggregator.pauseBitrateTimer();
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

            // QoE: accumulate startup-period pause time (excluded from startup time).
            // Only meaningful before content starts; the aggregator ignores it once started.
            if (!state.isAd && !qoeAggregator.hasContentStarted()) {
                Map<String, Object> resumeAttributes = getAttributes(CONTENT_RESUME, null);
                Object timeSincePaused = resumeAttributes.get("timeSincePaused");
                if (timeSincePaused instanceof Long) {
                    qoeAggregator.addStartupPauseTime((Long) timeSincePaused);
                }
            }

            if (state.isAd) {
                sendVideoAdEvent(AD_RESUME);
            } else {
                sendVideoEvent(CONTENT_RESUME);
                // Resume bitrate timer when playback resumes (only if not buffering or seeking)
                if (!state.isBuffering && !state.isSeeking) {
                    qoeAggregator.resumeBitrateTimer();
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
                // Build final QOE at CONTENT_END and mark for next harvest cycle
                if (configuration != null && configuration.isQoeAggregateEnabled() && qoeProviderRegistered) {
                    // Build final QOE with complete metrics
                    Map<String, Object> finalQoe = buildQoeEventWithStandardAttributes();

                    // Mark as final QOE so HarvestManager knows to unregister provider
                    finalQoe.put("isFinalQoe", true);

                    // Store for next harvest cycle (will be sent with priority)
                    pendingQoeForNextHarvest = finalQoe;

                    NRLog.d("Final QOE_AGGREGATE prepared at CONTENT_END, will send on next harvest cycle");
                }

                sendVideoEvent(CONTENT_END);
            }

            stopHeartbeat();

            viewIdIndex++;
            numberOfErrors = 0;
            playtimeSinceLastEventTimestamp = 0L;
            playtimeSinceLastEvent = 0L;
            totalPlaytime = 0L;
            // Reset QoE state for new view session (aggregator KPIs + tracker-side dirty-check
            // snapshot). pendingQoeForNextHarvest is intentionally NOT cleared here.
            qoeAggregator.reset();
            lastSentQoeKpis = null;
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
                qoeAggregator.pauseBitrateTimer();
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
                    qoeAggregator.resumeBitrateTimer();
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
                qoeAggregator.pauseBitrateTimer();
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
                    qoeAggregator.resumeBitrateTimer();
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
        // Priority 1: Check if there's a pending final QOE from CONTENT_END
        if (pendingQoeForNextHarvest != null) {
            Map<String, Object> finalQoe = pendingQoeForNextHarvest;
            pendingQoeForNextHarvest = null; // Clear after retrieving
            NRLog.d("Sending pending final QOE_AGGREGATE from CONTENT_END");
            return finalQoe; // HarvestManager will see "isFinalQoe" flag and unregister provider
        }

        // Priority 2: Generate regular harvest QOE if conditions are met
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
                // Calculate current QoE KPIs from the aggregator
                Map<String, Object> currentKpis = qoeAggregator.generateAggregateAttributes(computeRealtimePlaytimeMs());

                // Dirty check: Only send if KPI values have changed since last send
                if (currentKpis != null && haveQoeKpisChanged(currentKpis)) {
                    // Build QOE event with standard attributes
                    Map<String, Object> qoeEvent = buildQoeEventWithStandardAttributes();

                    // Update snapshot for next dirty check
                    lastSentQoeKpis = new HashMap<>(currentKpis);

                    NRLog.d("QOE_AGGREGATE generated for harvest cycle " + harvestCycleNumber + " (KPIs changed)");
                    return qoeEvent;
                } else {
                    NRLog.d("QOE_AGGREGATE skipped for harvest cycle " + harvestCycleNumber + " (no KPI changes)");
                }
            }
        }

        return null; // Don't generate QOE for this cycle
    }

    /**
     * Build QOE event with all standard video tracking attributes.
     * Thread-safe: Uses cached attributes from last video event instead of
     * accessing player directly (which requires main thread).
     *
     * @return Complete QOE event map with standard attributes
     */
    private Map<String, Object> buildQoeEventWithStandardAttributes() {
        // Start with QOE KPI attributes from the aggregator (real-time playtime supplied by tracker)
        Map<String, Object> qoeEvent = qoeAggregator.generateAggregateAttributes(computeRealtimePlaytimeMs());
        if (qoeEvent == null) {
            // Aggregator gate: no CONTENT_REQUEST seen yet (should not happen at build time)
            qoeEvent = new HashMap<>();
        }

        // Add filtered cached attributes (match iOS behavior)
        // Note: Cache is populated by video events (CONTENT_START, HEARTBEAT, etc.) on main thread
        // This avoids thread safety issues with ExoPlayer which requires main thread access
        if (cachedStandardAttributes != null) {
            // Filter attributes to match iOS implementation:
            // - Keep timeSinceRequested and timeSinceStarted (session context)
            // - Filter out all other timeSince* attributes (event-specific)
            // - Filter out bufferType (event-specific)
            for (Map.Entry<String, Object> entry : cachedStandardAttributes.entrySet()) {
                String key = entry.getKey();

                // Filter out event-specific timeSince* attributes
                // Keep only timeSinceRequested and timeSinceStarted for session context
                if (key.startsWith("timeSince")
                    && !key.equals("timeSinceRequested")
                    && !key.equals("timeSinceStarted")) {
                    continue;  // Skip event-specific timeSince
                }

                // Filter out buffer-specific attribute
                if (key.equals("bufferType")) {
                    continue;  // Skip buffer-specific attribute
                }

                // Include all other attributes
                qoeEvent.put(key, entry.getValue());
            }
        } else {
            NRLog.w("QOE: No cached attributes available yet (this is normal for very first QOE before any video events)");
        }

        // Explicitly ensure session context attributes are present by applying timeSince values
        // This guarantees timeSinceRequested and timeSinceStarted are always included
        Map<String, Object> freshTimeSinceAttributes = new HashMap<>();
        timeSinceTable.applyAttributes(QOE_AGGREGATE, freshTimeSinceAttributes);

        // Extract and add the session context timeSince attributes
        if (freshTimeSinceAttributes.containsKey("timeSinceRequested")) {
            qoeEvent.put("timeSinceRequested", freshTimeSinceAttributes.get("timeSinceRequested"));
        }
        if (freshTimeSinceAttributes.containsKey("timeSinceStarted")) {
            qoeEvent.put("timeSinceStarted", freshTimeSinceAttributes.get("timeSinceStarted"));
        }

        // Log to verify these critical attributes are present
        if (qoeEvent.containsKey("timeSinceRequested") || qoeEvent.containsKey("timeSinceStarted")) {
            NRLog.d("QOE session context: timeSinceRequested=" + qoeEvent.get("timeSinceRequested") +
                    ", timeSinceStarted=" + qoeEvent.get("timeSinceStarted"));
        } else {
            NRLog.w("QOE: Session context attributes (timeSinceRequested/timeSinceStarted) not available yet");
        }

        // Add/override instrumentation attributes (same as NRTracker.sendEvent())
        qoeEvent.put("agentSession", getAgentSession());
        qoeEvent.put("instrumentation.provider", "newrelic");
        qoeEvent.put("instrumentation.name", getInstrumentationName());
        qoeEvent.put("instrumentation.version", getCoreVersion());

        // Add/override core attributes
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
     * Check if QoE KPI attributes have changed since the last sent QoE event.
     * Implements dirty check pattern like iOS to prevent sending duplicate QoE with identical values.
     *
     * @param currentKpis Current QoE KPI attributes
     * @return true if KPIs have changed or this is the first QoE, false if identical to last send
     */
    private boolean haveQoeKpisChanged(Map<String, Object> currentKpis) {
        // First QoE always sends
        if (lastSentQoeKpis == null) {
            return true;
        }

        // Compare each KPI attribute
        // KPI attributes: startupTime, peakBitrate, averageBitrate, totalPlaytime,
        // totalRebufferingTime, rebufferingRatio, hadStartupError, hadPlaybackError
        for (String key : currentKpis.keySet()) {
            Object currentValue = currentKpis.get(key);
            Object lastValue = lastSentQoeKpis.get(key);

            // If key is missing in last snapshot, consider it changed
            if (lastValue == null && currentValue != null) {
                return true;
            }

            // Compare values (handles Long, Double, Boolean, String)
            if (currentValue != null && !currentValue.equals(lastValue)) {
                return true;
            }
        }

        // Check if any keys were removed
        for (String key : lastSentQoeKpis.keySet()) {
            if (!currentKpis.containsKey(key)) {
                return true;
            }
        }

        // No changes detected
        return false;
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
     * Compute real-time playtime (cumulative totalPlaytime topped up with time since the last
     * event while playing). Lives on the tracker because it reads tracker-private playtime state;
     * passed into the aggregator so rebufferingRatio/totalPlaytime stay exact.
     */
    private long computeRealtimePlaytimeMs() {
        long elapsedTime = (totalPlaytime == null) ? 0L : totalPlaytime;
        if (state.isPlaying && playtimeSinceLastEventTimestamp > 0) {
            long timeSinceLastUpdate = System.currentTimeMillis() - playtimeSinceLastEventTimestamp;
            if (timeSinceLastUpdate > 0) {
                elapsedTime += timeSinceLastUpdate;
            }
        }
        return elapsedTime;
    }

    /** Package-private accessor for tests. */
    NRQoEAggregator getQoeAggregator() {
        return qoeAggregator;
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
                // Error occurred after content started playing, so it's a playback error
                qoeAggregator.recordPlaybackError();
            } else {
                // Error occurred before content started playing, so it's a startup error
                qoeAggregator.recordStartupError();
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
     * Get manifest (indicated) bitrate in bits per second. The throughput required to play the stream as advertised by the server in the manifest.
     *
     * @return Attribute.
     */
    public Long getManifestBitrate() {
        return null;
    }

    /**
     * Get measured bitrate in bits per second. The actual, empirical throughput measured across all downloaded media.
     *
     * @return Attribute.
     */
    public Long getSegmentDownloadBitrate() {
        return null;
    }

    /**
     * Get download bitrate in bits per second.
     *
     * @return Attribute.
     */
    public Long getNetworkDownloadBitrate() {
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





    public void sendVideoErrorEvent(String action, Map<String, Object> attributes) {
        updatePlaytime();
        super.sendVideoErrorEvent(action, attributes);
    }


}