package com.newrelic.videotest;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.MediaRouteButton;
import android.view.View;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.common.images.WebImage;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.NewRelicVideoAgent;
import com.newrelic.videoagent.trackers.Exo2TrackerBuilder;

import java.util.ArrayList;
import java.util.List;

public class Exo2Activity extends AppCompatActivity {

    private SimpleExoPlayer player;
    private CastPlayer castPlayer;
    private Long trackerID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exo2);

        NRLog.enable();

        //setupPlayer();
        setupPlayerWithPlaylist();
        //setupPlayerWithHLSMediaSource();

        //setupCastMediaQueue();
    }

    private void setupCastMediaQueue() {

        // Hide mobile player
        PlayerView playerView = findViewById(R.id.player);
        playerView.setVisibility(View.INVISIBLE);

        // Setup cast button

        MediaRouteButton mMediaRouteButton = findViewById(R.id.media_route_button);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), mMediaRouteButton);
        CastContext mCastContext = CastContext.getSharedInstance(this);

        if(mCastContext.getCastState() != CastState.NO_DEVICES_AVAILABLE)
            mMediaRouteButton.setVisibility(View.VISIBLE);

        mCastContext.addCastStateListener((int state) -> {
            if (state == CastState.NO_DEVICES_AVAILABLE)
                mMediaRouteButton.setVisibility(View.GONE);
            else {
                if (mMediaRouteButton.getVisibility() == View.GONE)
                    mMediaRouteButton.setVisibility(View.VISIBLE);
            }
        });

        String videoUrl = getString(R.string.videoURL_jelly);

        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        movieMetadata.putString(MediaMetadata.KEY_TITLE, "NRVideoAgent Exo2 Cast Demo");
        movieMetadata.addImage(new WebImage(Uri.parse("https://newrelic.com/assets/newrelic/source/NewRelic-logo-square.png")));
        MediaInfo mediaInfo = new MediaInfo.Builder(videoUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(MimeTypes.VIDEO_UNKNOWN)
                .setMetadata(movieMetadata).build();

        final MediaQueueItem[] mediaItems = {new MediaQueueItem.Builder(mediaInfo).build()};

        // Setup Cast Player

        castPlayer = new CastPlayer(mCastContext);
        castPlayer.setSessionAvailabilityListener(new CastPlayer.SessionAvailabilityListener() {
            @Override
            public void onCastSessionAvailable() {
                castPlayer.loadItems(mediaItems, 0, 0, Player.REPEAT_MODE_OFF);
            }

            @Override
            public void onCastSessionUnavailable() {
            }
        });

        // Setup video agent for CastPlayer
        trackerID = NewRelicVideoAgent.start(castPlayer, Uri.parse(videoUrl), Exo2TrackerBuilder.class);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NewRelicVideoAgent.releaseTracker(trackerID);
        player.release();
    }

    private void setupPlayer() {

        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory videoTrackSelectionFactory =  new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =  new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoTestApp"));

        Uri videoUri = Uri.parse(getString(R.string.videoURL_dolby));

        MediaSource videoSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(videoUri);

        trackerID = NewRelicVideoAgent.start(player, videoUri, Exo2TrackerBuilder.class);

        player.setPlayWhenReady(true);
        player.prepare(videoSource);
    }

    private void setupPlayerWithPlaylist() {

        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory videoTrackSelectionFactory =  new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =  new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoTestApp"));

        List<Uri> playlistUri = new ArrayList<>();

        playlistUri.add(Uri.parse(getString(R.string.videoURL_bunny)));
        playlistUri.add(Uri.parse(getString(R.string.videoURL_dolby)));
        playlistUri.add(Uri.parse(getString(R.string.videoURL_jelly)));

        /*
        playlistUri.add(Uri.parse(getString(R.string.videoURL_dolby_local)));
        playlistUri.add(Uri.parse(getString(R.string.videoURL_bunny_local)));
        playlistUri.add(Uri.parse(getString(R.string.videoURL_jelly_local)));
        */

        /*
        playlistUri.add(Uri.parse(getString(R.string.videoURL_cgi_dolby)));
        playlistUri.add(Uri.parse(getString(R.string.videoURL_cgi_bunny)));
        playlistUri.add(Uri.parse(getString(R.string.videoURL_cgi_jelly)));
        */

        MediaSource mediaSourceArray[] = new MediaSource[playlistUri.size()];

        for (int i = 0 ; i < playlistUri.size() ; i++) {
            MediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(playlistUri.get(i));
            mediaSourceArray[i] = mediaSource;
        }

        ConcatenatingMediaSource concatenatedSource = new ConcatenatingMediaSource(mediaSourceArray);

        trackerID = NewRelicVideoAgent.start(player, playlistUri, Exo2TrackerBuilder.class);
        NewRelicVideoAgent.getContentsTracker(trackerID).disableHeartbeat();
        //trackerID = NewRelicVideoAgent.start(player, Exo2TrackerBuilder.class);

        player.prepare(concatenatedSource);
        player.setPlayWhenReady(true);
    }

    private void setupPlayerWithHLSMediaSource() {
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory videoTrackSelectionFactory =  new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =  new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoTestApp"));

        List<Uri> playlistUri = new ArrayList<>();

        playlistUri.add(Uri.parse(getString(R.string.videoURL_cgi_star_hls)));
        playlistUri.add(Uri.parse(getString(R.string.videoURL_cgi_jelly_hls)));

        MediaSource mediaSourceArray[] = new MediaSource[playlistUri.size()];

        for (int i = 0 ; i < playlistUri.size() ; i++) {
            MediaSource mediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(playlistUri.get(i));
            mediaSourceArray[i] = mediaSource;
        }

        ConcatenatingMediaSource concatenatedSource = new ConcatenatingMediaSource(mediaSourceArray);

        trackerID = NewRelicVideoAgent.start(player, playlistUri, Exo2TrackerBuilder.class);

        player.prepare(concatenatedSource);
        player.setPlayWhenReady(true);
    }
}
