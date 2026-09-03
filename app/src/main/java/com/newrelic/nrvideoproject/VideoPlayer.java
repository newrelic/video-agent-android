package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
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

    private ExoPlayer player;
    private Integer trackerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        String video = getIntent().getStringExtra("video");

        if (video.equals("Tears")) {
            Log.v("VideoPlayer", "Play Tears");
            playVideo("https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd");
        }
        else if (video.equals("Playhouse")) {
            Log.v("VideoPlayer", "Play Playhouse");
            playVideo("https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd");
        }
        else if (video.equals("Kite")) {
            Log.v("VideoPlayer", "Play Kite");
            playVideo("https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd");
        }
        else if (video.equals("Live")) {
            Log.v("VideoPlayer", "Play Live");
            playVideo("https://livesim2.dashif.org/livesim2/ato_10/testpic3_2s/Manifest.mpd");
        }
        else if (video.equals("direct")) {
            String directUrl = getIntent().getStringExtra("direct_url");
            Log.v("VideoPlayer", "Play direct URL: " + directUrl);
            playVideo(directUrl);
        }
        else {
            Log.v("VideoPlayer","Unknown video");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NRVideo.releaseTracker(trackerId);
        player.stop();
    }

    private void playVideo(String videoUrl) {
        player = new ExoPlayer.Builder(this).build();

        Map<String, Object> customAttr = new HashMap<>();
        customAttr.put("something", "This is my test title");
        customAttr.put("myAttrStr", "Hello");
        customAttr.put("myAttrInt", 101);
        customAttr.put("name", "nr-video-agent-android-01-24JUL-john-starc");
        NRVideoPlayerConfiguration playerConfiguration = new NRVideoPlayerConfiguration(
                "test-player", player, NRVideoPlayerConfiguration.PLAYER_TYPE_EXO, (NRAdConfig) null, customAttr);
        trackerId = NRVideo.addPlayer(playerConfiguration);

        NRVideo.setUserId("test-exo-001");
        NRVideo.setAttribute(trackerId, "playerType", "ExoPlayer");
        NRVideo.setAttribute(trackerId, "appVersion", "1.1");
        NRVideo.setAttribute(trackerId, "testEnvironment", "staging");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("actionName", "VIDEO_STARTED");
        attributes.put("videoUrl", videoUrl);
        attributes.put("playerType", "ExoPlayer");
        NRVideo.recordCustomEvent(attributes, trackerId);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onEvents(Player player, Player.Events events) {
                updateLiveUi(player, playerView);
            }
        });
        // Set the playlist URIs
        List<Uri> uris = new ArrayList<>();
        uris.add(Uri.parse(videoUrl));
        player.setMediaItem(MediaItem.fromUri(videoUrl));
        // Prepare the player.
        player.setPlayWhenReady(true);
        player.prepare();
    }

    // Stock PlayerControlView has no live-aware UI (see androidx.media3.ui.PlayerControlView):
    // exo_position/exo_duration/exo_progress always render as plain elapsed/total time, live or not.
    private void updateLiveUi(Player player, PlayerView playerView) {
        boolean isLive = player.isCurrentMediaItemLive();
        TextView liveBadge = findViewById(R.id.live_badge);
        liveBadge.setVisibility(isLive ? View.VISIBLE : View.GONE);

        View position = playerView.findViewById(R.id.exo_position);
        View duration = playerView.findViewById(R.id.exo_duration);
        View progress = playerView.findViewById(R.id.exo_progress);
        int visibility = isLive ? View.GONE : View.VISIBLE;
        if (position != null) position.setVisibility(visibility);
        if (duration != null) duration.setVisibility(visibility);
        if (progress != null) progress.setVisibility(visibility);
    }
}
