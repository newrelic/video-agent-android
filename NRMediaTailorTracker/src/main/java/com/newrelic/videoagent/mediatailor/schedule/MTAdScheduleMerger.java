package com.newrelic.videoagent.mediatailor.schedule;

import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.mediatailor.MTAdErrorCode;
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
     * titles/creativeIds/pods; avails not present in the schedule are
     * appended. Data-integrity anomalies encountered during the merge are
     * returned via {@link MergedSchedule#pendingErrors} so the caller can
     * emit them as {@code AD_ERROR} events on the main looper — the merger
     * itself has no event-emission path.
     */
    public static MergedSchedule enrichWithTracking(List<MTAdBreak> schedule, MTTrackingResponse tracking) {
        List<MTAdBreak> out = new ArrayList<>(schedule);
        List<MTAdErrorCode> pendingErrors = new ArrayList<>();
        if (tracking == null) return new MergedSchedule(out, pendingErrors);

        for (MTTrackingResponse.Avail avail : tracking.avails) {
            if (avail == null) continue;
            long availStart = resolveAvailStart(avail, pendingErrors);
            if (availStart < 0) continue;

            MTAdBreak match = findMatchForAvail(out, avail, availStart);
            if (match != null) {
                enrich(match, avail);
            } else {
                out.add(fromAvail(avail, availStart));
            }
        }
        sortByStart(out);
        return new MergedSchedule(out, pendingErrors);
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

        if (target.pods.isEmpty()) {
            // No manifest-derived pods on this break yet — either the manifest
            // parser hasn't run for this avail, or it produced no segments.
            // Build pods from the tracking timings as the only source.
            for (MTTrackingResponse.Ad ad : avail.ads) {
                MTAdPod pod = new MTAdPod(ad.startTimeMs, ad.durationMs);
                copyAdToPod(ad, pod);
                target.pods.add(pod);
            }
        } else if (target.pods.size() == avail.ads.size()) {
            // Manifest pod count and tracking ad count agree; index-align the
            // metadata onto the existing (correctly-timed) manifest pods.
            for (int i = 0; i < target.pods.size(); i++) {
                copyAdToPod(avail.ads.get(i), target.pods.get(i));
            }
        } else {
            // Counts disagree. This happens most often when the manifest parse
            // races the tracking fetch on live — the two sides observe the
            // avail at slightly different moments and one sees an ad the
            // other doesn't. Previously the code wiped the manifest pods and
            // rebuilt from tracking, but tracking timings are rounded to
            // whole seconds while manifest pods come from actual segment /
            // discontinuity data, so quartile events ended up firing at
            // playhead positions the player never reached (or firing on the
            // wrong creative when pods shifted by one slot). Keep the manifest
            // boundaries and copy tracking metadata onto whichever pod matches
            // by startTimeMs within tolerance; leave the rest unadorned.
            target.podCountMismatch = true;
            for (MTAdPod pod : target.pods) {
                MTTrackingResponse.Ad closest = closestAdWithinTolerance(pod.startTimeMs, avail.ads);
                if (closest != null) copyAdToPod(closest, pod);
            }
        }
    }

    private static MTTrackingResponse.Ad closestAdWithinTolerance(long startMs, List<MTTrackingResponse.Ad> ads) {
        MTTrackingResponse.Ad best = null;
        long bestDelta = Long.MAX_VALUE;
        for (MTTrackingResponse.Ad ad : ads) {
            long delta = Math.abs(ad.startTimeMs - startMs);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = ad;
            }
        }
        // Refuse to paste metadata onto a pod that has no near match — a
        // "least-wrong" copy would put ad-3's title on ad-2's slot when the
        // counts differ.
        return bestDelta < MTConstants.AD_TIMING_TOLERANCE_MS ? best : null;
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

    private static long resolveAvailStart(MTTrackingResponse.Avail avail, List<MTAdErrorCode> pendingErrors) {
        if (avail.startTimeMs > 0) return avail.startTimeMs;
        if (!avail.ads.isEmpty()) {
            // startTimeInSeconds is a required field on the tracking-avail
            // schema; when it's missing or zero the MediaTailor configuration
            // is usually wrong on the operator side. Infer from the first ad
            // so the break doesn't disappear from the schedule, but surface
            // MISSING_AVAIL_START so someone knows the data is off. On mid-
            // roll avails with a leading slate fragment this inference is
            // wrong by whatever gap sits before the first ad's media, and
            // the AD_BREAK_START ends up firing late.
            NRLog.w("MT tracking avail missing startTimeInSeconds; inferring from first ad");
            pendingErrors.add(MTAdErrorCode.MISSING_AVAIL_START);
            return avail.ads.get(0).startTimeMs;
        }
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
