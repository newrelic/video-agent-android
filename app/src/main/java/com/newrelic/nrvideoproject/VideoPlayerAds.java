package com.newrelic.nrvideoproject;

import android.net.Uri;
import android.os.Bundle;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer;
import com.newrelic.videoagent.ima.tracker.NRTrackerIMA;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSourceFactory;
import androidx.media3.exoplayer.SimpleExoPlayer;
import androidx.media3.exoplayer.ima.ImaAdsLoader;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import android.util.Log;

public class VideoPlayerAds extends AppCompatActivity implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    private SimpleExoPlayer player;
    private Integer trackerId;
    private ImaAdsLoader adsLoader;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player_ads);

        playerView = findViewById(R.id.player);

        String video = getIntent().getStringExtra("video");

        if (video.equals("Tears")) {
            Log.v("VideoPlayer", "Play Tears");
            playVideo("http://www.bok.net/dash/tears_of_steel/cleartext/stream.mpd");
        }
        else if (video.equals("Playhouse")) {
            Log.v("VideoPlayer", "Play Playhouse");
            playVideo("https://bitmovin-a.akamaihd.net/content/playhouse-vr/mpds/105560.mpd");
        }
        else if (video.equals("Kite")) {
            Log.v("VideoPlayer", "Play Kite");
            playVideo("https://demos.transloadit.com/dashtest/my_playlist.mpd");
        }
        else if (video.equals("Live")) {
            Log.v("VideoPlayer", "Play Live");
            playVideo("https://livesim.dashif.org/livesim/testpic_2s/Manifest.mpd");
        }
        else {
            Log.v("VideoPlayer","Unknown video");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ((NRVideoTracker) NewRelicVideoAgent.getInstance().getContentTracker(trackerId)).sendEnd();
        NewRelicVideoAgent.getInstance().releaseTracker(trackerId);
        player.stop();
    }

    private void playVideo(String videoUrl) {
        // Init trackers
        NRTrackerExoPlayer tracker = new NRTrackerExoPlayer();
        NRTrackerIMA adsTracker = new NRTrackerIMA();
        trackerId = NewRelicVideoAgent.getInstance().start(tracker, adsTracker);

        ImaAdsLoader.Builder builder = new ImaAdsLoader.Builder(this);
        // NOTE: The NRTrackerIMA instance can be used as a listener for both AdErrorEvent and AdEvent.
        builder.setAdErrorListener(this);
        builder.setAdEventListener(this);
        adsLoader = builder.build();

        // Set up the factory for media sources, passing the ads loader and ad view providers.
        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, getString(R.string.app_name)));
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
        mediaSourceFactory.setAdsLoaderProvider(unusedAdTagUri -> adsLoader);
        mediaSourceFactory.setAdViewProvider(playerView);

        // Create a SimpleExoPlayer and set it as the player for content and ads.
        player = new SimpleExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
        playerView.setPlayer(player);
        adsLoader.setPlayer(player);

        // Set the user ID
        NewRelicVideoAgent.getInstance().setUserId("your_user_id");

        // Pass the player to the content tracker
        tracker.setPlayer(player);

        // Create the MediaItem to play, specifying the content URI and ad tag URI.
        Uri contentUri = Uri.parse(videoUrl);
        Uri adTagUri = Uri.parse(getString(R.string.ad_tag_url));
        MediaItem mediaItem = new MediaItem.Builder().setUri(contentUri).setAdTagUri(adTagUri).build();

        // Prepare the content and ad to be played with the SimpleExoPlayer.
        player.setMediaItem(mediaItem);
        // Set PlayWhenReady. If true, content and ads will autoplay.
        player.setPlayWhenReady(true);
        player.prepare();
    }

    //AdErrorEvent.AdErrorListener
    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        if (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) != null) {
            ((NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId)).onAdError(adErrorEvent);
        }
    }

    //AdEvent.AdEventListener
    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) != null) {
            ((NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId)).onAdEvent(adEvent);
        }
    }
}