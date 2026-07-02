package com.newrelic.videoagent.mediatailor;

/**
 * Semantic error codes emitted on {@code AD_ERROR} events by the MediaTailor
 * tracker. The {@link #name()} of each entry is written verbatim to the
 * {@code errorCode} attribute so downstream NRDB queries can filter without
 * having to memorise numeric mappings.
 *
 * <p>Currently only {@link #NO_FILL} is wired; additional codes for network
 * failures, expired pagination tokens, and data-integrity warnings are added
 * as their emission paths are built out.</p>
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
    NO_FILL("Empty avail returned by MediaTailor ADS");

    private final String defaultMessage;

    MTAdErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
