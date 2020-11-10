package com.newrelic.videotest;

import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.MediaRouteButton;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
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
import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.jni.swig.CoreTrackerState;
import com.newrelic.videoagent.trackers.Exo2TrackerBuilder;
import com.newrelic.videoagent.trackers.ExoPlayer2ContentsTracker;

import java.util.ArrayList;
import java.util.List;

import ca.bellmedia.lib.vidi.analytics.qos.trackers.Exo2NewRelicTrackerBuilder;
import ca.bellmedia.lib.vidi.analytics.qos.trackers.GoogleImaNewRelicAdsTracker;
import ca.bellmedia.lib.vidi.analytics.qos.trackers.TrackerBuilderData;

public class Exo2Activity extends AppCompatActivity implements AdsLoader.AdsLoadedListener, AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

    private SimpleExoPlayer player;
    private CastPlayer castPlayer;
    private ImaAdsLoader adsLoader;
    private Long trackerID;
    private int currentVideoIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exo2);

        NRLog.enable();

        String video = getIntent().getStringExtra("video");
        NRLog.d("Got extra video = " + video);

        if (video.equals("DASH_DRM_IMA")) {
            NRLog.d("call setupPlayerDASH_DRM_IMA");
            setupPlayerDASH_DRM_IMA();
        }
        else if (video.equals("DASH_LIVE_IMA")) {
            NRLog.d("call setupPlayerDASHLiveWithIMA");
            setupPlayerDASHLiveWithIMA();
        }
        else if (video.equals("DASH")) {
            NRLog.d("call setupPlayerDASH");
            setupPlayerDASH();
        }
        else if (video.equals("HLS")) {
            NRLog.d("call setupPlayerHLS");
            setupPlayerHLS();
        }
        else if (video.equals("Cast")) {
            NRLog.d("call setupCastMediaQueue");
            setupCastMediaQueue();
        }
        else {
            NRLog.d("Unknown video!!");
        }

        //setupManualPlaylist();
        //setupPlayer();
        //setupPlayerWithPlaylist();
        //setupPlayerWithHLSMediaSource();
        //setupCastMediaQueue();
        //setupIMA();
        //setupPlayerHLS();
        //setupPlayerDASH();
        //setupPlayerDASHLiveWithIMA();
        //setupPlayerDASH_DRM_IMA();

        // Manipulate heartbeat
        //NewRelicVideoAgent.getContentsTracker(trackerID).getHeartbeat().setHeartbeatInterval(5000);
        //NewRelicVideoAgent.getContentsTracker(trackerID).getHeartbeat().disableHeartbeat();
        //NewRelicVideoAgent.getContentsTracker(trackerID).getHeartbeat().enableHeartbeat();
    }

    private void setupManualPlaylist() {
        List<Uri> playlistUri = new ArrayList<>();
        playlistUri.add(Uri.parse(getString(R.string.videoURL_dolby)));
        playlistUri.add(Uri.parse(getString(R.string.videoURL_jelly)));
        playlistUri.add(Uri.parse(getString(R.string.content_url)));

        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory videoTrackSelectionFactory =  new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =  new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);
        player.setPlayWhenReady(true);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        trackerID = NewRelicVideoAgent.start(player, Exo2TrackerBuilder.class);

        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoTestApp"));

        Button playerButton  = findViewById(R.id.playButton);
        playerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playlistUri.size() > currentVideoIndex) {
                    NRLog.d("-----------> PLAY NEXT VIDEO");

                    Uri videoUri = playlistUri.get(currentVideoIndex);
                    currentVideoIndex++;

                    MediaSource videoSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(videoUri);

                    NewRelicVideoAgent.getContentsTracker(trackerID).reset();
                    NewRelicVideoAgent.getContentsTracker(trackerID).setup();

                    player.prepare(videoSource);
                }
            }
        });
    }

    private void setupIMA() {
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory videoTrackSelectionFactory =  new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =  new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        adsLoader = new ImaAdsLoader(this, Uri.parse(getString(R.string.ad_tag_url)));
        adsLoader.setPlayer(player);

        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoTestApp"));

        Uri videoUri = Uri.parse(getString(R.string.videoURL_dolby));

        MediaSource videoSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(videoUri);

        AdsMediaSource adsMediaSource =
                new AdsMediaSource(videoSource, dataSourceFactory, adsLoader, playerView);

        adsLoader.getAdsLoader().addAdsLoadedListener(this);

        adsLoader.getAdsLoader().addAdErrorListener(this);

        trackerID = NewRelicVideoAgent.start(player, videoUri, Exo2TrackerBuilder.class);

        player.setPlayWhenReady(true);
        player.prepare(adsMediaSource);
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

        Button playerButton  = findViewById(R.id.playButton);
        playerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //is playing?
                CoreTrackerState state = NewRelicVideoAgent.getContentsTracker(trackerID).state();
                if (state == CoreTrackerState.CoreTrackerStatePlaying || state == CoreTrackerState.CoreTrackerStatePaused) {
                    castPlayer.stop();
                }
                else {
                    castPlayer.loadItems(mediaItems, 0, 0, Player.REPEAT_MODE_OFF);
                }
            }
        });

        // Setup video agent for CastPlayer
        trackerID = NewRelicVideoAgent.start(castPlayer, Uri.parse(videoUrl), Exo2TrackerBuilder.class);
    }

    @Override
    protected void onDestroy() {
        NRLog.d("Exo2Activity onDestroy");
        super.onDestroy();
        if (trackerID != null) {
            NewRelicVideoAgent.releaseTracker(trackerID);
        }
        if (player != null) {
            player.release();
        }
    }

    private void setupPlayerHLS() {
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory videoTrackSelectionFactory =  new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =  new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoTestApp"));

        //Uri videoUri = Uri.parse(getString(R.string.videoURL_hls));
        Uri videoUri = Uri.parse("http://cdn3.viblast.com/streams/hls/airshow/playlist.m3u8");

        Handler mainHandler = new Handler();
        MediaSource mediaSource = new HlsMediaSource(videoUri,
                dataSourceFactory, mainHandler, null);

        trackerID = NewRelicVideoAgent.start(player, videoUri, Exo2TrackerBuilder.class);

        player.setPlayWhenReady(true);
        player.prepare(mediaSource);
    }

    private void setupPlayerDASH() {
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory videoTrackSelectionFactory =  new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =  new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoTestApp"));

        //Uri videoUri = Uri.parse(getString(R.string.videoURL_bunnydash));
        //Uri videoUri = Uri.parse(getString(R.string.videoURL_cardash));
        Uri videoUri = Uri.parse("http://www.bok.net/dash/tears_of_steel/cleartext/stream.mpd");

        Handler mainHandler = new Handler();
        DashMediaSource dashMediaSource = new DashMediaSource(videoUri, dataSourceFactory,
                new DefaultDashChunkSource.Factory(dataSourceFactory), null, null);

        trackerID = NewRelicVideoAgent.start(player, videoUri, Exo2TrackerBuilder.class);

        // Create a custom action with timeSince attribute
        NewRelicVideoAgent.getContentsTracker(trackerID).sendCustomAction("CUSTOM_ACTION", "timeSinceCustomAction");

        player.setPlayWhenReady(true);
        player.prepare(dashMediaSource);
    }

    private void setupPlayerDASHLiveWithIMA() {
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory videoTrackSelectionFactory =  new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =  new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        adsLoader = new ImaAdsLoader(this, Uri.parse(getString(R.string.ad_tag_2_url)));
        adsLoader.setPlayer(player);

        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoTestApp"));

        //Live stream
        Uri videoUri = Uri.parse("https://pe-ak-lp02a-9c9media.akamaized.net/live/News1Digi/p/dash/00000001/8e377c581da8df4e/manifest.mpd");

        //Normal video
        //Uri videoUri = Uri.parse("http://www.bok.net/dash/tears_of_steel/cleartext/stream.mpd");

        //Other Version of Normal video
        //Uri videoUri = Uri.parse("https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd");

        DashMediaSource dashMediaSource = new DashMediaSource(videoUri, dataSourceFactory,
                new DefaultDashChunkSource.Factory(dataSourceFactory), null, null);

        AdsMediaSource adsMediaSource =
                new AdsMediaSource(dashMediaSource, dataSourceFactory, adsLoader, playerView);

        adsLoader.getAdsLoader().addAdsLoadedListener(this);

        adsLoader.getAdsLoader().addAdErrorListener(this);

        trackerID = NewRelicVideoAgent.start(player, videoUri, Exo2TrackerBuilder.class);

        player.setPlayWhenReady(true);
        player.prepare(adsMediaSource);
    }

    private void setupPlayerDASH_DRM_IMA() {

        NRLog.d("Play Bell Video with DRM");

        //Setup Bell Video (only with VPN from Canada)

        // Akamai CDN
        Uri videoUri = Uri.parse("https://capi.9c9media.com/destinations/ctv_android/platforms/android/contents/58240/contentPackages/2818168/manifest.mpd?did=6dc06635-ab6b-4eef-9fde-f0e64ecaf23e&filter=0x13");

        // Fastly CDN
        //Uri videoUri = Uri.parse("https://capi.9c9media.com/destinations/crave_atexace/platforms/desktop/bond/contents/1875893/contentpackages/3591283/manifest.mpd?jwt=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJsb2NhbGl6YXRpb24iOiJlbi1DQSIsInVzZXJfbmFtZSI6ImIwYjY4MTllMzFhMzRkMDg0NGFhZjJlZjdkMGRhOGRhMGZhZjQ1Y2FiNWEzY2NhN2M1NTFmMjM5OTU5ZGU4MDJAYmR1LmZha2UuZG9tYWluIiwiY3JlYXRpb25fZGF0ZSI6MTU5NDczNzE5MzkwNCwiYWlzX2lkIjoiYjBiNjgxOWUzMWEzNGQwODQ0YWFmMmVmN2QwZGE4ZGEwZmFmNDVjYWI1YTNjY2E3YzU1MWYyMzk5NTlkZTgwMiIsImJkdV9wcm92aWRlciI6IkJlbGwgTWVkaWEgR3Vlc3QiLCJhdXRob3JpdGllcyI6WyJCRFVfVVNFUiJdLCJjbGllbnRfaWQiOiJ1c2VyLW1hbmFnZW1lbnQtZGVmYXVsdCIsImFjY291bnRfaWQiOiI1YmJmOGYyYTQ0NDA5YzAwMDk0MDgwYWYiLCJwcm9maWxlX2lkIjoiNzNlMTNhODRkOGQ5NGYyZDhhYzhhZDhmZjljMzM4ZjQiLCJzY29wZSI6WyJkZWZhdWx0Iiwib2ZmbGluZV9kb3dubG9hZDoxMCIsIm1hdHVyaXR5OmFkdWx0Iiwic3Vic2NyaXB0aW9uOnN0YXJ6LHNlLGNyYXZlcCxjcmF2ZXR2IiwibWFzdGVyX3Byb2ZpbGUiXSwiZXhwIjoxNTk0NzUxNTkzLCJhY2NvdW50Ijp7InBvc3RhbF9jb2RlIjoiTTVWIiwic3RhdHVzIjoiQUNUSVZFIn0sImp0aSI6ImQ5ZTI3ZTFiLWQ4YTYtNDE5Zi05OTMyLWY3ZmNiNmQyMTU5NyJ9.omaytTJhpexIoouMB9lQZJ0XDPkAChj6AOxGXc4doW1k3oPbuh_UxOtH72P5VERjIrAsqtAj9fImZVn_tBROSoC9NDQIKcr5IusD4JXoJG53RZytvAhbbLqE2iQ188etGhjyup4lnb1hCOVP36ksmWasPLTwFjKman3xckZ0NlQXk56EljlYyARNMzIj8xhVmTqmEqOtCeUJuwkPl17tdouy_gExEksa-8x544MVrbxBVzmoYXfbVrhVTGvXQMQYHmKHrJgMHyfRbvOTfwCBjCwKGDF0YAZN61lMlx4vNgXsTucrfxjr5YqiUgEq56CfcQ7cMVFrMmHaPYY5wM5IOA&dId=5f085ffc66d576000619f169");

        DataSource.Factory dataSourceFactory =
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoTestApp"));
        DashMediaSource dashMediaSource = new DashMediaSource(videoUri, dataSourceFactory, new DefaultDashChunkSource.Factory(dataSourceFactory), null, null);

        // Setup player

        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory videoTrackSelectionFactory =  new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =  new DefaultTrackSelector(videoTrackSelectionFactory);

        //Setup DRM stuff
        final MediaDrmCallback mediaDrmCallback = new HttpMediaDrmCallback(
                "https://license.9c9media.ca/widevine",
                new DefaultHttpDataSourceFactory("VideoDemoApp"));

        DefaultDrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
        try {
            drmSessionManager = DefaultDrmSessionManager.newWidevineInstance(mediaDrmCallback, null);
        }
        catch (Exception e) {
            NRLog.d("Unsuported DRM Exception");
            e.printStackTrace();
            return;
        }

        player = ExoPlayerFactory.newSimpleInstance(this, new DefaultRenderersFactory(this), new DefaultTrackSelector(), drmSessionManager);

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        adsLoader = new ImaAdsLoader(this, Uri.parse(getString(R.string.ad_tag_2_url)));
        //adsLoader = new ImaAdsLoader(this, Uri.parse(getString(R.string.ad_tag_url)));
        adsLoader.setPlayer(player);

        AdsMediaSource adsMediaSource =
                new AdsMediaSource(dashMediaSource, dataSourceFactory, adsLoader, playerView);

        adsLoader.getAdsLoader().addAdsLoadedListener(this);
        /*
        adsLoader.getAdsLoader().addAdsLoadedListener(this);
        adsLoader.getAdsLoader().addAdErrorListener(this);
         */

        player.setPlayWhenReady(true);
        player.prepare(adsMediaSource);
        //player.prepare(dashMediaSource);

        //trackerID = NewRelicVideoAgent.start(player, videoUri, Exo2TrackerBuilder.class);
        trackerID = NewRelicVideoAgent.start(new TrackerBuilderData(player, true), videoUri, Exo2NewRelicTrackerBuilder.class);
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

        playlistUri.add(Uri.parse(getString(R.string.videoURL_dolby)));
        playlistUri.add(Uri.parse(getString(R.string.videoURL_jelly)));
        playlistUri.add(Uri.parse(getString(R.string.content_url)));

        MediaSource mediaSourceArray[] = new MediaSource[playlistUri.size()];

        for (int i = 0 ; i < playlistUri.size() ; i++) {
            MediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(playlistUri.get(i));
            mediaSourceArray[i] = mediaSource;
        }

        ConcatenatingMediaSource concatenatedSource = new ConcatenatingMediaSource(mediaSourceArray);

        trackerID = NewRelicVideoAgent.start(player, playlistUri, Exo2TrackerBuilder.class);

        ExoPlayer2ContentsTracker tracker = (ExoPlayer2ContentsTracker)NewRelicVideoAgent.getContentsTracker(trackerID);
        // Do whatever with tracker...
        NRLog.d("Tracker = " + tracker);

        //trackerID = NewRelicVideoAgent.start(player, Exo2TrackerBuilder.class);

        /*
        NewRelicVideoAgent.initJNIEnv();
        ExoPlayer2ContentsTracker tracker = new ExoPlayer2ContentsTracker(player);
        trackerID = NewRelicVideoAgent.startWithTracker(tracker, null);
         */

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

    // IMA ad listeners

    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
        NRLog.d("On Ads Loader Event");

        //AdsManager mAdsManager = adsManagerLoadedEvent.getAdsManager();
        //mAdsManager.addAdEventListener(this);

        AdsManager mAdsManager = adsManagerLoadedEvent.getAdsManager();
        AdsTracker adtracker = NewRelicVideoAgent.getAdsTracker(trackerID);
        if (adtracker != null) {
            ((GoogleImaNewRelicAdsTracker)adtracker).setAdsManager(mAdsManager);
        }
    }

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        NRLog.d("On Ads Error Event");
    }

    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (adEvent.getType() != AdEvent.AdEventType.AD_PROGRESS) {
            NRLog.d("On Ads Event = " + adEvent);
        }
    }
}
