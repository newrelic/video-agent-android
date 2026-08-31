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
        return enrichWithTracking(schedule, tracking, -1L);
    }

    /**
     * Variant that additionally takes the current playhead so the growth branch
     * can flag a pod appended after the playhead already left its window — the
     * append is real data but the engagement is unrecoverable, and marking it
     * keeps the under-count visible.
     */
    public static MergedSchedule enrichWithTracking(List<MTAdBreak> schedule, MTTrackingResponse tracking,
                                                     long playheadMs) {
        List<MTAdBreak> out = new ArrayList<>(schedule);
        List<MTAdErrorCode> pendingErrors = new ArrayList<>();
        if (tracking == null) return new MergedSchedule(out, pendingErrors);

        for (MTTrackingResponse.Avail avail : tracking.avails) {
            if (avail == null) continue;
            boolean startWasMissing = avail.startTimeMs <= 0 && !avail.ads.isEmpty();
            long availStart = resolveAvailStart(avail);
            if (availStart < 0) continue;

            MTAdBreak match = findMatchForAvail(out, avail, availStart);
            MTAdBreak target;
            if (match != null) {
                enrich(match, avail, playheadMs, pendingErrors);
                target = match;
            } else {
                target = fromAvail(avail, availStart);
                out.add(target);
            }
            // A misconfigured avail keeps reporting no startTimeInSeconds on
            // every poll for its whole lifetime — without this gate (mirroring
            // hasFiredNoFillError below) MISSING_AVAIL_START would re-fire as
            // an AD_ERROR every poll cycle instead of once per avail.
            if (startWasMissing && !target.hasFiredMissingStartError) {
                NRLog.w("MT tracking avail missing startTimeInSeconds; inferring from first ad");
                pendingErrors.add(MTAdErrorCode.MISSING_AVAIL_START);
                target.hasFiredMissingStartError = true;
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
        // identityKey() is null for every fresh manifest parse — availId and
        // availProgramDateTime only get attached once tracking enriches a
        // break, which hasn't happened yet on this candidate's first pass
        // through here. That makes this the actual matching path a live HLS
        // window rotation goes through, so fall back to id equality before
        // giving up to a tight time-tolerance match: MTHlsParser now derives
        // a break's id from the playlist's absolute discontinuity sequence,
        // which — unlike startTimeMs — stays the same for the same avail
        // across a window slide.
        if (candidate.id != null) {
            for (MTAdBreak b : list) {
                if (!b.hasFiredEnd && candidate.id.equals(b.id)) return b;
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
            // A break that already fired AD_BREAK_END is done. Without
            // availId+availProgramDateTime to identity-match on (live streams
            // can report either as null), a later, genuinely different avail
            // can land within the ±tolerance window of an old break's
            // startTimeMs and get merged straight into it — reopening a
            // "zombie" break the tracker already told the app had ended, with
            // no new AD_BREAK_START to explain the sudden new AD_START.
            // Once closed, a break is never a valid merge target again.
            if (b.hasFiredEnd) continue;
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

    /**
     * Stamps the ad-attribute fields both {@link #enrich} and {@link
     * #fromAvail} take from an avail's first ad — same fields, same order —
     * so a future field addition only has to happen once instead of drifting
     * between a break that existed before tracking data arrived and one
     * created by it.
     */
    private static void stampFirstAdMetadata(MTAdBreak target, MTTrackingResponse.Ad first) {
        if (first.adTitle != null) target.title = first.adTitle;
        target.adId = first.adId;
        target.creativeId = first.creativeId;
        target.adSystem = first.adSystem;
        target.creativeSequence = first.creativeSequence;
        target.vastAdId = first.vastAdId;
        target.skipOffset = first.skipOffset;
    }

    private static void enrich(MTAdBreak target, MTTrackingResponse.Avail avail,
                               long playheadMs, List<MTAdErrorCode> pendingErrors) {
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
            stampFirstAdMetadata(target, avail.ads.get(0));
        }

        if (target.pods.isEmpty()) {
            // No manifest-derived pods on this break yet — either the manifest
            // parser hasn't run for this avail, or it produced no segments.
            // Build pods from the tracking timings as the only source.
            for (MTTrackingResponse.Ad ad : avail.ads) {
                MTAdPod pod = new MTAdPod(ad.startTimeMs, ad.durationMs);
                copyAdToPod(ad, pod, avail.availId);
                target.pods.add(pod);
            }
            target.podsFromTracking = true;
        } else if (target.podsFromTracking && avail.ads.size() > target.pods.size()) {
            // The avail grew: MediaTailor reported more ads this poll than the
            // last one, and the existing pods were themselves tracking-built.
            // Update the pods we already have (preserving their fired flags) and
            // append the newcomers keyed by adId so findActivePod picks them up
            // on the next tick. Ads #2/#3 of a pod that first arrived as one ad
            // would otherwise never fire AD_START / quartiles / AD_END.
            for (MTTrackingResponse.Ad ad : avail.ads) {
                MTAdPod existing = findMatchingPod(target.pods, ad);
                if (existing != null) {
                    copyAdToPod(ad, existing, avail.availId);
                } else {
                    MTAdPod pod = new MTAdPod(ad.startTimeMs, ad.durationMs);
                    copyAdToPod(ad, pod, avail.availId);
                    // The append is real, but if the playhead is already past
                    // this pod's window the poll loop can never select it.
                    // Flag it rather than fabricate a retroactive impression.
                    if (playheadMs >= 0 && pod.endTimeMs <= playheadMs) {
                        pod.missedByLateAppend = true;
                        pendingErrors.add(MTAdErrorCode.POD_MISSED_LATE_APPEND);
                        NRLog.w(MTConstants.LOG_TAG + " pod appended past playhead ("
                                + pod.startTimeMs + "-" + pod.endTimeMs + "ms, playhead="
                                + playheadMs + "ms) — counted as missed, no AD_* emitted");
                    }
                    target.pods.add(pod);
                }
            }
        } else if (target.pods.size() == avail.ads.size()) {
            // Manifest pod count and tracking ad count agree, but MediaTailor's
            // avail.ads[] order isn't guaranteed to stay index-aligned with
            // target.pods across polls. A blind positional copy would smear ad
            // B's duration onto pod A's still-correct startTimeMs (copyAdToPod
            // never touches startTimeMs), corrupting endTimeMs and making
            // findActivePod flap the pod in and out of its own active window
            // every poll — spurious AD_END/AD_START pairs on an otherwise
            // unremarkable break. Match by identity first; index is only a
            // last-resort fallback for the (adId-less, ambiguous-time) case.
            for (int i = 0; i < avail.ads.size(); i++) {
                MTTrackingResponse.Ad ad = avail.ads.get(i);
                MTAdPod pod = findMatchingPod(target.pods, ad);
                copyAdToPod(ad, pod != null ? pod : target.pods.get(i), avail.availId);
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
                if (pod.adId != null) {
                    // This pod's identity is already known from an earlier
                    // poll. avail.ads.size() can flap between two values
                    // across polls (e.g. a still-stabilizing live avail
                    // reporting 1 ad on one poll, 2 on the next, back to 1),
                    // which routes through this exact branch whenever the
                    // count drops. Only touch the pod if THIS poll reconfirms
                    // the SAME ad — falling back to closest-time when its ad
                    // simply wasn't reported this cycle would risk smearing a
                    // different ad's metadata onto it, flipping which pod
                    // findActivePod treats as active.
                    MTTrackingResponse.Ad match = findAdById(avail.ads, pod.adId);
                    if (match != null) copyAdToPod(match, pod, avail.availId);
                } else {
                    // Never-identified pod (manifest-derived, no adId
                    // concept) — time proximity is the only signal available.
                    MTTrackingResponse.Ad closest = closestAdWithinTolerance(pod.startTimeMs, avail.ads);
                    if (closest != null) copyAdToPod(closest, pod, avail.availId);
                }
            }
        }

        // avail.durationMs describes the reserved slot, which for a still-open
        // live avail MediaTailor can keep reporting as generously larger than
        // the ads actually decisioned to fill it — trusting it verbatim above
        // means the break's window never shrinks back down once the real ads
        // are done, so it can never naturally go inactive and AD_BREAK_END
        // never fires. Once tracking-built pods exist, their own (individually
        // stable, confirmed) end times are the more accurate signal: clamp the
        // break to actually end where its last known pod ends.
        if (target.podsFromTracking) clampEndToPods(target);
    }

    /**
     * Overwrites {@code target.endTimeMs}/{@code durationMs} to end where the
     * break's own last pod ends, when it has pods. A no-op for a no-fill break
     * (no pods to derive from), which keeps trusting whatever duration it was
     * given.
     */
    private static void clampEndToPods(MTAdBreak target) {
        if (target.pods.isEmpty()) return;
        long maxPodEnd = target.startTimeMs;
        for (MTAdPod pod : target.pods) {
            if (pod.endTimeMs > maxPodEnd) maxPodEnd = pod.endTimeMs;
        }
        if (maxPodEnd > target.startTimeMs) {
            target.endTimeMs = maxPodEnd;
            target.durationMs = maxPodEnd - target.startTimeMs;
        }
    }

    /**
     * Locate the existing pod an incoming tracking ad corresponds to. adId is
     * the stable identity when MediaTailor supplies one. A VAST ad with no
     * {@code <Ad id>} reports a null adId; those fall back to matching by start
     * time, which is stable across polls for tracking-built pods. Without the
     * fallback a null-adId ad matches nothing and gets re-appended as a
     * duplicate pod on every growth poll, inflating impressions.
     */
    private static MTAdPod findMatchingPod(List<MTAdPod> pods, MTTrackingResponse.Ad ad) {
        if (ad.adId != null) {
            for (MTAdPod p : pods) {
                if (ad.adId.equals(p.adId)) return p;
            }
            return null;
        }
        for (MTAdPod p : pods) {
            if (p.adId == null && p.startTimeMs == ad.startTimeMs) return p;
        }
        return null;
    }

    private static MTTrackingResponse.Ad findAdById(List<MTTrackingResponse.Ad> ads, String adId) {
        for (MTTrackingResponse.Ad ad : ads) {
            if (adId.equals(ad.adId)) return ad;
        }
        return null;
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

    private static void copyAdToPod(MTTrackingResponse.Ad ad, MTAdPod pod, String availId) {
        pod.title = ad.adTitle;
        pod.availId = availId;
        pod.adId = ad.adId;
        pod.creativeId = ad.creativeId;
        pod.adSystem = ad.adSystem;
        pod.creativeSequence = ad.creativeSequence;
        pod.vastAdId = ad.vastAdId;
        pod.skipOffset = ad.skipOffset;
        pod.adProgramDateTime = ad.adProgramDateTime;
        pod.isBumper = ad.isBumper;
        pod.trackingEvents.clear();
        pod.trackingEvents.addAll(ad.trackingEvents);
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
            stampFirstAdMetadata(b, avail.ads.get(0));
            for (MTTrackingResponse.Ad ad : avail.ads) {
                MTAdPod pod = new MTAdPod(ad.startTimeMs, ad.durationMs);
                copyAdToPod(ad, pod, avail.availId);
                b.pods.add(pod);
            }
            b.podsFromTracking = true;
            clampEndToPods(b);
        }
        return b;
    }

    private static long resolveAvailStart(MTTrackingResponse.Avail avail) {
        if (avail.startTimeMs > 0) return avail.startTimeMs;
        if (!avail.ads.isEmpty()) {
            // startTimeInSeconds is a required field on the tracking-avail
            // schema; when it's missing the MediaTailor configuration is
            // usually wrong on the operator side. Infer from the first ad so
            // the break doesn't disappear from the schedule — the caller
            // reports MISSING_AVAIL_START once per avail so someone knows the
            // data is off. On mid-roll avails with a leading slate fragment
            // this inference is wrong by whatever gap sits before the first
            // ad's media, and the AD_BREAK_START ends up firing late.
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
