package com.newrelic.videoagent.mediatailor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A contiguous server-stitched ad slot in the player timeline (equivalent to a
 * MediaTailor "avail" or a VAST {@code <AdBreak>}). One or more
 * {@link MTAdPod}s play back-to-back inside the break.
 *
 * <p>Populated from two sources that are merged by
 * {@link com.newrelic.videoagent.mediatailor.schedule.MTAdScheduleMerger}:</p>
 * <ol>
 *   <li>Manifest parse ({@link com.newrelic.videoagent.mediatailor.detection.MTDashParser}
 *       / {@link com.newrelic.videoagent.mediatailor.detection.MTHlsParser}) —
 *       provides timing and pod boundaries from discontinuities.</li>
 *   <li>Tracking API ({@link com.newrelic.videoagent.mediatailor.net.MTTrackingClient})
 *       — fills in titles, VAST IDs, tracking beacon URLs, skippability.</li>
 * </ol>
 *
 * <p>All time fields are <strong>milliseconds</strong>, relative to the
 * content timeline (first content frame = 0). "Fired" flags prevent
 * duplicate emission of AD_BREAK_START / AD_START / quartile events from the
 * tracker's playhead-poll loop.</p>
 */
public class MTAdBreak {

    public String id;
    public long startTimeMs;
    public long durationMs;
    public long endTimeMs;

    public String title;
    public String adId;                    // VAST <Ad id="…">
    public String creativeId;              // VAST <Creative id="…">
    public String adSystem;                // ad server name
    public String creativeSequence;        // 1-based index
    public String vastAdId;
    public String skipOffset;
    public String availProgramDateTime;    // wall clock of avail (Live)
    public String adPosition;
    public boolean confirmedByTracking;

    /**
     * The tracking response returned this avail with an empty {@code ads}
     * array. MediaTailor treats that as a normal no-fill scenario — content
     * plays through the slot — but the tracker must not fire AD_START or
     * quartile events, otherwise the break appears as a rendered ad with
     * zero impressions in reporting. Instead the tracker emits AD_BREAK_START
     * followed by AD_ERROR(NO_FILL) followed by AD_BREAK_END.
     */
    public boolean isNoFill;

    /**
     * The manifest parser saw a different number of pods than the tracking
     * response's {@code ads} array reported for this avail. Manifest pod
     * boundaries were kept as ground truth (they come from actual segment /
     * discontinuity data), and tracking metadata was matched to the closest
     * pod within tolerance — some pods may therefore lack metadata, or some
     * tracking ads may have been left unassigned. Surfaces the mismatch as a
     * diagnostic without forcing quartile timing onto the wrong slot.
     */
    public boolean podCountMismatch;

    public boolean hasFiredStart;
    public boolean hasFiredEnd;
    public boolean hasFiredAdStart;
    public boolean hasFiredNoFillError;
    public boolean hasFiredQ1;
    public boolean hasFiredQ2;
    public boolean hasFiredQ3;

    public final List<MTAdPod> pods = new ArrayList<>();

    public MTAdBreak(String id, long startTimeMs, long durationMs) {
        this.id = id;
        this.startTimeMs = startTimeMs;
        this.durationMs = durationMs;
        this.endTimeMs = startTimeMs + durationMs;
    }

    public boolean contains(long positionMs) {
        return positionMs >= startTimeMs && positionMs < endTimeMs;
    }

    public MTAdPod findActivePod(long positionMs) {
        for (MTAdPod pod : pods) {
            if (pod.contains(positionMs)) {
                return pod;
            }
        }
        return null;
    }

    /**
     * Stable identity for the underlying MediaTailor avail across polls.
     *
     * <p>On live streams the HLS sliding window rotates, so {@link #startTimeMs}
     * (derived from segment time or the live edge) shifts on every manifest
     * refresh. A tolerance-based time match is therefore not stable for the
     * same avail across polls — the same ad break can appear at 100ms on one
     * poll and 5100ms on the next, and pure time matching treats them as two
     * distinct breaks.</p>
     *
     * <p>{@code availId} and {@code availProgramDateTime} both come from the
     * tracking response, and together identify the avail unambiguously across
     * window rotations. When either is absent (VOD, or the break is still
     * only manifest-detected and hasn't been enriched by tracking yet), this
     * returns {@code null} so callers fall back to time-based matching, which
     * is safe on VOD where {@link #startTimeMs} is stable.</p>
     */
    public String identityKey() {
        if (id == null || id.isEmpty()) return null;
        if (availProgramDateTime == null || availProgramDateTime.isEmpty()) return null;
        return id + "|" + availProgramDateTime;
    }
}
