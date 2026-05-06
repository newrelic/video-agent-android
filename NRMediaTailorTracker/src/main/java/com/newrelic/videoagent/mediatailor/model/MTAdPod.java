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
}
