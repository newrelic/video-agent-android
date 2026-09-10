package com.newrelic.nrvideoproject;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoPlayerConfiguration;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.core.tracker.NRTracker;
import com.newrelic.videoagent.theoplayer.tracker.NRTrackerTHEOPlayer;
import java.util.HashMap;
import java.util.Map;

import com.theoplayer.android.api.THEOplayerView;
import com.theoplayer.android.api.event.player.PlayerEventTypes;
import com.theoplayer.android.api.source.SourceDescription;
import com.theoplayer.android.api.source.TypedSource;
import com.theoplayer.android.api.source.drm.DRMConfiguration;
import com.theoplayer.android.api.source.drm.KeySystemConfiguration;

public class VideoPlayerTHEO extends AppCompatActivity {

    private static final String TAG = "VideoPlayerTHEO";

    // Apple HLS VOD test stream — reliable on emulator (Cronet trusts Apple CDN certs)
    private static final String STREAM_HLS_VOD =
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8";
    // DASHIF live simulator — reliable live DASH stream
    private static final String STREAM_DASH_LIVE =
            "https://livesim2.dashif.org/livesim2/ato_10/testpic3_2s/Manifest.mpd";
    // Same HLS VOD with a fake token query param — used to verify obfuscation masks token=REDACTED
    private static final String STREAM_HLS_TOKEN =
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8?token=secret12345&quality=high";
    // Bitmovin public Widevine-protected DASH stream — used to trigger CONTENTPROTECTIONERROR.
    // A deliberately wrong license URL causes DRM acquisition to fail without needing a real license.
    private static final String STREAM_DASH_DRM =
            "https://bitmovin-a.akamaihd.net/content/art-of-motion_drm/mpds/11331.mpd";
    private static final String FAKE_LICENSE_URL =
            "https://httpbin.org/status/403"; // reachable but returns 403 → CONTENT_PROTECTION_LICENSE_ERROR

    private THEOplayerView theoPlayerView;
    private Integer trackerId;

    // Controls
    private SeekBar seekBar;
    private Button btnPlayPause;
    private TextView txtCurrentTime;
    private TextView txtDuration;

    // NR event panel
    private TextView txtStatus;
    private TextView txtPlayhead;
    private TextView txtLastEvent;

