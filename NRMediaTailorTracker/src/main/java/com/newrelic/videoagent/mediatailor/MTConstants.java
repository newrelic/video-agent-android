package com.newrelic.videoagent.mediatailor;

/**
 * Central bag of compile-time constants used across the MediaTailor tracker.
 *
 * <p>Grouped by concern:</p>
 * <ul>
 *   <li><b>URL markers</b> — substrings we look for in content-source URLs
 *       ({@link #MT_URL_MARKER}) and in ad-period BaseURLs / segment URLs
 *       ({@link #MT_SEGMENT_PATTERN}, {@link #MT_DASHSEGMENT_PATH_PATTERN},
 *       {@link #MT_HLSSEGMENT_PATH_PATTERN}).</li>
 *   <li><b>Manifest &amp; stream types</b> — logical strings used internally
 *       ({@link #MANIFEST_TYPE_HLS}, {@link #MANIFEST_TYPE_DASH},
 *       {@link #STREAM_TYPE_VOD}, {@link #STREAM_TYPE_LIVE}).</li>
 *   <li><b>Event identity</b> — values reported on every {@code VideoAdAction}
 *       event produced by the tracker ({@link #TRACKER_NAME},
 *       {@link #AD_PARTNER}).</li>
 *   <li><b>Timing thresholds</b> — {@link #MIN_AD_DURATION_MS} filters false
 *       positives; {@link #AD_TIMING_TOLERANCE_MS} controls de-dup tolerance
 *       when merging manifest-detected breaks with tracking-API avails;
 *       {@link #PLAYHEAD_POLL_INTERVAL_MS} is the tick cadence.</li>
 *   <li><b>HTTP timeouts</b> — {@link #TRACKING_TIMEOUT_MS},
 *       {@link #TRACKING_MAX_RETRIES}.</li>
 *   <li><b>Quartile fractions</b> — {@link #QUARTILE_Q1}, {@link #QUARTILE_Q2},
 *       {@link #QUARTILE_Q3}.</li>
 *   <li><b>VAST beacon event types</b> — used by {@link com.newrelic.videoagent.mediatailor.net.MTBeaconFirer}
 *       when client-side reporting is enabled (see {@code BEACON_*}).</li>
 * </ul>
 */
public final class MTConstants {

    private MTConstants() {}

    public static final String MT_URL_MARKER = "mediatailor";
    public static final String MT_SEGMENT_PATTERN = "segments.mediatailor";
    // Alternative MediaTailor ad-period markers — used when responses are served
    // through MediaTailor's own/CDN rewrite host (see user guide p.183 for DASH).
    public static final String MT_DASHSEGMENT_PATH_PATTERN = "/v1/dashsegment/";
    public static final String MT_HLSSEGMENT_PATH_PATTERN = "/v1/hlssegment/";

    public static final String SCTE35_SCHEME_MARKER = "scte35";

    public static final String MANIFEST_TYPE_HLS = "hls";
    public static final String MANIFEST_TYPE_DASH = "dash";

    public static final String STREAM_TYPE_VOD = "vod";
    public static final String STREAM_TYPE_LIVE = "live";

    public static final String AD_POSITION_PRE = "pre";
    public static final String AD_POSITION_MID = "mid";
    public static final String AD_POSITION_POST = "post";

    public static final String AD_PARTNER = "aws-mediatailor";
    public static final String TRACKER_NAME = "NRMTracker";

    public static final long MIN_AD_DURATION_MS = 500L;
    public static final long AD_TIMING_TOLERANCE_MS = 500L;

    public static final long PLAYHEAD_POLL_INTERVAL_MS = 250L;

    public static final int TRACKING_TIMEOUT_MS = 5000;
    public static final int TRACKING_MAX_RETRIES = 1;

    public static final double QUARTILE_Q1 = 0.25;
    public static final double QUARTILE_Q2 = 0.50;
    public static final double QUARTILE_Q3 = 0.75;
}
