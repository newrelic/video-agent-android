package com.newrelic.videoagent.mediatailor.detection;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.mediatailor.MTConstants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL-level heuristics for recognising MediaTailor streams and deriving
 * companion endpoints.
 *
 * <ul>
 *   <li>{@link #isMediaTailorUri(Uri)} — gate that activates the tracker only
 *       when the {@link androidx.media3.common.MediaItem} URI looks like a
 *       MediaTailor playback endpoint.</li>
 *   <li>{@link #manifestType(Uri)} — DASH vs HLS from file extension.</li>
 *   <li>{@link #extractTrackingUrl(Uri)} / {@link #extractSessionId(Uri)} —
 *       rewrite a sessionized manifest URL ({@code ?aws.sessionId=…} or
 *       {@code ?sessionId=…}) into its sibling {@code /v1/tracking/…/<sessionId>}
 *       URL used by {@link com.newrelic.videoagent.mediatailor.net.MTTrackingClient}
 *       to pull ad metadata.</li>
 * </ul>
 */
public final class MTDetector {

    private static final Pattern SESSION_ID = Pattern.compile("sessionId=([^&]+)");
    private static final Pattern MANIFEST_SEGMENT =
            Pattern.compile("/v1/(master|session|dash)/");
    private static final Pattern MANIFEST_FILE =
            Pattern.compile("/[^/]*\\.(m3u8|mpd)(\\?.*)?$");
    // Implicit-session flows carry no ?sessionId= query param; the session id
    // lives inside the media playlist path. HLS uses
    // /v1/manifest/{cfg}/{origin}/{sessionId}/{variant}.m3u8 and DASH uses
    // /v1/dash/{cfg}/{origin}/{sessionId}/{variant}.mpd. The tracking endpoint
    // is the sibling /v1/tracking/{cfg}/{origin}/{sessionId} in both cases.
    private static final Pattern MEDIA_SESSION_PATH =
            Pattern.compile("/v1/(?:manifest|dash)/([^/]+)/([^/]+)/([^/]+)/[^/]+\\.(m3u8|mpd)");

    private MTDetector() {}

    public static boolean isMediaTailorUri(Uri uri) {
        if (uri == null) return false;
        String s = uri.toString();
        return s != null && s.contains(MTConstants.MT_URL_MARKER);
    }

    public static String manifestType(Uri uri) {
        if (uri == null) return MTConstants.MANIFEST_TYPE_HLS;
        String path = uri.getPath();
        if (path == null) return MTConstants.MANIFEST_TYPE_HLS;
        if (path.endsWith(".mpd")) return MTConstants.MANIFEST_TYPE_DASH;
        if (path.endsWith(".m3u8")) return MTConstants.MANIFEST_TYPE_HLS;
        return MTConstants.MANIFEST_TYPE_HLS;
    }

    public static String extractTrackingUrl(Uri uri) {
        if (uri == null) return null;
        return extractTrackingUrl(uri.toString());
    }

    /**
     * String overload so the DASH {@code <Location>} rescue and other callers
     * that already hold a URL string can derive without wrapping in a {@link Uri}.
     */
    public static String extractTrackingUrl(String full) {
        if (full == null) return null;
        Matcher m = SESSION_ID.matcher(full);
        if (m.find()) {
            String sessionId = m.group(1);
            String rewritten = MANIFEST_SEGMENT.matcher(full).replaceFirst("/v1/tracking/");
            rewritten = MANIFEST_FILE.matcher(rewritten).replaceFirst("/" + sessionId);
            int q = rewritten.indexOf('?');
            if (q >= 0) rewritten = rewritten.substring(0, q);
            return rewritten;
        }
        // Implicit-session fallback: no query param, session id is a path
        // segment. Rebuild the tracking endpoint from the origin instead of
        // rewriting in place, since the manifest path shape differs.
        Matcher p = MEDIA_SESSION_PATH.matcher(full);
        if (p.find()) {
            String origin = full.substring(0, p.start());
            return origin + "/v1/tracking/" + p.group(1) + "/" + p.group(2) + "/" + p.group(3);
        }
        return null;
    }

    public static String extractSessionId(Uri uri) {
        if (uri == null) return null;
        String full = uri.toString();
        if (full == null) return null;
        Matcher m = SESSION_ID.matcher(full);
        if (m.find()) return m.group(1);
        // Implicit-session and tracking URLs carry the session id as the last
        // path segment rather than a query param.
        return sessionIdFromPath(full);
    }

    /**
     * Null-safe extraction of the trailing path segment — the MediaTailor
     * convention that the session id is the last segment of a tracking URL
     * ({@code /v1/tracking/{cfg}/{origin}/{sessionId}}). Strips any query
     * string first. Returns {@code null} for missing or malformed input.
     *
     * <p>Single source of truth for the "trailing segment is sessionId"
     * assumption so it stays fixable in one place if MediaTailor changes shape.</p>
     */
    public static String sessionIdFromPath(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) return null;
        return path.substring(slash + 1);
    }

    /**
     * Returns the name of the first ad-segment detection marker that matched
     * the URL, or {@code null} if none did. Shared by {@code MTHlsParser} and
     * {@code MTDashParser}, which differ only in which format-specific
     * path pattern/label pair they check as the second candidate.
     */
    @Nullable
    public static String whichMarkerMatched(@Nullable String url, @Nullable String adSegmentPrefix,
                                             String formatPathPattern, String formatPathLabel) {
        if (url == null) return null;
        if (url.contains(MTConstants.MT_SEGMENT_PATTERN))       return "aws-hostname";
        if (url.contains(formatPathPattern))                    return formatPathLabel;
        if (url.contains(MTConstants.MT_DEFAULT_AD_SEGMENT_PATH)) return "/tm/";
        if (adSegmentPrefix != null && url.contains(adSegmentPrefix)) return "custom:'" + adSegmentPrefix + "'";
        return null;
    }

    /**
     * Logs the one-line detection-mode summary shared by both manifest
     * parsers, under the caller's own log prefix.
     */
    public static void logDetectionMode(String logPrefix, @Nullable String adSegmentPrefix) {
        if (adSegmentPrefix != null) {
            NRLog.d(logPrefix + " detection: aws-hostname | /tm/ | custom='" + adSegmentPrefix + "'");
        } else {
            NRLog.d(logPrefix + " detection: aws-hostname | /tm/ (no custom prefix)");
        }
    }
}
