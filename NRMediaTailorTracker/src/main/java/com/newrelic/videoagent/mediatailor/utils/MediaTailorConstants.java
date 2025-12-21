package com.newrelic.videoagent.mediatailor.utils;

import java.util.regex.Pattern;

/**
 * MediaTailor Constants
 * Contains all regex patterns, config defaults, and constant values for AWS MediaTailor ad tracking
 * Based on VideoJS mt-constants.js
 */
public class MediaTailorConstants {

    // HLS Manifest Regex Patterns
    public static final Pattern REGEX_CUE_OUT = Pattern.compile("#EXT-X-CUE-OUT:DURATION=([\\d.]+)");
    public static final Pattern REGEX_CUE_IN = Pattern.compile("#EXT-X-CUE-IN");
    public static final Pattern REGEX_DISCONTINUITY = Pattern.compile("#EXT-X-DISCONTINUITY");
    public static final Pattern REGEX_MAP = Pattern.compile("#EXT-X-MAP:URI=\"([^\"]+)\"");
    public static final Pattern REGEX_EXTINF = Pattern.compile("#EXTINF:([\\d.]+)");
    public static final Pattern REGEX_TARGET_DURATION = Pattern.compile("#EXT-X-TARGETDURATION:(\\d+)");

    // MediaTailor URL Patterns
    public static final String MT_SEGMENT_PATTERN = "segments.mediatailor";
    public static final String MT_URL_PATTERN = ".mediatailor.";

    // Default Configuration
    public static final boolean DEFAULT_ENABLE_MANIFEST_PARSING = true;
    public static final boolean DEFAULT_ENABLE_TRACKING_API = true;
    public static final long DEFAULT_LIVE_MANIFEST_POLL_INTERVAL_MS = 5000; // 5s
    public static final long DEFAULT_LIVE_TRACKING_POLL_INTERVAL_MS = 10000; // 10s
    public static final int DEFAULT_TRACKING_API_TIMEOUT_MS = 5000; // 5s
    public static final long DEFAULT_TIME_UPDATE_INTERVAL_MS = 250; // 250ms

    // Timing Thresholds
    public static final double MIN_AD_DURATION = 0.5; // Minimum ad duration in seconds (filter false positives)
    public static final double AD_TIMING_TOLERANCE = 0.5; // Tolerance for matching ad times in seconds
    public static final long POST_AD_PAUSE_THRESHOLD_MS = 500; // Ignore pause events within 500ms after ad break

    // Stream Types
    public static final String STREAM_TYPE_VOD = "vod";
    public static final String STREAM_TYPE_LIVE = "live";

    // Manifest Types
    public static final String MANIFEST_TYPE_HLS = "hls";
    public static final String MANIFEST_TYPE_DASH = "dash";

    // Ad Position Types (for VOD only)
    public static final String AD_POSITION_PRE = "pre";
    public static final String AD_POSITION_MID = "mid";
    public static final String AD_POSITION_POST = "post";

    // Ad Detection Sources
    public static final String AD_SOURCE_MANIFEST_CUE = "manifest-cue";
    public static final String AD_SOURCE_TRACKING_API = "tracking-api";
    public static final String AD_SOURCE_MANIFEST_AND_TRACKING = "manifest+tracking";
    public static final String AD_SOURCE_VHS_DISCONTINUITY = "vhs-discontinuity";
    public static final String AD_SOURCE_DASH_EMSG = "dash-emsg";
    public static final String AD_SOURCE_DASH_EVENT_STREAM = "dash-event-stream";

    // Quartile Percentages
    public static final double QUARTILE_Q1 = 0.25; // 25%
    public static final double QUARTILE_Q2 = 0.50; // 50%
    public static final double QUARTILE_Q3 = 0.75; // 75%

    // Ad Partner
    public static final String AD_PARTNER = "aws-mediatailor";

    // Heartbeat Interval
    public static final long AD_HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds

    private MediaTailorConstants() {
        // Prevent instantiation
    }
}
