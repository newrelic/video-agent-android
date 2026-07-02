package com.newrelic.videoagent.mediatailor.schedule;

import com.newrelic.videoagent.mediatailor.MTConstants;
import com.newrelic.videoagent.mediatailor.model.MTAdBreak;
import com.newrelic.videoagent.mediatailor.model.MTAdPod;
import com.newrelic.videoagent.mediatailor.net.MTTrackingResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Combines ad-break data from two independent sources into a single sorted
 * schedule owned by the tracker:
 *
 * <ol>
 *   <li><b>Manifest parse</b> — timing and pod boundaries discovered by
 *       {@link com.newrelic.videoagent.mediatailor.detection.MTDashParser}
 *       or {@link com.newrelic.videoagent.mediatailor.detection.MTHlsParser}.</li>
 *   <li><b>Tracking API</b> — rich VAST metadata (titles, creative IDs,
 *       ad system, skip offsets, tracking beacons) returned by
 *       {@link com.newrelic.videoagent.mediatailor.net.MTTrackingClient}.</li>
 * </ol>
 *
 * <p>De-duplication uses {@code startTimeMs} matching within
 * {@link MTConstants#AD_TIMING_TOLERANCE_MS} so that a break first seen by
 * the manifest parser and later confirmed by the tracking API is kept as a
 * single entry rather than counted twice.</p>
 *
 * <p>Pure utility — no I/O, no mutable state — so it's safe to call from the
 * main thread during poll-loop ticks.</p>
 */
public final class MTAdScheduleMerger {

    private MTAdScheduleMerger() {}

    /**
     * Merges newly-detected ad breaks into the existing schedule, de-duplicating
     * by {@code startTimeMs} within {@link MTConstants#AD_TIMING_TOLERANCE_MS}.
     * Preserves fired-flag state on existing breaks.
     */
    public static List<MTAdBreak> mergeSchedule(List<MTAdBreak> existing, List<MTAdBreak> incoming) {
        List<MTAdBreak> merged = new ArrayList<>(existing);
        for (MTAdBreak candidate : incoming) {
            if (candidate == null) continue;
            MTAdBreak match = findMatch(merged, candidate);
            if (match == null) {
                merged.add(candidate);
            } else if (!match.confirmedByTracking && candidate.confirmedByTracking) {
                copyMetadata(candidate, match);
            }
        }
        sortByStart(merged);
        return merged;
    }

    /**
     * Enriches the schedule with tracking API metadata. Existing breaks gain
     * titles/creativeIds/pods; avails not present in the schedule are appended.
     */
    public static List<MTAdBreak> enrichWithTracking(List<MTAdBreak> schedule, MTTrackingResponse tracking) {
        if (tracking == null) return schedule;
        List<MTAdBreak> out = new ArrayList<>(schedule);

        for (MTTrackingResponse.Avail avail : tracking.avails) {
            if (avail == null) continue;
            long availStart = resolveAvailStart(avail);
            if (availStart < 0) continue;

            MTAdBreak match = findMatchForAvail(out, avail, availStart);
            if (match != null) {
                enrich(match, avail);
            } else {
                out.add(fromAvail(avail, availStart));
            }
        }
        sortByStart(out);
        return out;
    }

    private static MTAdBreak findMatch(List<MTAdBreak> list, MTAdBreak candidate) {
        // Prefer the stable (availId|availProgramDateTime) key when the
        // candidate carries one — otherwise a live sliding window that has
        // rotated by more than AD_TIMING_TOLERANCE_MS between polls looks
        // like a distinct new avail under pure time matching.
        String key = candidate.identityKey();
        if (key != null) {
            for (MTAdBreak b : list) {
                if (key.equals(b.identityKey())) return b;
            }
        }
        return findByStart(list, candidate.startTimeMs);
    }

    private static MTAdBreak findMatchForAvail(List<MTAdBreak> list,
                                                MTTrackingResponse.Avail avail,
                                                long availStart) {
        // Tracking avails always carry availId; availProgramDateTime is
        // present on live and typically absent on VOD. When both are present
        // we can identity-match against an already-enriched break in the
        // schedule and skip the time-proximity path entirely.
        if (avail.availId != null && !avail.availId.isEmpty()
                && avail.availProgramDateTime != null && !avail.availProgramDateTime.isEmpty()) {
            String key = avail.availId + "|" + avail.availProgramDateTime;
            for (MTAdBreak b : list) {
                if (key.equals(b.identityKey())) return b;
            }
        }
        return findByStart(list, availStart);
    }

    private static MTAdBreak findByStart(List<MTAdBreak> list, long startMs) {
        for (MTAdBreak b : list) {
            if (Math.abs(b.startTimeMs - startMs) < MTConstants.AD_TIMING_TOLERANCE_MS) {
                return b;
            }
        }
        return null;
    }

    private static void copyMetadata(MTAdBreak from, MTAdBreak into) {
        into.id = from.id;
        into.title = from.title;
        into.creativeId = from.creativeId;
        into.confirmedByTracking = from.confirmedByTracking;
        if (from.durationMs > 0) {
            into.durationMs = from.durationMs;
            into.endTimeMs = into.startTimeMs + from.durationMs;
        }
    }

    private static void enrich(MTAdBreak target, MTTrackingResponse.Avail avail) {
        if (avail.availId != null) target.id = avail.availId;
        target.confirmedByTracking = true;
        target.availProgramDateTime = avail.availProgramDateTime;
        // An empty ads array is MediaTailor's signal that the ad decision
        // server had nothing to serve for this slot — content will play
        // through the avail. Mark it now so the state machine knows to fire
        // AD_ERROR(NO_FILL) instead of pretending an ad ran.
        target.isNoFill = avail.ads.isEmpty();
        if (avail.durationMs > 0) {
            target.durationMs = avail.durationMs;
            target.endTimeMs = target.startTimeMs + avail.durationMs;
        }
        if (!avail.ads.isEmpty()) {
            MTTrackingResponse.Ad first = avail.ads.get(0);
            if (first.adTitle != null) target.title = first.adTitle;
            target.adId = first.adId;
            target.creativeId = first.creativeId;
            target.adSystem = first.adSystem;
            target.creativeSequence = first.creativeSequence;
            target.vastAdId = first.vastAdId;
            target.skipOffset = first.skipOffset;
        }

        if (!target.pods.isEmpty() && target.pods.size() == avail.ads.size()) {
            for (int i = 0; i < target.pods.size(); i++) {
                copyAdToPod(avail.ads.get(i), target.pods.get(i));
            }
        } else {
            target.pods.clear();
            for (MTTrackingResponse.Ad ad : avail.ads) {
                MTAdPod pod = new MTAdPod(ad.startTimeMs, ad.durationMs);
                copyAdToPod(ad, pod);
                target.pods.add(pod);
            }
        }
    }

    private static void copyAdToPod(MTTrackingResponse.Ad ad, MTAdPod pod) {
        pod.title = ad.adTitle;
        pod.adId = ad.adId;
        pod.creativeId = ad.creativeId;
        pod.adSystem = ad.adSystem;
        pod.creativeSequence = ad.creativeSequence;
        pod.vastAdId = ad.vastAdId;
        pod.skipOffset = ad.skipOffset;
        pod.adProgramDateTime = ad.adProgramDateTime;
        pod.isBumper = ad.isBumper;
        if (ad.durationMs > 0) {
            pod.durationMs = ad.durationMs;
            pod.endTimeMs = pod.startTimeMs + ad.durationMs;
        }
    }

    private static MTAdBreak fromAvail(MTTrackingResponse.Avail avail, long startMs) {
        String id = avail.availId != null ? avail.availId : ("avail-" + startMs);
        long durationMs = avail.durationMs > 0 ? avail.durationMs : sumAdDurations(avail);
        MTAdBreak b = new MTAdBreak(id, startMs, durationMs);
        b.confirmedByTracking = true;
        b.availProgramDateTime = avail.availProgramDateTime;
        // An avail with no ads is a no-fill on the tracking side. The break
        // still needs to exist so downstream can fire AD_BREAK_START and the
        // AD_ERROR(NO_FILL), but its "no ad rendered" nature must survive
        // into the schedule.
        b.isNoFill = avail.ads.isEmpty();
        if (!avail.ads.isEmpty()) {
            MTTrackingResponse.Ad first = avail.ads.get(0);
            b.title = first.adTitle;
            b.adId = first.adId;
            b.creativeId = first.creativeId;
            b.adSystem = first.adSystem;
            b.creativeSequence = first.creativeSequence;
            b.vastAdId = first.vastAdId;
            b.skipOffset = first.skipOffset;
            for (MTTrackingResponse.Ad ad : avail.ads) {
                MTAdPod pod = new MTAdPod(ad.startTimeMs, ad.durationMs);
                copyAdToPod(ad, pod);
                b.pods.add(pod);
            }
        }
        return b;
    }

    private static long resolveAvailStart(MTTrackingResponse.Avail avail) {
        if (avail.startTimeMs > 0) return avail.startTimeMs;
        if (!avail.ads.isEmpty()) return avail.ads.get(0).startTimeMs;
        return -1L;
    }

    private static long sumAdDurations(MTTrackingResponse.Avail avail) {
        long total = 0L;
        for (MTTrackingResponse.Ad ad : avail.ads) total += Math.max(ad.durationMs, 0L);
        return total;
    }

    private static void sortByStart(List<MTAdBreak> list) {
        Collections.sort(list, new Comparator<MTAdBreak>() {
            @Override
            public int compare(MTAdBreak a, MTAdBreak b) {
                return Long.compare(a.startTimeMs, b.startTimeMs);
            }
        });
    }
}
