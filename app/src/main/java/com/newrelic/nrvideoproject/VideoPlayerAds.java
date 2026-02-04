package com.newrelic.nrvideoproject;

import android.net.Uri;
import android.os.Bundle;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoPlayerConfiguration;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ima.ImaAdsLoader;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import android.util.Log;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.ima.tracker.NRTrackerIMA;

/**
 * @OptIn is required for Media3 IMA ads integration APIs which are marked as @UnstableApi.
 * This is by design as per Google's Media3 documentation.
 * @see <a href="https://developer.android.com/media/media3/exoplayer/customization#unstable-api">Media3 Unstable API Documentation</a>
 */
@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerAds extends AppCompatActivity implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    private ExoPlayer player;
    private Integer trackerId;
    private ImaAdsLoader adsLoader;
    private PlayerView playerView;
    private com.newrelic.videoagent.ima.tracker.NRTrackerIMA adTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player_ads);

        playerView = findViewById(R.id.player);

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
        // Set up the factory for media sources, passing the ads loader and ad view providers.
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
        mediaSourceFactory.setAdsLoaderProvider(unusedAdTagUri -> adsLoader);
        mediaSourceFactory.setAdViewProvider(playerView);

        player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
        NRVideoPlayerConfiguration playerConfiguration = new NRVideoPlayerConfiguration("test-player-something-else", player, true, null);
        trackerId = NRVideo.addPlayer(playerConfiguration);
        adTracker = (NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId);
        // Get the ad tracker before building the loader
        // Set ad event and error listeners on the builder
        ImaAdsLoader.Builder builder = new ImaAdsLoader.Builder(this);
        builder.setAdErrorListener(this);
        builder.setAdEventListener(this);
//          OR
//        builder.setAdErrorListener(adTracker.getAdErrorListener());
//        builder.setAdEventListener(adTracker.getAdEventListener());
        adsLoader = builder.build();
        playerView.setPlayer(player);
        adsLoader.setPlayer(player);

        // Create the MediaItem to play, specifying the content URI and ad tag URI.
        Uri contentUri = Uri.parse(videoUrl);
        Uri adTagUri = Uri.parse(getString(R.string.ad_tag_url));
        MediaItem mediaItem = new MediaItem.Builder().setUri(contentUri).setAdTagUri(adTagUri).build();
        // Prepare the content and ad to be played with the ExoPlayer.
        player.setMediaItem(mediaItem);
        // Set PlayWhenReady. If true, content and ads will autoplay.
        player.setPlayWhenReady(true);
        player.prepare();
    }

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        if (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) != null) {
            ((NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId)).handleAdError(adErrorEvent);
        }
    }

    //AdEvent.AdEventListener
    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) != null) {
            ((NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId)).handleAdEvent(adEvent);
        }
    }
}
