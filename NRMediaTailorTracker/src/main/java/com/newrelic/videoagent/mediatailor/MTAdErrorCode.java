package com.newrelic.videoagent.mediatailor;

/**
 * Semantic error codes emitted on {@code AD_ERROR} events by the MediaTailor
 * tracker. The {@link #name()} of each entry is written verbatim to the
 * {@code errorCode} attribute so downstream NRDB queries can filter without
 * having to memorise numeric mappings.
 */
public enum MTAdErrorCode {

    /**
     * The tracking response returned an avail with an empty {@code ads}
     * array. In MediaTailor this is a normal outcome — the ad decision
     * server had nothing to serve for this slot and the underlying content
     * plays through the avail (or the operator's slate fills it on live).
     * The tracker still needs to signal that no ad rendered, otherwise
     * downstream funnel metrics show a break with zero impressions and no
     * explanation.
     */
    NO_FILL("Empty avail returned by MediaTailor ADS"),

    /**
     * A tracking fetch timed out at the socket level — the ad decision
     * server didn't respond in time. Distinguished from generic fetch
     * failures because it maps directly to the ADS-timeout scenario in
     * MediaTailor's failure mode taxonomy.
     */
    ADS_TIMEOUT("Ad decision server timed out"),

    /**
     * The tracking fetch failed for a non-timeout reason after the retry
     * budget was exhausted (network error, DNS, non-2xx HTTP other than
     * 400). The session continues with manifest-only detection (no titles,
     * no creative IDs) so this event is the only signal that anything
     * degraded.
     */
    TRACKING_FETCH_FAILED("Tracking API fetch failed after retries"),

    /**
     * The server rejected the retained {@code NextToken} with HTTP 400 and
     * the reset-and-retry path also failed. MediaTailor tokens live for
     * ~24h; on a session that outlives that window, this is what the user
     * sees after the pre-emptive-reset clock has already ticked past.
     */
    TOKEN_EXPIRED("MediaTailor NextToken expired"),

    /**
     * A tracking avail arrived without a {@code startTimeInSeconds} field.
     * The schema requires it; the tracker infers the start from the first
     * ad's timing to avoid dropping the break on the floor, but the fact
     * that inference was necessary usually means the MediaTailor
     * configuration is wrong on the operator side and someone should look.
     */
    MISSING_AVAIL_START("Avail missing startTimeInSeconds; inferred from first ad"),

    /**
     * Media3 returned a manifest object that couldn't be walked into an
     * ad schedule. Usually means the manifest bytes are malformed; the
     * tracker degrades to whatever prior schedule it had.
     */
    MANIFEST_PARSE_FAILED("Manifest parse failed"),

    /**
     * The manifest and the tracking response disagree about whether an
     * avail exists at a given position — one side sees an ad break the
     * other side has no record of. Reserved for the reconciliation code
     * paths that surface this asymmetry rather than silently favouring
     * one source.
     */
    MANIFEST_TRACKING_MISMATCH("Manifest and tracking response disagree");

    private final String defaultMessage;

    MTAdErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
