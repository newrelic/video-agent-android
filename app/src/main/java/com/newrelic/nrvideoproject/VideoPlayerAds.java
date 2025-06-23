package com.newrelic.nrvideoproject;

import android.net.Uri;
import android.os.Bundle;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.newrelic.videoagent.core.NewRelicVideoAgent;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;
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
            NRLog.d("VideoPlayerAds: Play Tears"); // Updated log call
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else if (video.equals("Playhouse")) {
            NRLog.d("VideoPlayerAds: Play Playhouse"); // Updated log call
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else if (video.equals("Kite")) {
            NRLog.d("VideoPlayerAds: Play Kite"); // Updated log call
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else if (video.equals("Live")) {
            NRLog.d("VideoPlayerAds: Play Live"); // Updated log call
            playVideo("https://turtle-tube.appspot.com/t/t2/dash.mpd");
        }
        else {
            NRLog.d("VideoPlayerAds: Unknown video selected"); // Updated log call
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensure the content tracker is correctly cast before calling sendEnd, and then release it.
        // It's assumed that NRVideoTracker's dispose and sendEnd methods are updated to use
        // the standalone VideoAnalyticsController and VideoHarvest for data flushing.
        if (trackerId != null) {
            NRVideoTracker contentTracker = (NRVideoTracker) NewRelicVideoAgent.getInstance().getContentTracker(trackerId);
            if (contentTracker != null) {
                contentTracker.sendEnd(); // Trigger final event for content tracker
            }
            // Dispose of the ads loader to release its resources
            if (adsLoader != null) {
                adsLoader.release();
            }
            NewRelicVideoAgent.getInstance().releaseTracker(trackerId); // Release the tracker pair
            NRLog.d("VideoPlayerAds: Tracker with ID " + trackerId + " released and ads loader released on onDestroy."); // Updated log call
        }
        if (player != null) {
            player.stop();
            player.release(); // Release the ExoPlayer resources
            NRLog.d("VideoPlayerAds: ExoPlayer stopped and released."); // Updated log call
        }
    }

    private void playVideo(String videoUrl) {
        // Init trackers
        NRTrackerExoPlayer tracker = new NRTrackerExoPlayer();
        NRTrackerIMA adsTracker = new NRTrackerIMA();

        // Start the NewRelicVideoAgent with both content and ads trackers
        trackerId = NewRelicVideoAgent.getInstance().start(tracker, adsTracker);
        NRLog.d("VideoPlayerAds: NewRelicVideoAgent started with content and ads trackers. Tracker ID: " + trackerId); // Updated log call

        ImaAdsLoader.Builder builder = new ImaAdsLoader.Builder(this);
        // NOTE: The NRTrackerIMA instance can be used as a listener for both AdErrorEvent and AdEvent.
        // These methods in NRTrackerIMA should internally route to VideoAnalyticsController.
        builder.setAdErrorListener(this); // This refers to VideoPlayerAds implementing the listener interface
        builder.setAdEventListener(this); // This refers to VideoPlayerAds implementing the listener interface
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

        // Set the user ID through the standalone agent.
        NewRelicVideoAgent.getInstance().setUserId("your_user_id_with_ads");
        NRLog.d("VideoPlayerAds: User ID set for ads playback."); // Updated log call

        // Pass the player to the content tracker
        tracker.setPlayer(player);

        // Create the MediaItem to play, specifying the content URI and ad tag URI.
        Uri contentUri = Uri.parse(videoUrl);
        // IMPORTANT: Ensure R.string.ad_tag_url is defined in your strings.xml
        Uri adTagUri = Uri.parse(getString(R.string.ad_tag_url));
        MediaItem mediaItem = new MediaItem.Builder().setUri(contentUri).setAdTagUri(adTagUri).build();

        // Prepare the content and ad to be played with the SimpleExoPlayer.
        player.setMediaItem(mediaItem);
        // Set PlayWhenReady. If true, content and ads will autoplay.
        player.setPlayWhenReady(true);
        player.prepare();

        NRLog.d("VideoPlayerAds: Started playing video with ads from: " + videoUrl); // Updated log call
    }

    // AdErrorEvent.AdErrorListener implementation
    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        NRLog.e("VideoPlayerAds: Ad Error occurred: " + adErrorEvent.getError().getMessage()); // Updated log call
        // Delegate the ad error event to the NRTrackerIMA instance.
        // This method in NRTrackerIMA should process the error and send relevant analytics.
        if (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) != null) {
            ((NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId)).onAdError(adErrorEvent);
        }
    }

    // AdEvent.AdEventListener implementation
    @Override
    public void onAdEvent(AdEvent adEvent) {
        NRLog.d("VideoPlayerAds: Ad Event occurred: " + adEvent.getType().name()); // Updated log call
        // Delegate the ad event to the NRTrackerIMA instance.
        // This method in NRTrackerIMA should process the event (e.g., AD_START, AD_END)
        // and send relevant analytics.
        if (NewRelicVideoAgent.getInstance().getAdTracker(trackerId) != null) {
            ((NRTrackerIMA) NewRelicVideoAgent.getInstance().getAdTracker(trackerId)).onAdEvent(adEvent);
        }
    }
}