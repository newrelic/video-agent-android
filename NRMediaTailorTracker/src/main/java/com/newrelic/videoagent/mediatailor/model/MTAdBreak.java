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

    public boolean hasFiredStart;
    public boolean hasFiredEnd;
    public boolean hasFiredAdStart;
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
}
