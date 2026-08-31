package com.newrelic.videoagent.mediatailor.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.newrelic.videoagent.mediatailor.model.MTAdBreak;
import com.newrelic.videoagent.mediatailor.model.MTAdPod;
import com.newrelic.videoagent.mediatailor.net.MTTrackingResponse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression coverage for two bugs found via a live MediaTailor session where
 * ads.ads[] arrived with an unstable order and the avail's own
 * durationInSeconds stayed inflated well past when the real ads finished:
 * repeated AD_START/AD_END pairs on the same two ads, and AD_BREAK_END never
 * firing at all.
 */
public class MTAdScheduleMergerTest {

    private static MTTrackingResponse.Ad ad(String id, long startMs, long durationMs) {
        MTTrackingResponse.Ad a = new MTTrackingResponse.Ad();
        a.adId = id;
        a.startTimeMs = startMs;
        a.durationMs = durationMs;
        return a;
    }

    private static MTTrackingResponse.Avail avail(String availId, long startMs, long durationMs,
                                                    MTTrackingResponse.Ad... ads) {
        MTTrackingResponse.Avail av = new MTTrackingResponse.Avail();
        av.availId = availId;
        av.startTimeMs = startMs;
        av.durationMs = durationMs;
        for (MTTrackingResponse.Ad a : ads) av.ads.add(a);
        return av;
    }

    private static MTTrackingResponse response(MTTrackingResponse.Avail... avails) {
        MTTrackingResponse r = new MTTrackingResponse();
        for (MTTrackingResponse.Avail a : avails) r.avails.add(a);
        return r;
    }

    private static MTAdPod podFor(MTAdBreak br, String adId) {
        for (MTAdPod p : br.pods) {
            if (adId.equals(p.adId)) return p;
        }
        throw new AssertionError("no pod found with adId=" + adId);
    }

    /**
     * Reproduces the observed live bug: on poll 1, avail.ads = [A, B]; on poll
     * 2, MediaTailor reports the same two ads but in flipped order [B, A].
     * Before the fix, the "counts agree" branch blindly copied by index,
     * smearing B's duration onto A's pod (whose startTimeMs never moves) and
     * vice versa — corrupting endTimeMs so findActivePod would flap the pod
     * in and out of its own window every poll.
     */
    @Test
    public void availAdsOrderFlipDoesNotCorruptPodIdentityOrTiming() {
        MTTrackingResponse.Ad adA = ad("A", 1000L, 25659L);
        MTTrackingResponse.Ad adB = ad("B", 26659L, 14214L);

        MergedSchedule first = MTAdScheduleMerger.enrichWithTracking(
                new ArrayList<MTAdBreak>(), response(avail("avail-1", 1000L, 81581L, adA, adB)));
        assertEquals(1, first.breaks.size());
        MTAdBreak br = first.breaks.get(0);
        assertEquals(2, br.pods.size());

        MTAdPod podABefore = podFor(br, "A");
        MTAdPod podBBefore = podFor(br, "B");
        long startA = podABefore.startTimeMs;
        long startB = podBBefore.startTimeMs;

        // Poll 2: same two ads, order flipped.
        MTTrackingResponse.Ad adAAgain = ad("A", 1000L, 25659L);
        MTTrackingResponse.Ad adBAgain = ad("B", 26659L, 14214L);
        List<MTAdBreak> existing = new ArrayList<>();
        existing.add(br);
        MergedSchedule second = MTAdScheduleMerger.enrichWithTracking(
                existing, response(avail("avail-1", 1000L, 81581L, adBAgain, adAAgain)));

        assertEquals(1, second.breaks.size());
        MTAdBreak brAfter = second.breaks.get(0);
        MTAdPod podAAfter = podFor(brAfter, "A");
        MTAdPod podBAfter = podFor(brAfter, "B");

        // Object identity preserved — no duplicate/replacement pods.
        assertTrue("pod A should be the same object across polls", podAAfter == podABefore);
        assertTrue("pod B should be the same object across polls", podBAfter == podBBefore);

        // Each pod keeps its own timing; nothing got smeared from the other ad.
        assertEquals(startA, podAAfter.startTimeMs);
        assertEquals(25659L, podAAfter.durationMs);
        assertEquals(startA + 25659L, podAAfter.endTimeMs);

        assertEquals(startB, podBAfter.startTimeMs);
        assertEquals(14214L, podBAfter.durationMs);
        assertEquals(startB + 14214L, podBAfter.endTimeMs);
    }

    /**
     * Reproduces the observed live bug: the avail's own durationInSeconds
     * (81581ms) stays far larger than the two real ads that actually filled
     * it (25659ms + 14214ms = 39873ms total). Before the fix, the break's
     * endTimeMs was overwritten from avail.durationMs on every poll, so the
     * break's active window never shrank back down and AD_BREAK_END could
     * never fire even minutes after the real ads finished.
     */
    @Test
    public void breakEndDerivesFromPodsNotInflatedAvailDuration() {
        MTTrackingResponse.Ad adA = ad("A", 1000L, 25659L);
        MTTrackingResponse.Ad adB = ad("B", 26659L, 14214L);

        MergedSchedule result = MTAdScheduleMerger.enrichWithTracking(
                new ArrayList<MTAdBreak>(), response(avail("avail-1", 1000L, 81581L, adA, adB)));

        assertEquals(1, result.breaks.size());
        MTAdBreak br = result.breaks.get(0);

        long expectedEnd = 26659L + 14214L; // adB's endTimeMs, the later of the two pods
        assertEquals("break should end where its last real pod ends, not at the inflated avail duration",
                expectedEnd, br.endTimeMs);
        assertTrue("break should NOT still be active well past the real ads' end",
                !br.contains(expectedEnd + 5_000L));
    }

