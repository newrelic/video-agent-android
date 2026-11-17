package com.newrelic.nrvideoproject;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
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

/**
 * Google IMA Dynamic Ad Insertion (DAI) with Media3: the ads are stitched into the stream.
 * The tracker only observes. The app keeps its own IMA listeners and forwards the events and
 * errors, selecting the tracker with {@link NRAdConfig#imaDai()}. "Live" plays a live
 * stream, any other button plays VOD.
 *
 * @OptIn is required for the Media3 IMA APIs, which are marked as @UnstableApi.
 */
@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerDai extends AppCompatActivity implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    // Google's public DAI sample streams.
    private static final String VOD_NETWORK_CODE = "21775744923";
    private static final String VOD_CONTENT_SOURCE_ID = "2548831";
    private static final String VOD_VIDEO_ID = "tears-of-steel";
    private static final String LIVE_ASSET_KEY = "c-rArva4ShKVIAkNfy6HUQ";

    private ExoPlayer player;
    private Integer trackerId;
    private ImaServerSideAdInsertionMediaSource.AdsLoader adsLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player_ads);
        PlayerView playerView = findViewById(R.id.player);
        boolean live = "Live".equals(getIntent().getStringExtra("video"));

        adsLoader = new ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(this, playerView)
                .setAdEventListener(this)
                .setAdErrorListener(this)
                .build();
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                .setServerSideAdInsertionMediaSourceFactory(
                        new ImaServerSideAdInsertionMediaSource.Factory(
                                adsLoader, new DefaultMediaSourceFactory(this)));
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
        playerView.setPlayer(player);
        adsLoader.setPlayer(player);

        NRVideoPlayerConfiguration playerConfiguration =
                new NRVideoPlayerConfiguration("test-player-dai", player, NRAdConfig.imaDai(), null);
        trackerId = NRVideo.addPlayer(playerConfiguration);

        ImaServerSideAdInsertionUriBuilder uriBuilder = new ImaServerSideAdInsertionUriBuilder()
                .setFormat(C.CONTENT_TYPE_HLS);
        if (live) {
            uriBuilder.setAssetKey(LIVE_ASSET_KEY);
        } else {
            uriBuilder.setContentSourceId(VOD_CONTENT_SOURCE_ID).setVideoId(VOD_VIDEO_ID)
                    .setNetworkCode(VOD_NETWORK_CODE);
        }
        Uri uri = uriBuilder.build();
        player.setMediaItem(MediaItem.fromUri(uri));
        player.setPlayWhenReady(true);
        player.prepare();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NRVideo.releaseTracker(trackerId);
        adsLoader.setPlayer(null);
        adsLoader.release();
        player.release();
    }

    // AdErrorEvent.AdErrorListener
    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        NRTrackerIMA tracker = (NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId);
        if (tracker != null) {
            tracker.handleAdError(adErrorEvent);
        }
    }

    // AdEvent.AdEventListener
    @Override
    public void onAdEvent(AdEvent adEvent) {
        NRTrackerIMA tracker = (NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId);
        if (tracker != null) {
            tracker.handleAdEvent(adEvent);
        }
    }
}
