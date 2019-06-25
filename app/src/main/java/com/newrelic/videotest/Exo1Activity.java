package com.newrelic.videotest;

import android.media.MediaCodec;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.MediaCodecSelector;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.NewRelicVideoAgent;
import com.newrelic.videoagent.jni.swig.CoreTrackerState;
import com.newrelic.videoagent.trackers.Exo1TrackerBuilder;
import com.newrelic.videoagent.trackers.ExoPlayer1ContentsTracker;

import java.util.ArrayList;
import java.util.Arrays;

public class Exo1Activity extends AppCompatActivity implements MediaCodecVideoTrackRenderer.EventListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener, ExoPlayer.Listener {

    class VideoProvider {
        private ArrayList<Uri> listOfVideos;
        private int videoIndex = 0;

        public VideoProvider(Uri... uris) {
            setList(uris);
        }

        public void setList(Uri... uris) {
            listOfVideos = new ArrayList<>(Arrays.asList(uris));
            videoIndex = 0;
        }

        public Uri getNext() {
            if (videoIndex < listOfVideos.size()) {
                return listOfVideos.get(videoIndex++);
            }
            else {
                return null;
            }
        }

        public Uri getAt(int pos) {
            videoIndex = pos;
            return getNext();
        }

        public void rewind() {
            videoIndex = 0;
        }
    }

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;
    private static final int PROGRESS_TRACK_DELAY_MS = 1000;

    VideoProvider videoProvider;

    ExoPlayer exoPlayer;
    Handler mainHandler;
    SeekBar progressBar;
    Button playButton;

    private Long trackerID;

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            mainHandler.postDelayed(runnable, PROGRESS_TRACK_DELAY_MS);
            progressBar.setProgress((int) ((exoPlayer.getCurrentPosition() * 100) / exoPlayer.getDuration()));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exo1);
        videoProvider = new VideoProvider(
                Uri.parse(getString(R.string.videoURL_panasonic))
                );
        setupPlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NewRelicVideoAgent.releaseTracker(trackerID);
        exoPlayer.release();
    }

    void setupPlayer() {
        mainHandler = new Handler();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surface_view);

        Uri url = videoProvider.getNext();
        //Uri url = videoProvider.getAt(2);
        if (url == null) return;

        TrackRenderer[] rendererArray = buildRendererArray(url);

        exoPlayer = ExoPlayer.Factory.newInstance(rendererArray.length);
        exoPlayer.prepare(rendererArray);

        exoPlayer.sendMessage(getVideoRenderer(rendererArray), MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surfaceView.getHolder().getSurface());

        playButton = findViewById(R.id.play_button);
        playButton.setOnClickListener(this);

        progressBar = findViewById(R.id.progress_bar);
        progressBar.setOnSeekBarChangeListener(this);
        progressBar.setMax(100);

        mainHandler.removeCallbacks(runnable);
        mainHandler.postDelayed(runnable, 0);

        // Enable logs and start Video Agent with ExoPlayer-1 Tracker
        NRLog.enable();
        trackerID = NewRelicVideoAgent.start(exoPlayer, url, Exo1TrackerBuilder.class);

        // Autoplay
        exoPlayer.setPlayWhenReady(true);

        exoPlayer.addListener(this);
    }

    public TrackRenderer[] buildRendererArray(Uri url) {
        Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
        DataSource dataSource = new DefaultUriDataSource(this, "userAgent");

        SampleSource sampleSource = new ExtractorSampleSource(
                url, dataSource, allocator, BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);

        MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(
                this, sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 100, mainHandler,  this, 1);

        MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
                sampleSource, MediaCodecSelector.DEFAULT);
        TrackRenderer[] rendererArray = {videoRenderer, audioRenderer};

        return rendererArray;
    }

    public TrackRenderer getVideoRenderer(TrackRenderer[] rendererArray) {
        TrackRenderer videoRenderer = null;
        for (TrackRenderer renderer : rendererArray) {
            if (renderer instanceof MediaCodecVideoTrackRenderer) {
                videoRenderer = renderer;
            }
        }
        return videoRenderer;
    }

    private void changeVideo(Uri url){
        exoPlayer.stop();
        exoPlayer.seekTo(0L);
        TrackRenderer[] rendererArray = buildRendererArray(url);
        exoPlayer.prepare(rendererArray);

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surface_view);
        exoPlayer.sendMessage(getVideoRenderer(rendererArray), MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surfaceView.getHolder().getSurface());

        ExoPlayer1ContentsTracker tracker = (ExoPlayer1ContentsTracker)NewRelicVideoAgent.getContentsTracker(trackerID);
        tracker.setSrc(url);
    }

    // MediaCodecVideoTrackRenderer.EventListener

    @Override
    public void onDroppedFrames(int count, long elapsed) {
        NewRelicVideoAgent.getContentsTracker(trackerID).sendDroppedFrame(count, (int)elapsed);
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        AspectRatioFrameLayout aspectRatioFrameLayout = findViewById(R.id.video_frame);
        aspectRatioFrameLayout.setAspectRatio(height == 0 ? 1 : (width * pixelWidthHeightRatio) / height);
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
    }

    // OnClickListener

    @Override
    public void onClick(View v) {
        exoPlayer.stop();
        NewRelicVideoAgent.getContentsTracker(trackerID).sendEnd();

        /*
        if (exoPlayer.getPlayWhenReady()) {
            exoPlayer.setPlayWhenReady(false);
        }
        else {
            exoPlayer.setPlayWhenReady(true);
        }
        */
    }

    // OnSeekBarChangeListener

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        NewRelicVideoAgent.getContentsTracker(trackerID).sendSeekStart();
        long newPos = (long)(((float)seekBar.getProgress() / 100.0f) * exoPlayer.getDuration());
        exoPlayer.seekTo(newPos);
    }

    // ExoPlayer.Listener

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

        if (NewRelicVideoAgent.getContentsTracker(trackerID).state() == CoreTrackerState.CoreTrackerStatePaused) {
            playButton.setText("Play");
        }
        else {
            playButton.setText("Pause");
        }

        if (playbackState == ExoPlayer.STATE_ENDED) {
            Log.v("Exo1Activity", "FINISHED PLAYING, NEXT TRACK");
            Uri url = videoProvider.getNext();
            if (url == null) return;
            changeVideo(url);
        }
    }

    @Override
    public void onPlayWhenReadyCommitted() {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }
}
