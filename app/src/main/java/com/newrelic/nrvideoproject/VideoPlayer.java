package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import com.newrelic.videoagent.core.NRAdConfig;
import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoPlayerConfiguration;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.core.tracker.NRTracker;
import com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoPlayer extends AppCompatActivity {

    private static final String GAMEDAY_ASSET_URL = "https://turtle-tube.appspot.com/t/t2/dash.mpd";
    private static final int TC1_LOW_BITRATE = 600_000;
    private static final int TC1_MID_BITRATE = 1_000_000;
    private static final int TC1_TOP_BITRATE = 3_000_000;

    private ExoPlayer player;
    private Integer trackerId;
    private final Handler gamedayHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        String video = getIntent().getStringExtra("video");

        if (video.equals("Tears")) {
            Log.v("VideoPlayer", "Play Tears");
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else if (video.equals("Playhouse")) {
            Log.v("VideoPlayer", "Play Playhouse");
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else if (video.equals("Kite")) {
            Log.v("VideoPlayer", "Play Kite");
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else if (video.equals("Live")) {
            Log.v("VideoPlayer", "Play Live");
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else {
            Log.v("VideoPlayer","Unknown video");
        }

        Button btnTc0 = findViewById(R.id.btn_tc0);
        Button btnTc1 = findViewById(R.id.btn_tc1);
        Button btnTc2 = findViewById(R.id.btn_tc2);
        btnTc0.setOnClickListener(v -> runTC0());
        btnTc1.setOnClickListener(v -> runTC1());
        btnTc2.setOnClickListener(v -> runTC2());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        gamedayHandler.removeCallbacksAndMessages(null);
        if (trackerId != null) {
            NRVideo.releaseTracker(trackerId);
        }
        if (player != null) {
            player.stop();
        }
    }

    private void resetForNewSession() {
        gamedayHandler.removeCallbacksAndMessages(null);
        if (player != null) {
            if (trackerId != null) {
                NRVideo.releaseTracker(trackerId);
            }
            player.release();
            player = null;
        }
    }

    private void setMaxVideoBitrate(int bitrate) {
        if (player == null) return;
        player.setTrackSelectionParameters(
            player.getTrackSelectionParameters().buildUpon().setMaxVideoBitrate(bitrate).build()
        );
    }

    private void runTC0() {
        Log.d("VideoPlayer", "GAMEDAY TC0 started");
        resetForNewSession();
        playVideo(GAMEDAY_ASSET_URL);
    }

    private void runTC1() {
        Log.d("VideoPlayer", "GAMEDAY TC1 started");
        resetForNewSession();
        playVideo(GAMEDAY_ASSET_URL);
        setMaxVideoBitrate(TC1_LOW_BITRATE);
        gamedayHandler.postDelayed(() -> setMaxVideoBitrate(TC1_MID_BITRATE), 20_000);
        gamedayHandler.postDelayed(() -> setMaxVideoBitrate(TC1_TOP_BITRATE), 27_000);
        gamedayHandler.postDelayed(() -> setMaxVideoBitrate(TC1_MID_BITRATE), 34_000);
        gamedayHandler.postDelayed(this::pauseForTenSeconds, 41_000);
    }

    private void pauseForTenSeconds() {
        if (player == null) return;
        Log.d("VideoPlayer", "GAMEDAY TC1 pausing for 10s");
        player.setPlayWhenReady(false);
        gamedayHandler.postDelayed(() -> {
            if (player != null) player.setPlayWhenReady(true);
        }, 10_000);
    }

    private void runTC2() {
        Log.d("VideoPlayer", "GAMEDAY TC2 started");
        resetForNewSession();
        playVideo(GAMEDAY_ASSET_URL);
        gamedayHandler.postDelayed(this::seekNearEnd, 3_000);
        gamedayHandler.postDelayed(this::triggerBrokenUrl, 6_000);
        gamedayHandler.postDelayed(this::fireOneCustomEvent, 9_000);
    }

    private void triggerBrokenUrl() {
        if (player == null) return;
        Log.d("VideoPlayer", "GAMEDAY TC2 triggering broken URL");
        String brokenUrl = "https://commondatastorage.googleapis.com/does-not-exist/broken-" + System.currentTimeMillis() + ".mp4";
        player.setMediaItem(MediaItem.fromUri(brokenUrl));
        player.prepare();
    }

    private void seekNearEnd() {
        if (player == null) return;
        long duration = player.getDuration();
        if (duration != C.TIME_UNSET && duration > 3_000) {
            Log.d("VideoPlayer", "GAMEDAY TC2 seeking near end");
            player.seekTo(duration - 3_000);
        }
    }

    private void fireOneCustomEvent() {
        if (trackerId == null) return;
        Log.d("VideoPlayer", "GAMEDAY TC2 firing one custom event");
        Map<String, Object> attr = new HashMap<>();
        attr.put("actionName", "GAMEDAY_TC2_CUSTOM_EVENT");
        NRVideo.recordCustomEvent(attr, trackerId);
    }

    private void playVideo(String videoUrl) {
        player = new ExoPlayer.Builder(this).build();

        Map<String, Object> customAttr = new HashMap<>();
        customAttr.put("something", "This is my test title");
        customAttr.put("myAttrStr", "Hello");
        customAttr.put("myAttrInt", 101);
        customAttr.put("name", "nr-video-agent-android-01-24JUL-john-starc");
        NRVideoPlayerConfiguration playerConfiguration = new NRVideoPlayerConfiguration("test-player", player, (NRAdConfig) null, customAttr);
        trackerId = NRVideo.addPlayer(playerConfiguration);
        // Get the content tracker and configure aggregation
        NRTracker tracker = NewRelicVideoAgent.getInstance().getContentTracker(trackerId);
        if (tracker instanceof NRTrackerExoPlayer) {
            Boolean aggregationEnabled = true;
            ((NRTrackerExoPlayer) tracker).setDroppedFrameAggregationEnabled(aggregationEnabled); // true for testing
            Log.d("VideoPlayer", "CONTENT_DROPPED_FRAMES events aggregation enabled: " + aggregationEnabled);
        }

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("actionName", "VIDEO_STARTED");
        attributes.put("videoUrl", videoUrl);
        attributes.put("playerType", "ExoPlayer");
        NRVideo.recordCustomEvent(attributes, trackerId);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);
        // Set the playlist URIs
        List<Uri> uris = new ArrayList<>();
        uris.add(Uri.parse(videoUrl));
        player.setMediaItem(MediaItem.fromUri(videoUrl));
        // Prepare the player.
        player.setPlayWhenReady(true);
        player.prepare();
    }
}
