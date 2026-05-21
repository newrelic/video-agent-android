package com.newrelic.videoagent.mediatailor.detection;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.hls.HlsManifest;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;

import com.newrelic.videoagent.mediatailor.MTConstants;
import com.newrelic.videoagent.mediatailor.model.MTAdBreak;
import com.newrelic.videoagent.mediatailor.model.MTAdPod;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a Media3 {@link HlsManifest} into a list of {@link MTAdBreak}s.
 *
 * <p>Media3 strips {@code #EXT-X-CUE-OUT} / {@code #EXT-X-CUE-IN} from the
 * typed model, so we detect ad segments by URL pattern:
 * either {@code segments.mediatailor} (hostname) or {@code /v1/hlssegment/}
 * (path, used when responses are served through MediaTailor's own/CDN rewrite host).
 * Pod boundaries inside a break are detected via
 * {@link HlsMediaPlaylist.Segment#relativeDiscontinuitySequence} changes.</p>
 */
@OptIn(markerClass = UnstableApi.class)
public final class MTHlsParser {

    private MTHlsParser() {}

    public static List<MTAdBreak> parse(HlsManifest manifest) {
        List<MTAdBreak> breaks = new ArrayList<>();
        if (manifest == null || manifest.mediaPlaylist == null) return breaks;
        HlsMediaPlaylist playlist = manifest.mediaPlaylist;
        if (playlist.segments == null || playlist.segments.isEmpty()) return breaks;

        MTAdBreak currentBreak = null;
        MTAdPod currentPod = null;
        int lastDiscontinuity = Integer.MIN_VALUE;

        for (HlsMediaPlaylist.Segment seg : playlist.segments) {
            if (seg == null) continue;
            long startMs = seg.relativeStartTimeUs / 1000L;
            long durMs = Math.max(seg.durationUs / 1000L, 0L);

            if (isMediaTailorSegment(seg)) {
                if (currentBreak == null) {
                    currentBreak = new MTAdBreak("hls-break-" + startMs, startMs, 0L);
                    currentPod = new MTAdPod(startMs, 0L);
                    lastDiscontinuity = seg.relativeDiscontinuitySequence;
                } else if (seg.relativeDiscontinuitySequence != lastDiscontinuity) {
                    closePod(currentBreak, currentPod);
                    currentPod = new MTAdPod(startMs, 0L);
                    lastDiscontinuity = seg.relativeDiscontinuitySequence;
                }
                currentBreak.durationMs += durMs;
                if (currentPod != null) currentPod.durationMs += durMs;
            } else if (currentBreak != null) {
                closeBreak(breaks, currentBreak, currentPod);
                currentBreak = null;
                currentPod = null;
                lastDiscontinuity = Integer.MIN_VALUE;
            }
        }
        if (currentBreak != null) {
            closeBreak(breaks, currentBreak, currentPod);
        }
        return breaks;
    }

    private static boolean isMediaTailorSegment(HlsMediaPlaylist.Segment seg) {
        if (seg.url == null) return false;
        return seg.url.contains(MTConstants.MT_SEGMENT_PATTERN)
                || seg.url.contains(MTConstants.MT_HLSSEGMENT_PATH_PATTERN);
    }

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
