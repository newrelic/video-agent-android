package com.newrelic.videotest;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
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
import com.google.android.exoplayer2.util.Util;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.NewRelicVideoAgent;
import com.newrelic.videoagent.trackers.Exo2TrackerBuilder;

import java.util.ArrayList;
import java.util.List;


public class Exo2Activity extends AppCompatActivity {

    private SimpleExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exo2);

        NRLog.enable();

        //setupPlayer();
        setupPlayerWithPlaylist();
        //setupPlayerWithHLSMediaSource();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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

        NewRelicVideoAgent.start(player, videoUri, Exo2TrackerBuilder.class);

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

        NewRelicVideoAgent.start(player, playlistUri, Exo2TrackerBuilder.class);

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

        NewRelicVideoAgent.start(player, playlistUri, Exo2TrackerBuilder.class);

        player.prepare(concatenatedSource);
        player.setPlayWhenReady(true);
    }
}
