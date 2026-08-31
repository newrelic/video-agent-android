package com.newrelic.videoagent.mediatailor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single VAST beacon entry parsed from a tracking response's
 * {@code trackingEvents[]} array.
 *
 * <p>The tracker itself does not POST to {@link #beaconUrls} — per
 * MediaTailor's contract, client-side beacon firing is the player's
 * responsibility. Beacons are surfaced on the model so integrators who need
 * to route them through their own attribution pipeline can pull them off a
 * pod without reaching into the network layer.</p>
 */
public class MTTrackingEvent {

    /** {@code impression}, {@code firstQuartile}, {@code midpoint},
     *  {@code thirdQuartile}, {@code complete}, and so on. */
    public String eventType;

    public final List<String> beaconUrls = new ArrayList<>();

    /**
     * Offset from the ad's own start, in milliseconds. The wire's
     * {@code startTimeInSeconds} field means different things at different
     * nesting levels — at the avail / ad level it's relative to the playback
     * session, and inside a {@code trackingEvent} it's relative to the ad's
     * own start. Combine with {@link MTAdPod#startTimeMs} for an absolute
     * timeline position; using the raw wire value as an absolute would fire
     * beacons at wildly wrong moments on any ad that isn't a pre-roll.
     */
    public long relativeToAdStartMs;
}
