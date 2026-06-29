package com.newrelic.videoagent.core.qoe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.newrelic.videoagent.core.NRDef.CONTENT_REQUEST;
import static com.newrelic.videoagent.core.NRDef.CONTENT_START;
import static com.newrelic.videoagent.core.NRDef.CONTENT_BUFFER_END;
import static com.newrelic.videoagent.core.NRDef.CONTENT_RENDITION_CHANGE;
import static com.newrelic.videoagent.core.NRDef.CONTENT_PAUSE;
import static com.newrelic.videoagent.core.NRDef.CONTENT_RESUME;

/**
 * Standalone, thread-safe QoE (Quality of Experience) aggregator.
 */
public final class NRQoEAggregator {

    // ---- Lifecycle gate ----------------------------------------------------
    private boolean hasReceivedRequest;

    // ---- Basic QoE KPIs ----------------------------------------------------
    private Long qoePeakBitrate;
    private Boolean qoeHadPlaybackError;
    private Boolean qoeHadStartupError;
    private Long qoeTotalRebufferingTime;
    private Long qoeBitrateSum;
    private Long qoeBitrateCount;
    private Long qoeLastTrackedBitrate;
    private Long qoeStartupTime;

    // ---- Startup-period exclusions -----------------------------------------
    private Long startupPeriodAdTime;
    private Long startupPeriodPauseTime;
    private boolean hasContentStarted;
    private boolean initialBufferingHappened;

    // ---- Download-rate tracking --------------------------------------------
    private Long qoeDownloadRateSum;
    private Long qoeDownloadRateCount;
    private Long qoeMinDownloadRate;
    private Long qoeMaxDownloadRate;

    // ---- Rendition switch metrics ------------------------------------------
    private long qoeTotalSwitchUps;
    private long qoeTotalSwitchDowns;
    private long qoeTotalTimeSwitchedDown;
    private long qoeMaxRenditionBitrate;
    private Long qoeSwitchedDownSinceMs;

    // ---- Pause-time tracking -----------------------------------------------
    private long qoeTotalPauseTime;
    private Long qoePauseStartMs;

    // ---- Distinct renditions (keyed by width*height) -----------------------
    private final Set<Long> qoePlayedRenditions = new HashSet<>();

    // ---- Time-weighted bitrate ---------------------------------------------
    private Long qoeCurrentBitrate;
    private Long qoeLastRenditionChangeTime;
    private Long qoeTotalBitrateWeightedTime;
    private Long qoeTotalActiveTime;
    private boolean qoeBitrateTimerPaused;

    public NRQoEAggregator() {
        initState();
    }

    /** Initial values */
    private void initState() {
        hasReceivedRequest = false;

        qoePeakBitrate = 0L;
        qoeHadPlaybackError = false;
        qoeHadStartupError = false;
        qoeTotalRebufferingTime = 0L;
        qoeBitrateSum = 0L;
        qoeBitrateCount = 0L;
        qoeLastTrackedBitrate = null;
        qoeStartupTime = null;

        startupPeriodAdTime = 0L;
        startupPeriodPauseTime = 0L;
        hasContentStarted = false;
        initialBufferingHappened = false;

        qoeCurrentBitrate = null;
        qoeLastRenditionChangeTime = null;
        qoeTotalBitrateWeightedTime = 0L;
        qoeTotalActiveTime = 0L;
        qoeBitrateTimerPaused = false;

        qoeDownloadRateSum = 0L;
        qoeDownloadRateCount = 0L;
        qoeMinDownloadRate = null;
        qoeMaxDownloadRate = null;

        qoeTotalSwitchUps = 0L;
        qoeTotalSwitchDowns = 0L;
        qoeTotalTimeSwitchedDown = 0L;
        qoeMaxRenditionBitrate = 0L;
        qoeSwitchedDownSinceMs = null;

        qoeTotalPauseTime = 0L;
        qoePauseStartMs = null;

        qoePlayedRenditions.clear();
    }

    // =========================================================================
    // Public API (all synchronized)
    // =========================================================================

