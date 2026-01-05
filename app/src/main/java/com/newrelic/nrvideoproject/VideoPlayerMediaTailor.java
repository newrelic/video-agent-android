package com.newrelic.nrvideoproject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoPlayerConfiguration;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoPlayerMediaTailor extends AppCompatActivity {

    private ExoPlayer player;
    private Integer trackerId;

    private RadioGroup initModeGroup;
    private RadioButton radioAuto;
    private RadioButton radioManual;
    private RadioButton radioAll;
    private Button loadStreamButton;
    private TextView statusText;

    private ExecutorService executorService;
    private Handler mainHandler;

    // MediaTailor configuration
    // TODO: Replace with your actual AWS MediaTailor credentials
    private static final String CLOUDFRONT_ID = "YOUR_CLOUDFRONT_ID_HERE";
    private static final String REGION = "YOUR_AWS_REGION_HERE"; // e.g., us-east-1, ap-southeast-2
    private static final String ACCOUNT_ID = "YOUR_ACCOUNT_ID_HERE";
    private static final String PLAYBACK_CONFIG = "YOUR_PLAYBACK_CONFIG_HERE";
    private static final String BASE_URL = "https://" + CLOUDFRONT_ID + ".mediatailor." + REGION + ".amazonaws.com";

    private enum InitMode {
        AUTO, MANUAL, ALL
    }

    private InitMode currentMode = InitMode.AUTO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player_mediatailor);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Check if launched from MainActivity with autoplay
        boolean autoplay = getIntent().getBooleanExtra("autoplay", false);

        String video = getIntent().getStringExtra("video");
        Log.v("VideoPlayerMediaTailor", "Play video with AWS MediaTailor: " + video);

        // Initialize UI components
        initModeGroup = findViewById(R.id.init_mode_group);
        radioAuto = findViewById(R.id.radio_auto);
        radioManual = findViewById(R.id.radio_manual);
        radioAll = findViewById(R.id.radio_all);
        loadStreamButton = findViewById(R.id.load_stream_button);
        statusText = findViewById(R.id.status_text);

        // If launched with autoplay flag, hide controls and auto-start
        if (autoplay) {
            findViewById(R.id.controls_container).setVisibility(View.GONE);
            updateStatus("Initializing MediaTailor session...");
            initializeSessionAndPlay();
        } else {
            // Setup radio button listeners for manual mode selection
            setupModeListeners();

            // Auto mode is default - load immediately
            if (currentMode == InitMode.AUTO) {
                updateStatus("Auto mode: Loading stream...");
                initializeSessionAndPlay();
            }
        }
    }

    private void setupModeListeners() {
        initModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_auto) {
                currentMode = InitMode.AUTO;
                updateStatus("Auto mode: Stream will load automatically");
                loadStreamButton.setVisibility(View.GONE);
                initializeSessionAndPlay();
            } else if (checkedId == R.id.radio_manual) {
                currentMode = InitMode.MANUAL;
                updateStatus("Manual mode: Click button to load stream");
                loadStreamButton.setVisibility(View.VISIBLE);
                if (player != null) {
                    player.stop();
                }
            } else if (checkedId == R.id.radio_all) {
                currentMode = InitMode.ALL;
                updateStatus("All mode: Supports both auto and manual initialization. Button available for manual control.");
                loadStreamButton.setVisibility(View.VISIBLE);
                initializeSessionAndPlay();
            }
        });

        loadStreamButton.setOnClickListener(v -> {
            updateStatus("Loading stream...");
            initializeSessionAndPlay();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trackerId != null) {
            NRVideo.releaseTracker(trackerId);
        }
        if (player != null) {
            player.stop();
            player.release();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void updateStatus(String message) {
        updateStatus(message, false);
    }

    private void updateStatus(String message, boolean isError) {
        mainHandler.post(() -> {
            statusText.setText(message);
            if (isError) {
                statusText.setBackgroundColor(Color.parseColor("#F8D7DA"));
                statusText.setTextColor(Color.parseColor("#721C24"));
            } else {
                statusText.setBackgroundColor(Color.parseColor("#F8F9FA"));
                statusText.setTextColor(Color.parseColor("#495057"));
            }
        });
        Log.d("VideoPlayerMediaTailor", message);
    }

    /**
     * Initialize MediaTailor session and play video with sessionId
     * This follows the pattern from the videojs sample
     */
    private void initializeSessionAndPlay() {
        executorService.execute(() -> {
            try {
                updateStatus("Initializing session...");

                // Call MediaTailor session initialization endpoint (using HLS since origin is HLS)
                String sessionEndpoint = BASE_URL + "/v1/session/" + ACCOUNT_ID + "/" + PLAYBACK_CONFIG + "/hls";
                Log.d("VideoPlayerMediaTailor", "Session endpoint: " + sessionEndpoint);

                URL url = new URL(sessionEndpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Send empty JSON body
                OutputStream os = connection.getOutputStream();
                os.write("{}".getBytes());
                os.flush();
                os.close();

                int responseCode = connection.getResponseCode();
                Log.d("VideoPlayerMediaTailor", "Session init response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String manifestUrl = jsonResponse.getString("manifestUrl");
                    Log.d("VideoPlayerMediaTailor", "Manifest URL: " + manifestUrl);

                    // Extract sessionId from manifestUrl
                    Pattern pattern = Pattern.compile("aws\\.sessionId=([^&]+)");
                    Matcher matcher = pattern.matcher(manifestUrl);

                    if (matcher.find()) {
                        String sessionId = matcher.group(1);
                        Log.d("VideoPlayerMediaTailor", "Extracted sessionId: " + sessionId);

                        // Construct HLS URL with sessionId (following videoJS pattern)
                        // Use master.m3u8 path and append sessionId as query parameter
                        String hlsUrl = BASE_URL + "/v1/master/" + ACCOUNT_ID + "/" + PLAYBACK_CONFIG + "/master.m3u8?aws.sessionId=" + sessionId;
                        Log.d("VideoPlayerMediaTailor", "HLS URL with sessionId: " + hlsUrl);

                        updateStatus("Session initialized! Loading stream...");

                        // Play video on main thread
                        mainHandler.post(() -> playVideo(hlsUrl, sessionId));
                    } else {
                        throw new Exception("Failed to extract sessionId from manifest URL");
                    }
                } else {
                    throw new Exception("Session init failed with response code: " + responseCode);
                }

                connection.disconnect();

            } catch (Exception e) {
                Log.e("VideoPlayerMediaTailor", "Session initialization error", e);
                updateStatus("Session init error: " + e.getMessage(), true);
            }
        });
    }

    /**
     * Play video with the given URL and sessionId
     */
    private void playVideo(String videoUrl, String sessionId) {
        updateStatus("Initializing player...");

        // Clean up existing player if any
        if (player != null) {
            if (trackerId != null) {
                NRVideo.releaseTracker(trackerId);
                trackerId = null;
            }
            player.stop();
            player.release();
            player = null;
        }

        // Create a DataSource.Factory for network access
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

        // Create a MediaSource factory with DASH and HLS support
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this);

        // Build ExoPlayer with media source factory
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();

        // IMPORTANT: Set media item BEFORE NRVideo.addPlayer() so MediaTailor detection can work
        // The detection checks player.getCurrentMediaItem().uri for ".mediatailor." pattern
        player.setMediaItem(MediaItem.fromUri(videoUrl));

        NRVideoPlayerConfiguration playerConfiguration = new NRVideoPlayerConfiguration("mediatailor-player", player, false, null);
        trackerId = NRVideo.addPlayer(playerConfiguration);

        Log.d("VideoPlayerMediaTailor", "Player configured with dual trackers (ExoPlayer + MediaTailor) and sessionId: " + sessionId);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        updateStatus("Loading HLS stream...");

        // MediaTailor provides an HLS manifest with ads stitched in
        player.setPlayWhenReady(true);  // Autoplay
        player.prepare();
    }
}
