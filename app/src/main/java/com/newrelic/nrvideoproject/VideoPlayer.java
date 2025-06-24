package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
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
            NRLog.d("VideoPlayer: Play Tears"); // Updated log call
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else if (video.equals("Playhouse")) {
            NRLog.d("VideoPlayer: Play Playhouse"); // Updated log call
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else if (video.equals("Kite")) {
            NRLog.d("VideoPlayer: Play Kite"); // Updated log call
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else if (video.equals("Live")) {
            NRLog.d("VideoPlayer: Play Live"); // Updated log call
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else {
            NRLog.d("VideoPlayer: Unknown video selected"); // Updated log call
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensure the tracker is correctly cast before calling sendEnd, and then release it.
        // It's assumed that NRVideoTracker's dispose and sendEnd methods are updated to use
        // the standalone VideoAnalyticsController and VideoHarvest for data flushing.
        if (trackerId != null) {
            NRVideoTracker videoTracker = (NRVideoTracker) NewRelicVideoAgent.getInstance().getContentTracker(trackerId);
            if (videoTracker != null) {
                videoTracker.sendEnd(); // Trigger final event before release
            }
            NewRelicVideoAgent.getInstance().releaseTracker(trackerId);
            NRLog.d("VideoPlayer: Tracker with ID " + trackerId + " released on onDestroy."); // Updated log call
        }

        // Optionally, trigger an immediate harvest if you want to ensure data is sent on exit.
        // This might be crucial for ensuring all events from a short session are captured.
        // VideoHarvest.harvestNow(true); // Blocking call, might delay app exit slightly.
        // Or non-blocking:
        // VideoHarvest.harvestNow();
    }

    private void playVideo(String videoUrl) {
        player = new ExoPlayer.Builder(this).build();

        NRTrackerExoPlayer tracker = new NRTrackerExoPlayer();

        // These setAttribute calls should internally route to VideoAnalyticsController
        // via the NRVideoTracker base class.
        tracker.setAttribute("customAttr", 12345, "CUSTOM_ACTION");

        tracker.setAttribute("contentTitle", "This is my test title", "CONTENT_START");
        tracker.setAttribute("contentIsLive", true, "CONTENT_START");
        tracker.setAttribute("myCustomAttr", "any value", "CONTENT_START");

        trackerId = NewRelicVideoAgent.getInstance().start(tracker);

        // Set the user ID through the standalone agent.
        NewRelicVideoAgent.getInstance().setUserId("your_user_id");

        Map<String, Object> attr = new HashMap<>();
        attr.put("myAttrStr", "Hello");
        attr.put("myAttrInt", 101);

        // These sendEvent calls should internally route to VideoAnalyticsController
        // via the NRVideoTracker base class.
        tracker.sendEvent("CUSTOM_ACTION", attr);
        tracker.sendEvent("CUSTOM_ACTION_2", attr);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        tracker.setPlayer(player);

        // Set the playlist URIs
        List<Uri> uris = new ArrayList<>();
        uris.add(Uri.parse(videoUrl));
        tracker.setSrc(uris);

        player.setMediaItem(MediaItem.fromUri(videoUrl));
        // Prepare the player.
        player.setPlayWhenReady(true);
        player.prepare();

        NRLog.d("VideoPlayer: Started playing video from: " + videoUrl); // Updated log call
    }
}