    /**
     * Feed one fully-assembled content event. Runs the always-on extractors (download rate,
     * bitrate, renditions) and dispatches by action. {@code isPlaying}/{@code adBreakActive}
     * are reserved for a later phase (bitrate timer is currently driven by explicit calls).
     */
    public synchronized void processAction(String action, Map<String, Object> attributes,
                                           boolean isPlaying, boolean adBreakActive) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }

        // Always-on extractors (run for every content event, as getAttributes() did before).
        trackDownloadRateMetrics(attributes);
        trackBitrateFromProcessedAttributes(action, attributes);
        trackPlayedRenditions(attributes);

        if (CONTENT_REQUEST.equals(action)) {
            hasReceivedRequest = true;
        } else if (CONTENT_START.equals(action)) {
            handleStart(attributes);
        } else if (CONTENT_BUFFER_END.equals(action)) {
            handleBufferEnd(attributes);
        } else if (CONTENT_RENDITION_CHANGE.equals(action)) {
            handleRenditionChange(attributes);
        } else if (CONTENT_PAUSE.equals(action)) {
            handlePause();
        } else if (CONTENT_RESUME.equals(action)) {
            handleResume();
        }
    }

    /**
     * Build the QoE KPI map. Returns {@code null} until a CONTENT_REQUEST has been fed (gate).
     *
     * @param realtimePlaytimeMs the tracker-computed real-time playtime (used for totalPlaytime
     *                           and the rebufferingRatio denominator — these depend on
     *                           tracker-private state, so they are passed in).
     */
    public synchronized Map<String, Object> generateAggregateAttributes(long realtimePlaytimeMs) {
        if (!hasReceivedRequest) {
            return null;
        }

        Map<String, Object> kpiAttributes = new HashMap<>();

        if (qoePeakBitrate != null && qoePeakBitrate > 0) {
            kpiAttributes.put("peakBitrate", qoePeakBitrate);
        }

        if (qoeHadPlaybackError == null) {
            qoeHadPlaybackError = false;
        }
        kpiAttributes.put("hadPlaybackError", qoeHadPlaybackError);

        if (qoeTotalRebufferingTime == null) {
            qoeTotalRebufferingTime = 0L;
        }
        kpiAttributes.put("totalRebufferingTime", qoeTotalRebufferingTime);

        long elapsedTime = realtimePlaytimeMs;

        if (elapsedTime > 0) {
            double rebufferingRatio = ((double) qoeTotalRebufferingTime / elapsedTime) * 100;
            kpiAttributes.put("rebufferingRatio", rebufferingRatio);
        } else {
            kpiAttributes.put("rebufferingRatio", 0.0);
        }

        kpiAttributes.put("totalPlaytime", elapsedTime);

        Long timeWeightedAverage = calculateTimeWeightedAverageBitrate();
        if (timeWeightedAverage != null) {
            kpiAttributes.put("averageBitrate", timeWeightedAverage);
        } else if (qoeBitrateCount != null && qoeBitrateCount > 0 && qoeBitrateSum != null) {
            long averageBitrate = Math.round((double) qoeBitrateSum / qoeBitrateCount);
            kpiAttributes.put("averageBitrate", averageBitrate);
        }

        kpiAttributes.put("qoeAggregateVersion", "1.1.0");

        if (qoeStartupTime != null && qoeStartupTime >= 0) {
            kpiAttributes.put("startupTime", qoeStartupTime);
        } else {
            kpiAttributes.put("startupTime", 0L);
        }

        if (qoeHadStartupError == null) {
            qoeHadStartupError = false;
        }
        kpiAttributes.put("hadStartupError", qoeHadStartupError);

        if (qoeDownloadRateCount != null && qoeDownloadRateCount > 0) {
            long avgDownloadRate = Math.round((double) qoeDownloadRateSum / qoeDownloadRateCount);
            kpiAttributes.put("avgDownloadRate", avgDownloadRate);
        }
        if (qoeMinDownloadRate != null) {
            kpiAttributes.put("minDownloadRate", qoeMinDownloadRate);
        }
        if (qoeMaxDownloadRate != null) {
            kpiAttributes.put("maxDownloadRate", qoeMaxDownloadRate);
        }

        kpiAttributes.put("totalSwitchUps", qoeTotalSwitchUps);
        kpiAttributes.put("totalSwitchDowns", qoeTotalSwitchDowns);
        long openMs = (qoeSwitchedDownSinceMs == null) ? 0L : System.currentTimeMillis() - qoeSwitchedDownSinceMs;
        kpiAttributes.put("totalTimeSwitchedDown", safeAdd(qoeTotalTimeSwitchedDown, openMs));

        long openPauseMs = (qoePauseStartMs == null) ? 0L : System.currentTimeMillis() - qoePauseStartMs;
        kpiAttributes.put("totalPauseTime", safeAdd(qoeTotalPauseTime, openPauseMs));

        kpiAttributes.put("totalRenditions", (long) qoePlayedRenditions.size());

        return kpiAttributes;
    }

    /** Reset per-view QoE state (mirrors the old resetQoeMetrics, KPI fields only). */
    public synchronized void reset() {
        qoePeakBitrate = null;
        qoeHadPlaybackError = false;
        qoeHadStartupError = false;
        qoeTotalRebufferingTime = 0L;
        qoeBitrateSum = 0L;
        qoeBitrateCount = 0L;
        qoeLastTrackedBitrate = null;
        qoeStartupTime = null;
        startupPeriodAdTime = null;
        startupPeriodPauseTime = 0L;
        hasContentStarted = false;
        initialBufferingHappened = false;

        qoeCurrentBitrate = null;
        qoeLastRenditionChangeTime = null;
        qoeTotalBitrateWeightedTime = 0L;
        qoeTotalActiveTime = 0L;
        qoeBitrateTimerPaused = false;

        qoeDownloadRateSum = 0L;
        qoeDownloadRateCount = 0L;
        qoeMinDownloadRate = null;
        qoeMaxDownloadRate = null;

        qoeTotalSwitchUps = 0L;
        qoeTotalSwitchDowns = 0L;
        qoeTotalTimeSwitchedDown = 0L;
        qoeMaxRenditionBitrate = 0L;
        qoeSwitchedDownSinceMs = null;

        qoeTotalPauseTime = 0L;
        qoePauseStartMs = null;

        qoePlayedRenditions.clear();

        hasReceivedRequest = false;
    }

    /** Pre-roll ad time observed before CONTENT_START (excluded from startup time). */
    public synchronized void setStartupAdTime(long ms) {
        startupPeriodAdTime = ms;
    }

    /** Pause time during the startup period; ignored once content has started. */
    public synchronized void addStartupPauseTime(long timeSincePaused) {
        if (hasContentStarted || timeSincePaused <= 0) {
            return;
        }
        try {
            startupPeriodPauseTime = safeAdd(startupPeriodPauseTime, timeSincePaused);
        } catch (ArithmeticException e) {
            startupPeriodPauseTime = timeSincePaused;
        }
    }

    public synchronized void recordStartupError() {
        qoeHadStartupError = true;
    }

    /** Whether CONTENT_START has been processed (used by the tracker to gate startup-pause accrual). */
    public synchronized boolean hasContentStarted() {
        return hasContentStarted;
    }

    public synchronized void recordPlaybackError() {
        qoeHadPlaybackError = true;
    }

    // =========================================================================
    // Action handlers (private, called under the lock)
    // =========================================================================

    private void handleStart(Map<String, Object> attributes) {
        hasContentStarted = true;

        if (qoeStartupTime != null) {
            return; // Already calculated
        }

        Object tsr = attributes.get("timeSinceRequested");
        Long timeSinceRequested = (tsr instanceof Long) ? (Long) tsr : null;

        if (timeSinceRequested != null && timeSinceRequested >= 0) {
            long totalExclusionTime = 0L;
            if (startupPeriodAdTime != null && startupPeriodAdTime > 0) {
                totalExclusionTime += startupPeriodAdTime;
            }
            if (startupPeriodPauseTime != null && startupPeriodPauseTime > 0) {
                totalExclusionTime += startupPeriodPauseTime;
            }
            qoeStartupTime = Math.max(timeSinceRequested - totalExclusionTime, 0L);
        } else {
            qoeStartupTime = 0L;
        }
    }

    private void handleBufferEnd(Map<String, Object> attributes) {
        Object timeSinceBufferBegin = attributes.get("timeSinceBufferBegin");
        if (timeSinceBufferBegin instanceof Long && initialBufferingHappened) {
            try {
                qoeTotalRebufferingTime = safeAdd(qoeTotalRebufferingTime, (Long) timeSinceBufferBegin);
            } catch (ArithmeticException e) {
                qoeTotalRebufferingTime = (Long) timeSinceBufferBegin;
            }
        }
        if (!initialBufferingHappened) {
            initialBufferingHappened = true;
        }
    }

    private void handleRenditionChange(Map<String, Object> attributes) {
        Object shift = attributes.get("shift");
        if ("up".equals(shift))   qoeTotalSwitchUps++;
        if ("down".equals(shift)) qoeTotalSwitchDowns++;

        Long bitrate = extractBitrateValue(attributes.get("contentRenditionBitrate"));
        if (bitrate == null || bitrate <= 0) return;
        long now = System.currentTimeMillis();

        // Recovered to/above the session peak -> close the open below-max interval.
        if (qoeSwitchedDownSinceMs != null && bitrate >= qoeMaxRenditionBitrate) {
            qoeTotalTimeSwitchedDown = safeAdd(qoeTotalTimeSwitchedDown, now - qoeSwitchedDownSinceMs);
            qoeSwitchedDownSinceMs = null;
        }
        // New session peak.
        if (bitrate > qoeMaxRenditionBitrate) {
            qoeMaxRenditionBitrate = bitrate;
        }
        // Dropped below the peak with no interval open -> start one.
        if (bitrate < qoeMaxRenditionBitrate && qoeSwitchedDownSinceMs == null) {
            qoeSwitchedDownSinceMs = now;
        }
    }

    private void handlePause() {
        qoePauseStartMs = System.currentTimeMillis();
    }

    private void handleResume() {
        if (qoePauseStartMs != null) {
            qoeTotalPauseTime = safeAdd(qoeTotalPauseTime, System.currentTimeMillis() - qoePauseStartMs);
            qoePauseStartMs = null;
        }
    }

    // =========================================================================
    // Bitrate timer
    // =========================================================================

    public synchronized void pauseBitrateTimer() {
        if (qoeBitrateTimerPaused) {
            return;
        }
        if (qoeCurrentBitrate != null && qoeLastRenditionChangeTime != null && qoeCurrentBitrate > 0) {
            long currentTime = System.currentTimeMillis();
            if (qoeLastRenditionChangeTime > 0 && currentTime >= qoeLastRenditionChangeTime) {
                long segmentDuration = currentTime - qoeLastRenditionChangeTime;
                if (segmentDuration > 0) {
                    if (qoeCurrentBitrate <= Long.MAX_VALUE / segmentDuration) {
                        try {
                            qoeTotalBitrateWeightedTime = safeAdd(qoeTotalBitrateWeightedTime, qoeCurrentBitrate * segmentDuration);
                            qoeTotalActiveTime = safeAdd(qoeTotalActiveTime, segmentDuration);
                        } catch (ArithmeticException e) {
                            qoeTotalBitrateWeightedTime = 0L;
                            qoeTotalActiveTime = 0L;
                        }
                    }
                }
            }
        }
        qoeBitrateTimerPaused = true;
    }

    public synchronized void resumeBitrateTimer() {
        if (!qoeBitrateTimerPaused) {
            return;
        }
        qoeLastRenditionChangeTime = System.currentTimeMillis();
        qoeBitrateTimerPaused = false;
    }

    private void updateTimeWeightedBitrate(Long newBitrate) {
        long currentTime = System.currentTimeMillis();
        if (qoeCurrentBitrate != null && qoeLastRenditionChangeTime != null && qoeCurrentBitrate > 0) {
            if (qoeLastRenditionChangeTime > 0 && currentTime >= qoeLastRenditionChangeTime) {
                long segmentDuration = currentTime - qoeLastRenditionChangeTime;
                if (segmentDuration > 0) {
                    if (qoeCurrentBitrate <= Long.MAX_VALUE / segmentDuration) {
                        try {
                            qoeTotalBitrateWeightedTime = safeAdd(qoeTotalBitrateWeightedTime, qoeCurrentBitrate * segmentDuration);
                            qoeTotalActiveTime = safeAdd(qoeTotalActiveTime, segmentDuration);
                        } catch (ArithmeticException e) {
                            qoeTotalBitrateWeightedTime = 0L;
                            qoeTotalActiveTime = 0L;
                        }
                    }
                }
            }
        }
        qoeCurrentBitrate = newBitrate;
        qoeLastRenditionChangeTime = currentTime;
    }

    private Long calculateTimeWeightedAverageBitrate() {
        if (!qoeBitrateTimerPaused && qoeCurrentBitrate != null && qoeLastRenditionChangeTime != null && qoeCurrentBitrate > 0) {
            long currentTime = System.currentTimeMillis();
            if (qoeLastRenditionChangeTime > 0 && currentTime >= qoeLastRenditionChangeTime) {
                long currentSegmentDuration = currentTime - qoeLastRenditionChangeTime;
                if (currentSegmentDuration > 0) {
                    if (qoeCurrentBitrate <= Long.MAX_VALUE / currentSegmentDuration) {
                        try {
                            long totalWeightedTime = safeAdd(qoeTotalBitrateWeightedTime, qoeCurrentBitrate * currentSegmentDuration);
                            long totalTime = safeAdd(qoeTotalActiveTime, currentSegmentDuration);
                            if (totalTime > 0) {
                                return Math.round((double) totalWeightedTime / totalTime);
                            }
                        } catch (ArithmeticException e) {
                            qoeTotalBitrateWeightedTime = 0L;
                            qoeTotalActiveTime = 0L;
                        }
                    }
                } else if (qoeTotalActiveTime > 0) {
                    return Math.round((double) qoeTotalBitrateWeightedTime / qoeTotalActiveTime);
                } else if (qoeTotalActiveTime == 0 && currentSegmentDuration == 0) {
                    return qoeCurrentBitrate;
                }
            }
        }

        if (qoeTotalActiveTime != null && qoeTotalActiveTime > 0 && qoeTotalBitrateWeightedTime != null) {
            return Math.round((double) qoeTotalBitrateWeightedTime / qoeTotalActiveTime);
        }

        return null;
    }

    // =========================================================================
    // Always-on extractors
    // =========================================================================

    private void trackBitrateFromProcessedAttributes(String action, Map<String, Object> processedAttributes) {
        Long currentBitrate = extractBitrateValue(processedAttributes.get("contentBitrate"));
        if (currentBitrate == null || currentBitrate <= 0) {
            return;
        }
        if (qoeLastTrackedBitrate != null && qoeLastTrackedBitrate.equals(currentBitrate)) {
            return;
        }
        qoeLastTrackedBitrate = currentBitrate;
        updateQoeBitrateMetrics(currentBitrate, action);
    }

    private void trackDownloadRateMetrics(Map<String, Object> processedAttributes) {
        Long sample = extractBitrateValue(processedAttributes.get("contentNetworkDownloadBitrate"));
        if (sample == null || sample <= 0) {
            return;
        }
        if (qoeDownloadRateCount < Long.MAX_VALUE - 1) {
            qoeDownloadRateSum = safeAdd(qoeDownloadRateSum, sample);
            qoeDownloadRateCount++;
        }
        qoeMinDownloadRate = (qoeMinDownloadRate == null) ? sample : Math.min(qoeMinDownloadRate, sample);
        qoeMaxDownloadRate = (qoeMaxDownloadRate == null) ? sample : Math.max(qoeMaxDownloadRate, sample);
    }

    private void trackPlayedRenditions(Map<String, Object> processedAttributes) {
        Long w = extractBitrateValue(processedAttributes.get("contentRenditionWidth"));
        Long h = extractBitrateValue(processedAttributes.get("contentRenditionHeight"));
        if (w != null && w > 0 && h != null && h > 0) {
            qoePlayedRenditions.add(w * h);
        }
    }

    private void updateQoeBitrateMetrics(Long bitrate, String action) {
        updateTimeWeightedBitrate(bitrate);
        if (qoePeakBitrate == null || bitrate > qoePeakBitrate) {
            qoePeakBitrate = bitrate;
        }
        if (qoeBitrateCount < Long.MAX_VALUE - 1) {
            qoeBitrateSum = safeAdd(qoeBitrateSum, bitrate);
            qoeBitrateCount++;
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

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

    /** Overflow-safe long addition (Math.addExact equivalent, API 16+ safe). */
    private static long safeAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return result;
    }
}
