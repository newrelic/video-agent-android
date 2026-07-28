package com.newrelic.videoagent.mediatailor.detection;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.hls.HlsManifest;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;

import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.mediatailor.MTConstants;
import com.newrelic.videoagent.mediatailor.model.MTAdBreak;
import com.newrelic.videoagent.mediatailor.model.MTAdPod;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a Media3 {@link HlsManifest} into a list of {@link MTAdBreak}s.
 *
 * <p>Media3 strips {@code #EXT-X-CUE-OUT} / {@code #EXT-X-CUE-IN} from the
 * typed model, so ad segments are identified by URL pattern. Detection checks
 * four candidates in order:</p>
 * <ol>
 *   <li>{@code segments.mediatailor} — default AWS ad-segment hostname.</li>
 *   <li>{@code /v1/hlssegment/} — MediaTailor CDN rewrite path.</li>
 *   <li>{@code /tm/} — AWS-recommended custom CDN prefix; always active.</li>
 *   <li>{@code segmentPrefix} — customer override; only checked when non-null.</li>
 * </ol>
 *
 * <p>Pod boundaries inside a break are detected via
 * {@link HlsMediaPlaylist.Segment#relativeDiscontinuitySequence} changes.</p>
 */
@OptIn(markerClass = UnstableApi.class)
public final class MTHlsParser {

    private MTHlsParser() {}

    /**
     * Parse the HLS manifest into ad breaks.
     *
     * @param manifest      Manifest from {@code ExoPlayer.getCurrentManifest()}.
     * @param segmentPrefix Optional customer CDN prefix override. Pass {@code null}
     *                      to rely on default detection ({@code segments.mediatailor},
     *                      {@code /v1/hlssegment/}, and {@code /tm/}).
     */
    public static List<MTAdBreak> parse(HlsManifest manifest, @Nullable String segmentPrefix) {
        List<MTAdBreak> breaks = new ArrayList<>();
        if (manifest == null || manifest.mediaPlaylist == null) return breaks;
        HlsMediaPlaylist playlist = manifest.mediaPlaylist;
        if (playlist.segments == null || playlist.segments.isEmpty()) return breaks;

        logDetectionMode(segmentPrefix);

        // ── Parse ad breaks ────────────────────────────────────────────────
        MTAdBreak currentBreak = null;
        MTAdPod   currentPod   = null;
        int lastDiscontinuity  = Integer.MIN_VALUE;
        String firstMatchVia   = null;

        for (HlsMediaPlaylist.Segment seg : playlist.segments) {
            if (seg == null) continue;
            long startMs = seg.relativeStartTimeUs / 1000L;
            long durMs   = Math.max(seg.durationUs / 1000L, 0L);

            String matchedVia = whichMarkerMatched(seg.url, segmentPrefix);
            if (matchedVia != null) {
                if (currentBreak == null) {
                    currentBreak      = new MTAdBreak("hls-break-" + startMs, startMs, 0L);
                    currentPod        = new MTAdPod(startMs, 0L);
                    lastDiscontinuity = seg.relativeDiscontinuitySequence;
                    firstMatchVia     = matchedVia;
                } else if (seg.relativeDiscontinuitySequence != lastDiscontinuity) {
                    closePod(currentBreak, currentPod);
                    currentPod        = new MTAdPod(startMs, 0L);
                    lastDiscontinuity = seg.relativeDiscontinuitySequence;
                }
                currentBreak.durationMs += durMs;
                if (currentPod != null) currentPod.durationMs += durMs;
            } else if (currentBreak != null) {
                closeBreak(breaks, currentBreak, currentPod);
                currentBreak      = null;
                currentPod        = null;
                lastDiscontinuity = Integer.MIN_VALUE;
            }
        }
        if (currentBreak != null) {
            closeBreak(breaks, currentBreak, currentPod);
        }

        logResult(breaks, firstMatchVia);
        return breaks;
    }

    // ── Detection ──────────────────────────────────────────────────────────

    /**
     * Returns the name of the first detection marker that matched the URL,
     * or {@code null} if the URL is not an ad segment.
     *
     * <p>The label is used in log output to show which path triggered detection
     * (inspired by VideoJS PR #108's {@code whichAdSegmentMarker} helper).</p>
     */
    @Nullable
    static String whichMarkerMatched(@Nullable String url, @Nullable String segmentPrefix) {
        if (url == null) return null;
        if (url.contains(MTConstants.MT_SEGMENT_PATTERN))         return "aws-hostname";
        if (url.contains(MTConstants.MT_HLSSEGMENT_PATH_PATTERN)) return "hlssegment-path";
        if (url.contains(MTConstants.MT_DEFAULT_AD_SEGMENT_PATH)) return "/tm/";
        if (segmentPrefix != null && url.contains(segmentPrefix)) return "custom:'" + segmentPrefix + "'";
        return null;
    }

    // ── Logging ────────────────────────────────────────────────────────────

    private static void logDetectionMode(@Nullable String segmentPrefix) {
        if (segmentPrefix != null) {
            NRLog.d(MTConstants.LOG_PARSE_HLS + " detection: aws-hostname | /tm/ | custom='"
                    + segmentPrefix + "'");
        } else {
            NRLog.d(MTConstants.LOG_PARSE_HLS + " detection: aws-hostname | /tm/ (no custom prefix)");
        }
    }

    private static void logResult(List<MTAdBreak> breaks, @Nullable String firstMatchVia) {
        if (breaks.isEmpty()) {
            NRLog.d(MTConstants.LOG_PARSE_HLS + " no ad breaks detected");
            return;
        }
        NRLog.d(MTConstants.LOG_PARSE_HLS + " " + breaks.size() + " ad break(s) detected"
                + (firstMatchVia != null ? " — first match via=" + firstMatchVia : "") + ":");
        for (int i = 0; i < breaks.size(); i++) {
            MTAdBreak b = breaks.get(i);
            NRLog.d(MTConstants.LOG_PARSE_HLS + "   break[" + i + "]"
                    + " start=" + b.startTimeMs + "ms"
                    + " duration=" + b.durationMs + "ms"
                    + " pods=" + b.pods.size());
        }
    }

    // ── Break / pod builders ───────────────────────────────────────────────

    private static void closePod(MTAdBreak br, MTAdPod pod) {
        if (pod == null) return;
        if (pod.durationMs < MTConstants.MIN_AD_DURATION_MS) return;
        pod.endTimeMs = pod.startTimeMs + pod.durationMs;
        br.pods.add(pod);
    }

    private static void closeBreak(List<MTAdBreak> out, MTAdBreak br, MTAdPod pod) {
        closePod(br, pod);
        if (br.durationMs < MTConstants.MIN_AD_DURATION_MS) return;
        br.endTimeMs = br.startTimeMs + br.durationMs;
        out.add(br);
    }
}
