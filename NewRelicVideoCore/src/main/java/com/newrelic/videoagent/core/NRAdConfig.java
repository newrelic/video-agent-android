package com.newrelic.videoagent.core;

import androidx.annotation.Nullable;

/**
 * Ad configuration for a single player session — the one place for all ad options.
 *
 * <p>Construct via static factory methods and pass to
 * {@link NRVideoPlayerConfiguration}. Pass {@code null} for no ad tracking.</p>
 *
 * <pre>
 *   // Google IMA / any CSAI framework
 *   NRAdConfig.csai()
 *
 *   // AWS MediaTailor — standard AWS hostnames
 *   NRAdConfig.mediaTailor()
 *
 *   // AWS MediaTailor — custom CDN (AWS-recommended /tm/ path is auto-detected;
 *   // only set adSegmentPrefix if your CDN uses a different path)
 *   NRAdConfig.mediaTailor("/my-ads/")
 *
 *   // AWS MediaTailor — explicit tracking URL (POST /v1/session/ flow,
 *   // or when CDN strips aws.sessionId from the manifest URL)
 *   NRAdConfig.mediaTailor(null, "https://...mediatailor.../v1/tracking/...")
 *
 *   // AWS MediaTailor — both overrides
 *   NRAdConfig.mediaTailor("/my-ads/", "https://...mediatailor.../v1/tracking/...")
 * </pre>
 *
 * <h3>Ad segment detection order (MediaTailor only)</h3>
 * <ol>
 *   <li>{@code segments.mediatailor} — default AWS ad-segment hostname.</li>
 *   <li>{@code /v1/hlssegment/} / {@code /v1/dashsegment/} — MediaTailor CDN
 *       rewrite paths.</li>
 *   <li>{@code /tm/} — AWS-recommended CDN ad-segment prefix; detected
 *       automatically for all customers, no configuration needed.</li>
 *   <li>{@link #adSegmentPrefix} — custom override; only checked when non-null.
 *       Use only if your CDN prefix differs from {@code /tm/}.</li>
 * </ol>
 */
public final class NRAdConfig {

    /**
     * Ad framework type — identifies which tracker is created by
     * {@link NRVideo#addPlayer}. Set internally by the factory methods;
     * read-only for callers.
     */
    public enum Type {
        /** Client-side ad insertion — Google IMA, Freewheel, generic CSAI. */
        CSAI,
        /** Server-side ad insertion — AWS Elemental MediaTailor. */
        SSAI_MT
    }

    /** Internal tracker type — set by the factory method, not by the customer. */
    public final Type type;

    /**
     * Custom CDN ad-segment path prefix override. {@code null} = no override.
     *
     * <p>Set only when your CDN rewrites ad-segment URLs to a path that is
     * not {@code segments.mediatailor} and not {@code /tm/} (both are detected
     * automatically). Example: {@code "/ads/"} for
     * {@code https://cdn.example.com/ads/segment_001.ts}.</p>
     *
     * <p>This is the canonical name, matching the VideoJS
     * {@code mtOptions.adSegmentPrefix} option and the cross-platform spec.</p>
     */
    @Nullable
    public final String adSegmentPrefix;

    /**
     * @deprecated Renamed to {@link #adSegmentPrefix} for cross-platform
     * naming parity. Holds the same value; will be removed in a future
     * release. Read {@link #adSegmentPrefix} instead.
     */
    @Deprecated
    @Nullable
    public final String segmentPrefix;

    /**
     * Explicit MediaTailor tracking URL. {@code null} = auto-derived from
     * the manifest URI (covers the common implicit-session case).
     *
     * <p>Supply this when using the POST {@code /v1/session/} flow (the
     * tracking URL is in the response body), or when your CDN strips
     * {@code aws.sessionId} from the manifest URL so auto-derivation fails.
     * You can also set this after {@code addPlayer()} via
     * {@code NRTrackerMediaTailor.setTrackingUrl()} for async session flows.</p>
     */
    @Nullable
    public final String trackingUrl;

    // ── Static factory methods ─────────────────────────────────────────────

    /**
     * Client-side ad insertion.
     *
     * <p>Covers Google IMA, Brightcove IMA, Freewheel, and any other CSAI
     * framework — they all share the same activation path and are
     * auto-detected by the tracker.</p>
     */
    public static NRAdConfig csai() {
        return new NRAdConfig(Type.CSAI, null, null);
    }

    /**
     * AWS Elemental MediaTailor SSAI — standard AWS domain detection.
     *
     * <p>Segments at {@code *.mediatailor.*} and {@code /tm/} paths are
     * detected automatically. Use this for the common case.</p>
     */
    public static NRAdConfig mediaTailor() {
        return new NRAdConfig(Type.SSAI_MT, null, null);
    }

    /**
     * AWS Elemental MediaTailor SSAI — with custom CDN segment prefix override.
     *
     * <p>Use this only when your CDN ad-segment path is not the default
     * {@code segments.mediatailor} hostname and not the AWS-recommended
     * {@code /tm/} path (which is checked automatically).</p>
     *
     * @param adSegmentPrefix Substring matched against each segment URL to
     *                        classify it as an ad segment. Pass {@code null}
     *                        to rely on default detection only.
     */
    public static NRAdConfig mediaTailor(@Nullable String adSegmentPrefix) {
        return new NRAdConfig(Type.SSAI_MT, adSegmentPrefix, null);
    }

    /**
     * AWS Elemental MediaTailor SSAI — custom segment prefix and explicit
     * tracking URL.
     *
     * @param adSegmentPrefix Substring matched against segment URLs to identify
     *                        ad segments. Pass {@code null} for default detection.
     * @param trackingUrl     Full MediaTailor tracking URL. Pass {@code null}
     *                        to let the tracker derive it from the manifest URI.
     */
    public static NRAdConfig mediaTailor(@Nullable String adSegmentPrefix,
                                         @Nullable String trackingUrl) {
        return new NRAdConfig(Type.SSAI_MT, adSegmentPrefix, trackingUrl);
    }

    // ── Private constructor ────────────────────────────────────────────────

    private NRAdConfig(Type type, @Nullable String adSegmentPrefix, @Nullable String trackingUrl) {
        this.type            = type;
        this.adSegmentPrefix = adSegmentPrefix;
        // Legacy alias mirrors the canonical value so existing readers keep
        // working until the field is removed.
        this.segmentPrefix   = adSegmentPrefix;
        this.trackingUrl     = trackingUrl;
    }

    // ── Object overrides ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NRAdConfig)) return false;
        NRAdConfig that = (NRAdConfig) o;
        return type == that.type
                && java.util.Objects.equals(adSegmentPrefix, that.adSegmentPrefix)
                && java.util.Objects.equals(trackingUrl,     that.trackingUrl);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, adSegmentPrefix, trackingUrl);
    }

    @Override
    public String toString() {
        if (type == Type.CSAI) {
            return "NRAdConfig{csai}";
        }
        StringBuilder sb = new StringBuilder("NRAdConfig{mediaTailor");
        if (adSegmentPrefix != null) sb.append(", adSegmentPrefix='").append(adSegmentPrefix).append("'");
        if (trackingUrl   != null) sb.append(", trackingUrl set");
        sb.append("}");
        return sb.toString();
    }
}
