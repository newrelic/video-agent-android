package com.newrelic.videoagent.mediatailor.detection;

import android.net.Uri;

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
        String full = uri.toString();
        if (full == null) return null;
        Matcher m = SESSION_ID.matcher(full);
        if (!m.find()) return null;
        String sessionId = m.group(1);
        String rewritten = MANIFEST_SEGMENT.matcher(full).replaceFirst("/v1/tracking/");
        rewritten = MANIFEST_FILE.matcher(rewritten).replaceFirst("/" + sessionId);
        int q = rewritten.indexOf('?');
        if (q >= 0) rewritten = rewritten.substring(0, q);
        return rewritten;
    }

    public static String extractSessionId(Uri uri) {
        if (uri == null) return null;
        String full = uri.toString();
        if (full == null) return null;
        Matcher m = SESSION_ID.matcher(full);
        return m.find() ? m.group(1) : null;
    }
}
