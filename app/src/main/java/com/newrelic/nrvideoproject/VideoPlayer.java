package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
    private boolean isStressingCPU = false;

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
        NRVideo.releaseTracker(trackerId);
        player.stop();
    }

    private void playVideo(String videoUrl) {
        //player = new ExoPlayer.Builder(this).build();
        //Create ExoPlayer with custom configuration for easier frame drop detection
        androidx.media3.exoplayer.DefaultLoadControl loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        500,   // Min buffer (very low to stress system)
                        2000,  // Max buffer (low to force frequent loading)
                        250,   // Buffer for playback
                        500    // Buffer for playback after rebuffer
                )
                .build();

        // Use default renderers factory without the problematic operation mode
        androidx.media3.exoplayer.DefaultRenderersFactory renderersFactory =
                new androidx.media3.exoplayer.DefaultRenderersFactory(this);

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setRenderersFactory(renderersFactory)
                .build();
        Log.d("VideoPlayer", "ExoPlayer created with LOW BUFFER settings for frame drop detection: " + player.getClass().getSimpleName());

        Map<String, Object> customAttr = new HashMap<>();
        customAttr.put("something", "This is my test title");
        customAttr.put("myAttrStr", "Hello");
        customAttr.put("myAttrInt", 101);
        customAttr.put("name", "nr-video-agent-android-01-24JUL-john-starc");
        NRVideoPlayerConfiguration playerConfiguration = new NRVideoPlayerConfiguration("test-player", player, false, customAttr);
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

        // Add button to trigger frame drops for testing
        setupFrameDropTestButton();
    }
    private void setupFrameDropTestButton() {
        Button frameDropButton = findViewById(R.id.button_frame_drop_attack);
        if (frameDropButton != null) {
            frameDropButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ManualFrameDropAttack();
                }
            });
        }
    }
    private void ManualFrameDropAttack() {
        // Attack 1: Saturate ALL threads with MAXIMUM priority
        int cores = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < cores * 4; i++) { // 4x oversaturate
            new Thread(new Runnable() {
                @Override
                public void run() {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    while (isStressingCPU) {
                        // Most CPU-intensive operations possible
                        for (int j = 0; j < 10000000; j++) { // 10 million iterations
                            double result = Math.pow(Math.sin(j), Math.cos(j)) * Math.sqrt(j * Math.PI) * Math.log(j + 1);
                        }
                    }
                }
            }).start();
        }

        // Attack 2: Memory allocation bomb
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<byte[]> memoryBomb = new ArrayList<>();
                    for (int i = 0; i < 1000; i++) {
                        memoryBomb.add(new byte[50 * 1024 * 1024]); // 50MB chunks
                        if (i % 5 == 0) System.gc(); // Frequent GC
                    }
                } catch (OutOfMemoryError e) {
                    Log.d("VideoPlayer", "Memory bomb successful - OOM triggered");
                }
            }
        }).start();

        // Attack 3: Main thread destruction
        brutalMainThreadBlocking();

        isStressingCPU = true;

        // Stop after 20 seconds
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                isStressingCPU = false;
            }
        }, 20000);
    }
    private void brutalMainThreadBlocking() {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final Runnable blocker = new Runnable() {
            int iteration = 0;

            @Override
            public void run() {
                if (iteration < 100) {
                    // Block main thread for 200ms
                    long startTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - startTime < 200) {
                        // Intensive work on main thread
                        for (int i = 0; i < 1000000; i++) {
                            Math.sin(i * Math.PI);
                        }
                    }

                    iteration++;
                    Log.d("VideoPlayer", "Main thread blocked for 200ms - iteration " + iteration);

                    // Block again in 100ms
                    mainHandler.postDelayed(this, 100);
                }
            }
        };
        mainHandler.post(blocker);
    }
}
