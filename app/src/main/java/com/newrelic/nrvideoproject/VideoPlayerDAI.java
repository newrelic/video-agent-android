package com.newrelic.nrvideoproject;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionMediaSource;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionUriBuilder;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.newrelic.videoagent.core.NRAdConfig;
import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoPlayerConfiguration;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.ima.tracker.NRTrackerIMA;

import java.util.HashMap;
import java.util.Map;

/**
 * Google IMA DAI (dynamic ad insertion / SSAI) sample.
 *
 * <p>Wiring: {@link NRAdConfig#dai()} on the player configuration, then forward the IMA DAI
 * AdEvent / AdErrorEvent callbacks to {@link NRTrackerIMA#handleStreamAdEvent} /
 * {@link NRTrackerIMA#handleStreamAdError}. Media3's {@code ImaServerSideAdInsertionMediaSource}
 * owns the StreamManager and does not expose it, so the Media3 builder listeners are the
 * entry point here. Apps that drive the IMA DAI SDK directly can instead call
 * {@link NRTrackerIMA#registerStreamManager}.</p>
 *
 * <p>Stream keys are Google's public DAI samples (video id "tears-of-steel", live asset key
 * "c-rArva4ShKVIAkNfy6HUQ"). Intent extra "video": "Tears" (VOD), "Live", "BadKey"
 * or the "Kite" button (invalid asset key, produces AD_ERROR). Anything else plays the VOD sample.</p>
 */
@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerDAI extends AppCompatActivity
        implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener {

    private static final String TAG = "VideoPlayerDAI";
    private static final String VOD_CONTENT_SOURCE_ID = "2548831";
    private static final String VOD_VIDEO_ID = "tears-of-steel";
    private static final String LIVE_ASSET_KEY = "c-rArva4ShKVIAkNfy6HUQ";

    private ExoPlayer player;
    private ImaServerSideAdInsertionMediaSource.AdsLoader adsLoader;
    private Integer trackerId;
    private NRTrackerIMA adTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player_dai);
        PlayerView playerView = findViewById(R.id.player);

        String video = getIntent().getStringExtra("video");
        ImaServerSideAdInsertionUriBuilder uriBuilder = new ImaServerSideAdInsertionUriBuilder();
        if ("Live".equals(video)) {
            uriBuilder.setAssetKey(LIVE_ASSET_KEY).setFormat(C.CONTENT_TYPE_HLS);
        } else if ("BadKey".equals(video) || "Kite".equals(video)) {
            uriBuilder.setAssetKey("invalid-asset-key").setFormat(C.CONTENT_TYPE_HLS);
        } else {
            uriBuilder.setContentSourceId(VOD_CONTENT_SOURCE_ID).setVideoId(VOD_VIDEO_ID)
                    .setFormat(C.CONTENT_TYPE_HLS);
        }
        Uri streamUri = uriBuilder.build();

        adsLoader = new ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(this, playerView)
                .setAdEventListener(this)
                .setAdErrorListener(this)
                .build();
        DefaultMediaSourceFactory contentFactory =
                new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this));
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                        new ImaServerSideAdInsertionMediaSource.Factory(adsLoader, contentFactory))
                .build();
        playerView.setPlayer(player);
        adsLoader.setPlayer(player);

        // Same lifecycle as every other agent integration; only the ad config differs.
        Map<String, Object> custom = new HashMap<>();
        String testRun = getIntent().getStringExtra("testRun");
        custom.put("testRun", testRun != null ? testRun : "dai-sample");
        trackerId = NRVideo.addPlayer(new NRVideoPlayerConfiguration(
                "dai-player", player, NRVideoPlayerConfiguration.PLAYER_TYPE_EXO,
                NRAdConfig.dai(), custom));
        adTracker = (NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId);

        player.setMediaItem(MediaItem.fromUri(streamUri));
        player.setPlayWhenReady(true);
        player.prepare();
    }

    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (adTracker != null) adTracker.handleStreamAdEvent(adEvent);
    }

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        Log.w(TAG, "DAI ad error: " + adErrorEvent.getError());
        if (adTracker != null) adTracker.handleStreamAdError(adErrorEvent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trackerId != null) NRVideo.releaseTracker(trackerId);
        if (adsLoader != null) adsLoader.release();
        if (player != null) player.release();
    }
}
