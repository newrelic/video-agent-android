package com.newrelic.videoagent.mediatailor.net;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed Java view of the JSON returned by MediaTailor's client-side tracking
 * API ({@code GET /v1/tracking/<accountId>/<config>/<sessionId>}).
 *
 * <p>Populated by {@link MTTrackingClient#parse(String)} and consumed by
 * {@link com.newrelic.videoagent.mediatailor.schedule.MTAdScheduleMerger} to
 * enrich the manifest-derived ad schedule with creative metadata and, when the
 * app opts in, VAST beacon URLs to fire client-side.</p>
 *
 * <p>Only fields the tracker uses are modelled; extensions, companion ads,
 * icons, and ad-verification payloads are ignored. Time fields are
 * normalised to milliseconds at parse time — the wire format uses seconds.</p>
 */
public class MTTrackingResponse {

    public final List<Avail> avails = new ArrayList<>();
    /** Overlay / banner / VPAID non-linear avails. */
    public final List<NonLinearAvail> nonLinearAvails = new ArrayList<>();

    public static class Avail {
        public String availId;
        public long startTimeMs;
        public long durationMs;
        public String availProgramDateTime;
        public final List<Ad> ads = new ArrayList<>();
    }

    public static class Ad {
        public String adId;             // VAST <Ad id="…">
        public String adTitle;
        public long startTimeMs;
        public long durationMs;
        public String adSystem;         // ad server name (GDFP, FreeWheel, SpringServe, Publica)
        public String creativeId;       // VAST <Creative id="…"> — different from adId
        public String creativeSequence; // 1-based index of creative within an ad pod
        public String vastAdId;         // VAST hierarchical ad identifier
        public String skipOffset;       // HH:MM:SS for skippable ads (null if not skippable)
        public String adProgramDateTime; // wall clock of ad start (Live correlation)
        public boolean isBumper;        // ad stitched in from a MediaTailor bumper configuration
    }

    /**
     * A single non-linear (overlay/banner) ad avail. Full rendering is the
     * app's responsibility; the tracker only surfaces that they exist.
     */
    public static class NonLinearAvail {
        public String availId;
        public long startTimeMs;
        public long durationMs;
        public int adCount;
    }
}
