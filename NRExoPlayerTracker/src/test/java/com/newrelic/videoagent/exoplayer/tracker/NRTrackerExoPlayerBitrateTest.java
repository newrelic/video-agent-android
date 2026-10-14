package com.newrelic.videoagent.exoplayer.tracker;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;

/**
 * Regression coverage for NR-617146: on Android, {@code contentBitrate} reports 0 for HLS
 * streams.
 * <p>
 * Root cause: media3's HlsMediaPeriod tags the main variant's segment loads as
 * {@code C.TRACK_TYPE_DEFAULT} (not {@code C.TRACK_TYPE_VIDEO}) whenever video is muxed with
 * audio, which is the common case for HLS. {@link NRTrackerExoPlayer#onLoadCompleted} only
 * populated bitrate fields when {@code trackType == C.TRACK_TYPE_VIDEO}, so HLS loads never hit
 * that block and {@code actualBitrate} (surfaced as {@code contentBitrate}) stayed at 0.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class NRTrackerExoPlayerBitrateTest {

    private static final String SEGMENT_URL = "https://example.com/hls/segment.ts";

    private MockNRTrackerExoPlayer tracker;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        ExoPlayer realPlayer = new ExoPlayer.Builder(context).build();
        tracker = new MockNRTrackerExoPlayer();
        tracker.setPlayer(realPlayer);
        tracker.clearEvents();
    }

    private static Format videoFormat(int bitrate) {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H264)
                .setWidth(1280)
                .setHeight(720)
                .setAverageBitrate(bitrate)
                .setPeakBitrate(bitrate)
                .build();
    }

    private static Format audioFormat(int bitrate) {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setAverageBitrate(bitrate)
                .setPeakBitrate(bitrate)
                .build();
    }

    private static MediaLoadData mediaLoadData(int trackType, Format trackFormat) {
        return new MediaLoadData(
                C.DATA_TYPE_MEDIA,
                trackType,
                trackFormat,
                C.SELECTION_REASON_UNKNOWN,
                /* trackSelectionData= */ null,
                /* mediaStartTimeMs= */ 0,
                /* mediaEndTimeMs= */ 0);
    }

    private static LoadEventInfo loadEventInfo(long loadDurationMs, long bytesLoaded) {
        Uri uri = Uri.parse(SEGMENT_URL);
        return new LoadEventInfo(
                LoadEventInfo.getNewId(),
                new DataSpec(uri),
                uri,
                Collections.emptyMap(),
                /* elapsedRealtimeMs= */ 0,
                loadDurationMs,
                bytesLoaded);
    }

    // -------------------------------------------------------------------------
    // Bug repro: HLS muxed main-variant segment load (TRACK_TYPE_DEFAULT + video Format)
    // -------------------------------------------------------------------------

    @Test
    public void onLoadCompleted_hlsMuxedVariant_defaultTrackTypeWithVideoFormat_setsNonZeroActualBitrate() {
        MediaLoadData mediaLoadData = mediaLoadData(C.TRACK_TYPE_DEFAULT, videoFormat(2_000_000));
        LoadEventInfo loadEventInfo = loadEventInfo(/* loadDurationMs= */ 500, /* bytesLoaded= */ 250_000);

        tracker.onLoadCompleted(null, loadEventInfo, mediaLoadData);

        Long actualBitrate = tracker.getActualBitrate();
        assertNotNull("contentBitrate must not be null for an HLS muxed main-variant load", actualBitrate);
        assertTrue("contentBitrate must be > 0 for an HLS muxed main-variant load, got " + actualBitrate,
                actualBitrate > 0);
    }

    @Test
    public void onLoadCompleted_hlsMuxedVariant_alsoPopulatesRenditionAndNetworkDownloadBitrate() {
        MediaLoadData mediaLoadData = mediaLoadData(C.TRACK_TYPE_DEFAULT, videoFormat(2_000_000));
        LoadEventInfo loadEventInfo = loadEventInfo(/* loadDurationMs= */ 500, /* bytesLoaded= */ 250_000);

        tracker.onLoadCompleted(null, loadEventInfo, mediaLoadData);

        assertNotNull("contentRenditionBitrate must be populated for the same shared gate", tracker.getRenditionBitrate());
        assertNotNull("contentNetworkDownloadBitrate must be populated for the same shared gate", tracker.getNetworkDownloadBitrate());
    }

    // -------------------------------------------------------------------------
    // DASH / progressive regression: TRACK_TYPE_VIDEO loads must keep working
    // -------------------------------------------------------------------------

    @Test
    public void onLoadCompleted_dashOrProgressiveVideoTrack_trackTypeVideo_setsNonZeroActualBitrate() {
        MediaLoadData mediaLoadData = mediaLoadData(C.TRACK_TYPE_VIDEO, videoFormat(3_000_000));
        LoadEventInfo loadEventInfo = loadEventInfo(/* loadDurationMs= */ 400, /* bytesLoaded= */ 300_000);

        tracker.onLoadCompleted(null, loadEventInfo, mediaLoadData);

        Long actualBitrate = tracker.getActualBitrate();
        assertNotNull("contentBitrate must not be null for a DASH/progressive VIDEO-typed load", actualBitrate);
        assertTrue("contentBitrate must be > 0 for a DASH/progressive VIDEO-typed load", actualBitrate > 0);
    }

    // -------------------------------------------------------------------------
    // Unaffected loads: genuinely audio-only DEFAULT-typed loads must NOT be treated as video
    // -------------------------------------------------------------------------

    @Test
    public void onLoadCompleted_audioOnlyDefaultTrack_doesNotSetActualBitrate() {
        MediaLoadData mediaLoadData = mediaLoadData(C.TRACK_TYPE_DEFAULT, audioFormat(128_000));
        LoadEventInfo loadEventInfo = loadEventInfo(/* loadDurationMs= */ 500, /* bytesLoaded= */ 16_000);

        tracker.onLoadCompleted(null, loadEventInfo, mediaLoadData);

        assertNull("An audio-only DEFAULT-typed load must not be mistaken for video",
                tracker.getActualBitrate());
    }

    @Test
    public void onLoadCompleted_audioTrackType_stillIgnoredRegardlessOfFormat() {
        MediaLoadData mediaLoadData = mediaLoadData(C.TRACK_TYPE_AUDIO, videoFormat(2_000_000));
        LoadEventInfo loadEventInfo = loadEventInfo(/* loadDurationMs= */ 500, /* bytesLoaded= */ 250_000);

        tracker.onLoadCompleted(null, loadEventInfo, mediaLoadData);

        assertNull("TRACK_TYPE_AUDIO loads must never populate contentBitrate", tracker.getActualBitrate());
    }

    // -------------------------------------------------------------------------
    // Null-consistency: getActualBitrate() must match the null-coalescing pattern already
    // used by getManifestBitrate()/getRenditionBitrate()/getNetworkDownloadBitrate().
    // -------------------------------------------------------------------------

    @Test
    public void getActualBitrate_returnsNullWhenNeverSet() {
        assertNull("A tracker that never observed a video load must report contentBitrate as null, not 0",
                tracker.getActualBitrate());
    }
}
