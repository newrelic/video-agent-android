package com.newrelic.videoagent.mediatailor.detection;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.BaseUrl;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.EventStream;
import androidx.media3.exoplayer.dash.manifest.Period;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.extractor.metadata.emsg.EventMessage;

import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.mediatailor.MTConstants;
import com.newrelic.videoagent.mediatailor.model.MTAdBreak;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a Media3 {@link DashManifest} into a list of {@link MTAdBreak}s.
 *
 * <p>Two shapes are recognised:</p>
 * <ul>
 *   <li><b>Multi-period:</b> a period whose representations point at a base URL
 *       that matches any of the four detection candidates is treated as an ad
 *       period. Media3 1.2+ exposes {@link BaseUrl}s on {@link Representation},
 *       not on {@link Period} directly — we walk down through adaptation sets.</li>
 *   <li><b>Single-period:</b> SCTE-35 {@link EventStream}s inside the period.</li>
 * </ul>
 *
 * <p>Detection candidates (checked in order for every base URL):</p>
 * <ol>
 *   <li>{@code segments.mediatailor} — default AWS ad-segment hostname.</li>
 *   <li>{@code /v1/dashsegment/} — MediaTailor CDN rewrite path.</li>
 *   <li>{@code /tm/} — AWS-recommended custom CDN prefix; always active.</li>
 *   <li>{@code segmentPrefix} — customer override; only checked when non-null.</li>
 * </ol>
 */
@OptIn(markerClass = UnstableApi.class)
public final class MTDashParser {

    private MTDashParser() {}

    /**
     * Parse the DASH manifest into ad breaks.
     *
     * @param manifest      Manifest from {@code ExoPlayer.getCurrentManifest()}.
     * @param segmentPrefix Optional customer CDN prefix override. Pass {@code null}
     *                      to rely on default detection ({@code segments.mediatailor},
     *                      {@code /v1/dashsegment/}, and {@code /tm/}).
     */
    public static List<MTAdBreak> parse(DashManifest manifest, @Nullable String segmentPrefix) {
        List<MTAdBreak> breaks = new ArrayList<>();
        if (manifest == null) return breaks;

        int periodCount = manifest.getPeriodCount();
        if (periodCount <= 0) return breaks;

        logDetectionMode(segmentPrefix);

        // ── Parse ──────────────────────────────────────────────────────────
        NRLog.d(MTConstants.LOG_PARSE_DASH + " parsing " + periodCount + " period(s), type="
                + (periodCount > 1 ? "multi-period" : "single-period"));

        if (periodCount > 1) {
            parseMultiPeriod(manifest, segmentPrefix, breaks);
        } else {
            parseSinglePeriod(manifest.getPeriod(0), breaks);
        }

        logResult(breaks);
        return breaks;
    }

    // ── Multi-period ───────────────────────────────────────────────────────

    private static void parseMultiPeriod(DashManifest manifest,
                                          @Nullable String segmentPrefix,
                                          List<MTAdBreak> out) {
        int count = manifest.getPeriodCount();
        for (int i = 0; i < count; i++) {
            Period period = manifest.getPeriod(i);
            String adMarker = whichMarkerMatchedPeriod(period, segmentPrefix);
            if (adMarker == null) continue;

            long startMs    = period.startMs;
            long durationMs = manifest.getPeriodDurationMs(i);
            if (durationMs < MTConstants.MIN_AD_DURATION_MS) continue;

            String id = period.id != null ? period.id : ("mt-period-" + startMs);
            NRLog.d(MTConstants.LOG_PARSE_DASH + "   ad period id=" + id
                    + " startMs=" + startMs
                    + " durationMs=" + durationMs
                    + " via=" + adMarker);
            out.add(new MTAdBreak(id, startMs, durationMs));
        }
    }

    // ── Single-period (SCTE-35) ────────────────────────────────────────────