    private boolean isPlaying = false;
    private boolean isSeeking = false;
    private String  currentUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player_theo);

        theoPlayerView = findViewById(R.id.theo_player_view);
        seekBar        = findViewById(R.id.seek_bar);
        btnPlayPause   = findViewById(R.id.btn_play_pause);
        txtCurrentTime = findViewById(R.id.txt_current_time);
        txtDuration    = findViewById(R.id.txt_duration);
        txtStatus      = findViewById(R.id.txt_status);
        txtPlayhead    = findViewById(R.id.txt_playhead);
        txtLastEvent   = findViewById(R.id.txt_last_event);

        // Register NR tracker
        Map<String, Object> customAttr = new HashMap<>();
        customAttr.put("something", "This is my test title");
        customAttr.put("myAttrStr", "Hello");
        customAttr.put("myAttrInt", 101);
        customAttr.put("name", "nr-video-agent-android-01-24JUL-john-starc");
        trackerId = NRVideo.addPlayer(
                new NRVideoPlayerConfiguration("theo-player", theoPlayerView,
                        NRVideoPlayerConfiguration.PLAYER_TYPE_THEO, null, customAttr));

        NRVideo.setUserId("test-theo-001");
        NRVideo.setAttribute(trackerId, "playerType", "THEOplayer");
        NRVideo.setAttribute(trackerId, "appVersion", "1.1");
        NRVideo.setAttribute(trackerId, "testEnvironment", "staging");

        wireControls();
        attachPlayerListeners();

        String video = getIntent().getStringExtra("video");
        if ("Live".equals(video)) {
            loadStream(STREAM_DASH_LIVE);
        } else if ("direct".equals(video)) {
            String directUrl = getIntent().getStringExtra("direct_url");
            Log.d(TAG, "Play direct URL: " + directUrl);
            loadStream(directUrl);
        } else {
            loadStream(STREAM_HLS_VOD);
        }
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    private void wireControls() {
        btnPlayPause.setOnClickListener(v -> {
            if (isPlaying) {
                theoPlayerView.getPlayer().pause();
            } else {
                theoPlayerView.getPlayer().play();
            }
        });

        findViewById(R.id.btn_seek_back).setOnClickListener(v ->
            seekRelative(-10));

        findViewById(R.id.btn_seek_fwd).setOnClickListener(v ->
            seekRelative(10));

        Button btnMute = findViewById(R.id.btn_mute);
        btnMute.setOnClickListener(v -> {
            boolean muted = theoPlayerView.getPlayer().isMuted();
            theoPlayerView.getPlayer().setMuted(!muted);
            btnMute.setText(muted ? "🔇 Mute" : "🔊 Unmute");
            txtStatus.setText("Muted: " + !muted);
        });

        findViewById(R.id.btn_speed_half).setOnClickListener(v ->  setSpeed(0.5));
        findViewById(R.id.btn_speed_normal).setOnClickListener(v -> setSpeed(1.0));
        findViewById(R.id.btn_speed_1_5).setOnClickListener(v ->  setSpeed(1.5));
        findViewById(R.id.btn_speed_2).setOnClickListener(v ->     setSpeed(2.0));

        findViewById(R.id.btn_vod).setOnClickListener(v ->
            loadStream(STREAM_HLS_VOD));

        findViewById(R.id.btn_live).setOnClickListener(v ->
            loadStream(STREAM_DASH_LIVE));

        findViewById(R.id.btn_drm_fail).setOnClickListener(v ->
            loadDrmFailureStream());

        findViewById(R.id.btn_token_url).setOnClickListener(v ->
            loadStream(STREAM_HLS_TOKEN));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    double duration = theoPlayerView.getPlayer().getDuration();
                    if (!Double.isInfinite(duration) && duration > 0) {
                        double target = (progress / 1000.0) * duration;
                        txtCurrentTime.setText(formatTime(target));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                isSeeking = false;
                double duration = theoPlayerView.getPlayer().getDuration();
                if (!Double.isInfinite(duration) && duration > 0) {
                    double target = (bar.getProgress() / 1000.0) * duration;
                    theoPlayerView.getPlayer().setCurrentTime(target);
                }
            }
        });
    }

    private void seekRelative(int seconds) {
        double current  = theoPlayerView.getPlayer().getCurrentTime();
        double duration = theoPlayerView.getPlayer().getDuration();
        double target   = Math.max(0, current + seconds);
        if (!Double.isInfinite(duration)) {
            target = Math.min(target, duration);
        }
        theoPlayerView.getPlayer().setCurrentTime(target);
    }

    private void setSpeed(double rate) {
        theoPlayerView.getPlayer().setPlaybackRate(rate);
        txtStatus.setText("Speed: " + rate + "x");
        Log.d(TAG, "setSpeed: " + rate);
    }

    private void loadDrmFailureStream() {
        Log.d(TAG, "loadDrmFailureStream: deliberate DRM failure to verify CONTENT_ERROR");
        currentUrl = STREAM_DASH_DRM;
        TypedSource source = new TypedSource.Builder(STREAM_DASH_DRM)
                .drm(new DRMConfiguration.Builder()
                        .widevine(new KeySystemConfiguration.Builder(FAKE_LICENSE_URL).build())
                        .build())
                .build();
        theoPlayerView.getPlayer().setSource(
                new SourceDescription.Builder(source).build());
        theoPlayerView.getPlayer().setAutoplay(true);
    }

    private void loadStream(String url) {
        Log.d(TAG, "loadStream: " + url);
        currentUrl = url;
        TypedSource source = new TypedSource.Builder(url).build();
        theoPlayerView.getPlayer().setSource(
                new SourceDescription.Builder(source).build());
        theoPlayerView.getPlayer().setAutoplay(true);
    }

    // ── Player event listeners ────────────────────────────────────────────────

    private void attachPlayerListeners() {
        com.theoplayer.android.api.player.Player p = theoPlayerView.getPlayer();

        p.addEventListener(PlayerEventTypes.SOURCECHANGE, e -> {
            txtStatus.setText("Status: loading...");
            txtLastEvent.setText("NR → CONTENT_REQUEST");
            seekBar.setProgress(0);
            txtCurrentTime.setText("0:00");
            txtDuration.setText("0:00");
            // Fire after SOURCECHANGE so the NR tracker's sendRequest() has already
            // opened the new session — matches ExoPlayer's VIDEO_STARTED pattern.
            if (currentUrl != null) {
                Map<String, Object> attrs = new HashMap<>();
                attrs.put("actionName", "VIDEO_STARTED");
                attrs.put("videoUrl", currentUrl);
                attrs.put("playerType", "THEOplayer");
                NRVideo.recordCustomEvent(attrs, trackerId);
            }
        });

        p.addEventListener(PlayerEventTypes.PLAYING, e -> {
            isPlaying = true;
            btnPlayPause.setText("⏸ Pause");
            txtStatus.setText("Status: playing");
            txtLastEvent.setText("NR → CONTENT_START / CONTENT_BUFFER_END");
        });

        p.addEventListener(PlayerEventTypes.PAUSE, e -> {
            isPlaying = false;
            btnPlayPause.setText("▶ Play");
            txtStatus.setText("Status: paused");
            txtLastEvent.setText("NR → CONTENT_PAUSE");
        });

        p.addEventListener(PlayerEventTypes.PLAY, e -> {
            if (!isPlaying) {
                txtLastEvent.setText("NR → CONTENT_RESUME");
            }
        });

        p.addEventListener(PlayerEventTypes.WAITING, e -> {
            txtStatus.setText("Status: buffering...");
            txtLastEvent.setText("NR → CONTENT_BUFFER_START");
        });

        p.addEventListener(PlayerEventTypes.SEEKING, e -> {
            txtStatus.setText("Status: seeking...");
            txtLastEvent.setText("NR → CONTENT_SEEK_START");
        });

        p.addEventListener(PlayerEventTypes.SEEKED, e ->
            txtLastEvent.setText("NR → CONTENT_SEEK_END"));

        p.addEventListener(PlayerEventTypes.ENDED, e -> {
            isPlaying = false;
            btnPlayPause.setText("▶ Play");
            txtStatus.setText("Status: ended");
            txtLastEvent.setText("NR → CONTENT_END + QOE_AGGREGATE");
        });

        p.addEventListener(PlayerEventTypes.ERROR, e -> {
            com.theoplayer.android.api.error.THEOplayerException err = e.getErrorObject();
            String detail = (err != null)
                ? "code=" + err.getCode() + " msg=" + err.getMessage()
                : "unknown";
            Log.e(TAG, "PLAYER ERROR: " + detail);
            txtStatus.setText("Status: ERROR");
            txtLastEvent.setText("NR → CONTENT_ERROR: " + detail);
        });

        // TIMEUPDATE — update seek bar and playhead display
        p.addEventListener(PlayerEventTypes.TIMEUPDATE, e -> {
            double current  = p.getCurrentTime();
            double duration = p.getDuration();

            txtCurrentTime.setText(formatTime(current));

            long ms = (long)(current * 1000);
            txtPlayhead.setText(String.format("Playhead: %s (%d ms)",
                    formatTime(current), ms));

            if (!isSeeking && !Double.isInfinite(duration) && duration > 0) {
                seekBar.setProgress((int)((current / duration) * 1000));
                txtDuration.setText(formatTime(duration));
            }
        });

        p.addEventListener(PlayerEventTypes.DURATIONCHANGE, e -> {
            double duration = p.getDuration();
            if (!Double.isInfinite(duration) && duration > 0) {
                txtDuration.setText(formatTime(duration));
            } else {
                txtDuration.setText("LIVE");
            }
        });
    }

    private static String formatTime(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
            return "0:00";
        }
        int total = (int) seconds;
        return String.format("%d:%02d", total / 60, total % 60);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override protected void onResume()  { super.onResume();  if (theoPlayerView != null) theoPlayerView.onResume(); }
    @Override protected void onPause()   { super.onPause();   if (theoPlayerView != null) theoPlayerView.onPause(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        NRTracker tracker = NewRelicVideoAgent.getInstance().getContentTracker(trackerId);
        if (tracker instanceof NRTrackerTHEOPlayer) {
            ((NRTrackerTHEOPlayer) tracker).onDestroy();
        }
        NRVideo.releaseTracker(trackerId);
    }
}
