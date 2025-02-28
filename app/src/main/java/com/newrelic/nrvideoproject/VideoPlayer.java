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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ((NRVideoTracker)NewRelicVideoAgent.getInstance().getContentTracker(trackerId)).sendEnd();
        NewRelicVideoAgent.getInstance().releaseTracker(trackerId);
    }

    private void playVideo(String videoUrl) {
        player = new ExoPlayer.Builder(this).build();

        NRTrackerExoPlayer tracker = new NRTrackerExoPlayer();

        tracker.setAttribute("customAttr", 12345, "CUSTOM_ACTION");

        tracker.setAttribute("contentTitle", "This is my test title", "CONTENT_START");
        tracker.setAttribute("contentIsLive", true, "CONTENT_START");
        tracker.setAttribute("myCustomAttr", "any value", "CONTENT_START");
        // Uncomment below to set userId
        tracker.setUserId("myUserId");

        trackerId = NewRelicVideoAgent.getInstance().start(tracker);

        // Set the user ID
        NewRelicVideoAgent.getInstance().setUserId("your_user_id");

        Map<String, Object> attr = new HashMap<>();
        attr.put("myAttrStr", "Hello");
        attr.put("myAttrInt", 101);
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
    }
}