    private static void parseSinglePeriod(Period period, List<MTAdBreak> out) {
        if (period == null || period.eventStreams == null) return;

        for (EventStream stream : period.eventStreams) {
            String scheme = stream.schemeIdUri != null ? stream.schemeIdUri : "";
            if (!scheme.toLowerCase().contains(MTConstants.SCTE35_SCHEME_MARKER)) continue;

            long[]         times  = stream.presentationTimesUs;
            EventMessage[] events = stream.events;
            if (times == null || events == null) continue;
            int n = Math.min(times.length, events.length);

            for (int i = 0; i < n; i++) {
                long startMs    = times[i] / 1000L;
                long durationMs = events[i] != null ? events[i].durationMs : 0L;
                if (durationMs < MTConstants.MIN_AD_DURATION_MS) continue;

                String id = events[i] != null && events[i].id != 0
                        ? "scte35-" + events[i].id
                        : "scte35-" + startMs;
                NRLog.d(MTConstants.LOG_PARSE_DASH + "   SCTE-35 cue id=" + id
                        + " startMs=" + startMs + " durationMs=" + durationMs);
                out.add(new MTAdBreak(id, startMs, durationMs));
            }
        }
    }

    // ── Detection helpers ──────────────────────────────────────────────────

    /**
     * Returns the detection marker label that identified this period as an ad
     * period, or {@code null} if the period is content.
     */
    @Nullable
    private static String whichMarkerMatchedPeriod(Period period,
                                                    @Nullable String segmentPrefix) {
        if (period == null || period.adaptationSets == null) return null;
        for (AdaptationSet set : period.adaptationSets) {
            if (set == null || set.representations == null) continue;
            for (Representation rep : set.representations) {
                if (rep == null || rep.baseUrls == null) continue;
                for (BaseUrl b : rep.baseUrls) {
                    if (b == null || b.url == null) continue;
                    String marker = whichMarkerMatched(b.url, segmentPrefix);
                    if (marker != null) return marker;
                }
            }
        }
        return null;
    }

    /**
     * Returns the name of the first detection marker that matched the URL,
     * or {@code null} if no marker matched.
     */
    @Nullable
    static String whichMarkerMatched(@Nullable String url, @Nullable String segmentPrefix) {
        if (url == null) return null;
        if (url.contains(MTConstants.MT_SEGMENT_PATTERN))          return "aws-hostname";
        if (url.contains(MTConstants.MT_DASHSEGMENT_PATH_PATTERN)) return "dashsegment-path";
        if (url.contains(MTConstants.MT_DEFAULT_AD_SEGMENT_PATH))  return "/tm/";
        if (segmentPrefix != null && url.contains(segmentPrefix))  return "custom:'" + segmentPrefix + "'";
        return null;
    }

    // ── Logging ────────────────────────────────────────────────────────────

    private static void logDetectionMode(@Nullable String segmentPrefix) {
        if (segmentPrefix != null) {
            NRLog.d(MTConstants.LOG_PARSE_DASH + " detection: aws-hostname | /tm/ | custom='"
                    + segmentPrefix + "'");
        } else {
            NRLog.d(MTConstants.LOG_PARSE_DASH + " detection: aws-hostname | /tm/ (no custom prefix)");
        }
    }

    private static void logResult(List<MTAdBreak> breaks) {
        if (breaks.isEmpty()) {
            NRLog.d(MTConstants.LOG_PARSE_DASH + " no ad breaks detected");
            return;
        }
        NRLog.d(MTConstants.LOG_PARSE_DASH + " " + breaks.size() + " ad break(s) detected:");
        for (int i = 0; i < breaks.size(); i++) {
            MTAdBreak b = breaks.get(i);
            NRLog.d(MTConstants.LOG_PARSE_DASH + "   break[" + i + "] id=" + b.id
                    + " start=" + b.startTimeMs + "ms"
                    + " duration=" + b.durationMs + "ms");
        }
    }
}
