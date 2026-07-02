package com.newrelic.videoagent.mediatailor.detection;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.BaseUrl;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.EventStream;
import androidx.media3.exoplayer.dash.manifest.Period;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.extractor.metadata.emsg.EventMessage;

import com.newrelic.videoagent.mediatailor.MTConstants;
import com.newrelic.videoagent.mediatailor.model.MTAdBreak;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Turns a Media3 {@link DashManifest} into a list of {@link MTAdBreak}s.
 *
 * <p>Two shapes are recognised:</p>
 * <ul>
 *   <li><b>Multi-period:</b> a period whose representations point at a base URL
 *       containing {@code segments.mediatailor} is treated as an ad period.
 *       Media3 1.2 exposes {@code BaseUrl}s on {@link Representation}, not on
 *       {@link Period} directly — we walk down.</li>
 *   <li><b>Single-period:</b> SCTE-35 {@link EventStream}s inside the period.</li>
 * </ul>
 */
@OptIn(markerClass = UnstableApi.class)
public final class MTDashParser {

    private MTDashParser() {}

    /**
     * Cumulative count of DASH periods where some representations resolved to
     * ad-segment URLs and others didn't. Such periods are classified as
     * content because a single ad-flagged representation is not sufficient
     * evidence — a low-bitrate audio rendition happening to sit on a CDN path
     * that contains {@code /tm/} would otherwise misclassify a full content
     * period as an ad. Non-zero values point at manifests where detection is
     * ambiguous and worth investigating.
     */
    private static final AtomicInteger mixedPeriodCount = new AtomicInteger(0);

    public static int mixedPeriodCount() {
        return mixedPeriodCount.get();
    }

    public static List<MTAdBreak> parse(DashManifest manifest) {
        List<MTAdBreak> breaks = new ArrayList<>();
        if (manifest == null) return breaks;

        int periodCount = manifest.getPeriodCount();
        if (periodCount <= 0) return breaks;

        if (periodCount > 1) {
            parseMultiPeriod(manifest, breaks);
        } else {
            parseSinglePeriod(manifest.getPeriod(0), breaks);
        }
        return breaks;
    }

    private static void parseMultiPeriod(DashManifest manifest, List<MTAdBreak> out) {
        int count = manifest.getPeriodCount();
        for (int i = 0; i < count; i++) {
            Period period = manifest.getPeriod(i);
            if (!isMediaTailorAdPeriod(period)) continue;

            long startMs = period.startMs;
            long durationMs = manifest.getPeriodDurationMs(i);
            if (durationMs < MTConstants.MIN_AD_DURATION_MS) continue;

            String id = period.id != null ? period.id : ("mt-period-" + startMs);
            out.add(new MTAdBreak(id, startMs, durationMs));
        }
    }

    private static void parseSinglePeriod(Period period, List<MTAdBreak> out) {
        if (period == null || period.eventStreams == null) return;

        for (EventStream stream : period.eventStreams) {
            String scheme = stream.schemeIdUri != null ? stream.schemeIdUri : "";
            if (!scheme.toLowerCase().contains(MTConstants.SCTE35_SCHEME_MARKER)) continue;

            long[] times = stream.presentationTimesUs;
            EventMessage[] events = stream.events;
            if (times == null || events == null) continue;
            int n = Math.min(times.length, events.length);

            for (int i = 0; i < n; i++) {
                long startMs = times[i] / 1000L;
                long durationMs = events[i] != null ? events[i].durationMs : 0L;
                if (durationMs < MTConstants.MIN_AD_DURATION_MS) continue;

                String id = events[i] != null && events[i].id != 0
                        ? "scte35-" + events[i].id
                        : "scte35-" + startMs;
                out.add(new MTAdBreak(id, startMs, durationMs));
            }
        }
    }

    /**
     * A DASH period is an ad only when <em>every</em> representation in the
     * period resolves to an ad-segment URL. Requiring unanimous agreement
     * avoids false positives when a subset of representations happens to
     * live on a CDN path pattern that overlaps with MediaTailor's — most
     * commonly a low-bitrate audio rendition on a shared CDN — where a
     * single-hit heuristic would classify the whole period as an ad and
     * block content telemetry from progressing through it.
     */
    private static boolean isMediaTailorAdPeriod(Period period) {
        if (period == null || period.adaptationSets == null) return false;
        int totalReps = 0;
        int adReps = 0;
        for (AdaptationSet set : period.adaptationSets) {
            if (set == null || set.representations == null) continue;
            for (Representation rep : set.representations) {
                if (rep == null || rep.baseUrls == null) continue;
                totalReps++;
                if (isAdRepresentation(rep)) adReps++;
            }
        }
        if (totalReps == 0) return false;
        if (adReps == totalReps) return true;
        if (adReps > 0) {
            // Some but not all representations point at ad segments — treat
            // as content (safer than a false-positive ad break) and record
            // the ambiguity for diagnostics.
            mixedPeriodCount.incrementAndGet();
        }
        return false;
    }

    private static boolean isAdRepresentation(Representation rep) {
        for (BaseUrl b : rep.baseUrls) {
            if (b == null || b.url == null) continue;
            if (b.url.contains(MTConstants.MT_SEGMENT_PATTERN)) return true;
            if (b.url.contains(MTConstants.MT_DASHSEGMENT_PATH_PATTERN)) return true;
        }
        return false;
    }
}
