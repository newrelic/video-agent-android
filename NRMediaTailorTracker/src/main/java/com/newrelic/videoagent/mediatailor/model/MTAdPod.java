package com.newrelic.videoagent.mediatailor.model;

/**
 * A single ad creative inside an {@link MTAdBreak}. Roughly equivalent to a
 * VAST {@code <Ad>} / {@code <Creative>} pair. Multiple pods play sequentially
 * within one break.
 *
 * <p>Timing fields are absolute-to-the-content-timeline milliseconds. The
 * quartile flags ({@code hasFiredQ1/Q2/Q3}) are set by the tracker's poll loop
 * to ensure each quartile event fires at most once.</p>
 */
public class MTAdPod {

    public long startTimeMs;
    public long durationMs;
    public long endTimeMs;

    public String title;
    public String availId;            // parent avail; used to disambiguate adId across avails
    public String adId;               // VAST <Ad id="…"> — exposed as adId in NR events
    public String creativeId;         // VAST <Creative id="…"> — exposed as adCreativeId
    public String adSystem;           // ad server name
    public String creativeSequence;   // 1-based index within pod
    public String vastAdId;
    public String skipOffset;         // HH:MM:SS, null if not skippable
    public String adProgramDateTime;  // wall clock
    public boolean isBumper;

    public boolean hasFiredStart;
    public boolean hasFiredQ1;
    public boolean hasFiredQ2;
    public boolean hasFiredQ3;

    public MTAdPod(long startTimeMs, long durationMs) {
        this.startTimeMs = startTimeMs;
        this.durationMs = durationMs;
        this.endTimeMs = startTimeMs + durationMs;
    }

    public boolean contains(long positionMs) {
        return positionMs >= startTimeMs && positionMs < endTimeMs;
    }

    /**
     * Stable per-creative identifier for downstream metric aggregation.
     *
     * <p>VAST distinguishes between the {@code <Ad>} envelope (a single ad
     * response from an ad server) and the {@code <Creative>} inside it (the
     * playable asset). An ad server can wrap the same creative in different
     * {@code <Ad>} IDs across sessions, or A/B-test different creatives
     * under the same {@code <Ad>} ID within a session, so {@code adId} is
     * unstable as an identity key. MediaTailor itself indexes uniquely by
     * the {@code Creative} id, and downstream queries like {@code count
     * (DISTINCT adPrimaryId)} should follow suit.</p>
     *
     * <p>When {@code creativeId} is present, it's the identity. Otherwise
     * the composite {@code availId + ":" + adId} disambiguates across
     * avails where the ad server happens to reuse the same {@code adId}.</p>
     */
    public String primaryKey() {
        if (creativeId != null && !creativeId.isEmpty()) return creativeId;
        if (adId != null && !adId.isEmpty()) {
            return (availId != null && !availId.isEmpty()) ? availId + ":" + adId : adId;
        }
        return null;
    }
}