    /**
     * Reproduces the observed live bug: a break fires AD_BREAK_END (hasFiredEnd
     * = true), then a later, genuinely different avail — with no
     * availId+availProgramDateTime to identity-match on, as live streams often
     * don't set the latter — happens to land within the ±500ms time-tolerance
     * of the OLD break's startTimeMs. Before the fix, this merged straight
     * into the closed break, silently reopening it: a bare AD_START would fire
     * with no preceding AD_BREAK_START, and the break's stale startTimeMs left
     * getPlayhead() frozen while it never legitimately exited again.
     */
    @Test
    public void closedBreakIsNeverReusedAsAMatchTargetForANewAvail() {
        MTTrackingResponse.Ad adA = ad("A", 1000L, 25659L);
        MergedSchedule first = MTAdScheduleMerger.enrichWithTracking(
                new ArrayList<MTAdBreak>(), response(avail("avail-1", 1000L, 25659L, adA)));
        assertEquals(1, first.breaks.size());
        MTAdBreak closed = first.breaks.get(0);
        closed.hasFiredEnd = true; // simulates AD_BREAK_END having already fired

        // A later avail nobody would call "the same avail" — new availId, new
        // ad — but its startTimeMs happens to fall within tolerance of the
        // old (closed) break's startTimeMs.
        MTTrackingResponse.Ad adC = ad("C", 1200L, 18000L);
        List<MTAdBreak> existing = new ArrayList<>();
        existing.add(closed);
        MergedSchedule second = MTAdScheduleMerger.enrichWithTracking(
                existing, response(avail("avail-2", 1200L, 18000L, adC)));

        assertEquals("the new avail must become its own break, not reopen the closed one",
                2, second.breaks.size());
        for (MTAdBreak br : second.breaks) {
            if (br == closed) {
                assertTrue("the closed break must be untouched", br.hasFiredEnd);
                assertEquals(1, br.pods.size());
                assertEquals("A", br.pods.get(0).adId);
            } else {
                assertTrue("the new break must start unfired", !br.hasFiredEnd);
                assertEquals(1, br.pods.size());
                assertEquals("C", br.pods.get(0).adId);
            }
        }
    }

    /**
     * Reproduces the observed live bug: avail.ads.size() flaps between 1 and 2
     * across polls for a still-stabilizing live avail (2 pods already exist
     * from an earlier poll, but this poll's avail reports only 1 ad), routing
     * into the "counts disagree" branch. Before the fix, that branch matched
     * purely by time proximity, with no adId check — B's metadata got pasted
     * onto pod A whenever B's rounded-to-the-second startTimeMs happened to
     * land closer to pod A's startTimeMs than pod B's own. That corrupted pod
     * A's endTimeMs, making findActivePod flap between the two pods every
     * poll — the exact AD_START/AD_END repetition seen live.
     */
    @Test
    public void countMismatchNeverSmearsAKnownAdOntoTheWrongPod() {
        MTTrackingResponse.Ad adA = ad("A", 1000L, 25659L);
        MTTrackingResponse.Ad adB = ad("B", 26659L, 14214L);
        MergedSchedule first = MTAdScheduleMerger.enrichWithTracking(
                new ArrayList<MTAdBreak>(), response(avail("avail-1", 1000L, 40873L, adA, adB)));
        MTAdBreak br = first.breaks.get(0);
        MTAdPod podA = podFor(br, "A");
        MTAdPod podB = podFor(br, "B");
        long podAEndBefore = podA.endTimeMs;
        long podBEndBefore = podB.endTimeMs;

        // Poll 2: avail shrinks to reporting only "B" this cycle, and its
        // rounded startTimeMs (1100ms) happens to land closer to pod A's
        // startTimeMs (1000ms, delta=100ms) than to pod B's own (26659ms).
        MTTrackingResponse.Ad adBAgainButCloserToA = ad("B", 1100L, 14214L);
        List<MTAdBreak> existing = new ArrayList<>();
        existing.add(br);
        MergedSchedule second = MTAdScheduleMerger.enrichWithTracking(
                existing, response(avail("avail-1", 1000L, 40873L, adBAgainButCloserToA)));

        MTAdBreak brAfter = second.breaks.get(0);
        MTAdPod podAAfter = podFor(brAfter, "A");
        MTAdPod podBAfter = podFor(brAfter, "B");

        assertEquals("pod A must be untouched — its own ad wasn't in this poll's avail",
                podAEndBefore, podAAfter.endTimeMs);
        assertEquals("pod B is legitimately reconfirmed, even off-schedule on time",
                podBEndBefore, podBAfter.endTimeMs);
    }
}
