package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer;

public class VideoPlayer extends AppCompatActivity {

    private SimpleExoPlayer player;
    private Integer trackerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        String video = getIntent().getStringExtra("video");

        if (video.equals("Tears")) {
            Log.v("VideoPlayer", "Play Tears");
            playDash("http://www.bok.net/dash/tears_of_steel/cleartext/stream.mpd");
        }
        else if (video.equals("Playhouse")) {
            Log.v("VideoPlayer", "Play Playhouse");
            playDash("https://bitmovin-a.akamaihd.net/content/playhouse-vr/mpds/105560.mpd");
        }
        else if (video.equals("Kite")) {
            Log.v("VideoPlayer", "Play Kite");
            playDash("https://demos.transloadit.com/dashtest/my_playlist.mpd");
        }
        else {
            Log.v("VideoPlayer","Unknown video!!");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ((NRVideoTracker)NewRelicVideoAgent.getInstance().getContentTracker(trackerId)).sendEnd();
        NewRelicVideoAgent.getInstance().releaseTracker(trackerId);
    }

    private void playDash(String videoUrl) {
        player = new SimpleExoPlayer.Builder(this).build();

        trackerId = NewRelicVideoAgent.getInstance().start(new NRTrackerExoPlayer(player));

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        player.setMediaItem(MediaItem.fromUri(videoUrl));
        // Prepare the player.
        player.setPlayWhenReady(true);
        player.prepare();

        /*
        NRVideoTracker tracker = new NRVideoTracker();
        NRVideoTracker adTracker = new NRVideoTracker();
        trackerId = NewRelicVideoAgent.getInstance().start(tracker, adTracker);

        tracker.setAttribute("generic", "Per tots");
        tracker.setAttribute("contEnd", "Per CONTENT_END", "^CONTENT_END$");
        tracker.setAttribute("anyEnd", "Per *_END", "^[A-Z_]+_END$");

        tracker.setHeartbeatTime(5);
        tracker.sendRequest();
        tracker.sendStart();
        tracker.sendBufferStart();
        tracker.sendBufferEnd();
        tracker.sendPause();
        adTracker.sendAdBreakStart();
        adTracker.sendRequest();
        adTracker.sendStart();
        adTracker.sendEnd();
        adTracker.sendRequest();
        adTracker.sendStart();
        adTracker.sendEnd();
        adTracker.sendAdBreakEnd();
        tracker.sendResume();
         */
    }
}