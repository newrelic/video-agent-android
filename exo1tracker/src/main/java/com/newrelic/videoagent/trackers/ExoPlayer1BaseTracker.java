package com.newrelic.videoagent.trackers;

import android.media.MediaCodec;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.basetrackers.AdsTracker;
import com.newrelic.videoagent.jni.swig.AttrList;
import com.newrelic.videoagent.jni.swig.CoreTrackerState;
import com.newrelic.videoagent.jni.swig.TrackerCore;
import com.newrelic.videoagent.jni.swig.ValueHolder;

import java.util.List;

public class ExoPlayer1BaseTracker extends Object implements ExoPlayer.Listener, MediaCodecVideoTrackRenderer.EventListener {

    private static final long timerTrackTimeMs = 250;
    private boolean firstFrameHappened;
    protected ExoPlayer player;
    protected TrackerCore trackerCore;
    private List<Uri> playlist;

    Handler mainHandler;

    Runnable runnable = new Runnable() {
        /*
        @Override
        public void run() {

            if (player.getPlaybackState() == ExoPlayer.STATE_READY) {
                mainHandler.postDelayed(runnable, PROGRESS_TRACK_DELAY_MS);
            }
            else {
                return;
            }

            Log.v("Exo1Log", "Time Tracked = " + exoPlayer.getCurrentPosition());
        }
        */

        @Override
        public void run() {
            double currentTimeSecs = (double)player.getCurrentPosition() / 1000.0;
            double durationSecs = (double)player.getDuration() / 1000.0;

            /*
            NRLog.d("Current content position time = " + currentTimeSecs);
            NRLog.d("Duration time = " + durationSecs);
            NRLog.d("Current content position percentage = " + 100.0 * currentTimeSecs / durationSecs);
            NRLog.d("Get current seek bar postion = " + player.getCurrentPosition());
            */

            if (currentTimeSecs > 0 && firstFrameHappened == false) {
                NRLog.d("!! First Frame !!");
                firstFrameHappened = true;
                trackerCore.sendStart();
            }

            // Give it margin to ensure the video won't fin ish before we get the last time event
            double margin = 2.0 * (double)timerTrackTimeMs / 1000.0;
            if (currentTimeSecs + margin >= durationSecs) {
                if (trackerCore.state() != CoreTrackerState.CoreTrackerStateStopped) {
                    NRLog.d("!! End Of Video !!");
                    sendEnd();
                }
                return;
            }

            if (trackerCore.state() != CoreTrackerState.CoreTrackerStateStopped) {
                mainHandler.postDelayed(this, timerTrackTimeMs );
            }
        }
    };

    public ExoPlayer1BaseTracker(ExoPlayer player, TrackerCore trackerCore) {
        this.trackerCore = trackerCore;
        this.player = player;
    }

    public void setup() {
        player.addListener(this);
        trackerCore.sendPlayerReady();
    }

    public void reset() {
        firstFrameHappened = false;
    }

    public Boolean isAd() {
        return this.trackerCore instanceof AdsTracker;
    }


    public void setPlaylist(List<Uri> playlist) {
        this.playlist = playlist;
    }

    private void sendRequest() {
        NRLog.d("OVERWRITTEN sendRequest");
        trackerCore.sendRequest();
        mainHandler.removeCallbacks(runnable);
        mainHandler.postDelayed(this.runnable, timerTrackTimeMs);
    }

    private void sendEnd() {
        trackerCore.sendEnd();
        firstFrameHappened = false;
    }

    private void sendError(Exception error) {
        String msg;
        if (error != null) {
            if (error.getMessage() != null) {
                msg = error.getMessage();
            }
            else {
                msg = error.toString();
            }
        }
        else {
            msg = "<Unknown error>";
        }

        trackerCore.sendError(msg);
    }

    // ExoPlayer.Listener

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        /*
        String stateStr = "Unknown";
        switch (playbackState) {
            case ExoPlayer.STATE_IDLE:
                stateStr = "STATE_IDLE";
                break;
            case ExoPlayer.STATE_PREPARING:
                stateStr = "STATE_PREPARING";
                break;
            case ExoPlayer.STATE_BUFFERING:
                stateStr = "STATE_BUFFERING";
                break;
            case ExoPlayer.STATE_READY:
                stateStr = "STATE_READY";
                mainHandler.removeCallbacks(runnable);
                mainHandler.postDelayed(runnable, 0);
                break;
            case ExoPlayer.STATE_ENDED:
                stateStr = "STATE_ENDED";
                break;
        }

        Log.v("Exo1Log", "onPlayerStateChanged = " + stateStr);
        */

        NRLog.d("onPlayerStateChanged, payback state = " + playbackState + " {");

        if (playbackState == ExoPlayer.STATE_READY) {
            NRLog.d("\tVideo Is Ready");

            if (trackerCore.state() == CoreTrackerState.CoreTrackerStateBuffering) {
                trackerCore.sendBufferEnd();

                if (trackerCore.state() == CoreTrackerState.CoreTrackerStateSeeking) {
                    trackerCore.sendSeekEnd();
                }
            }
        }
        else if (playbackState == ExoPlayer.STATE_ENDED) {
            NRLog.d("\tVideo Ended Playing");
        }
        else if (playbackState == ExoPlayer.STATE_BUFFERING) {
            NRLog.d("\tVideo Is Buffering");

            if (trackerCore.state() != CoreTrackerState.CoreTrackerStateBuffering) {
                trackerCore.sendBufferStart();
            }
        }

        if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
            NRLog.d("\tVideo Playing");

            if (trackerCore.state() == CoreTrackerState.CoreTrackerStateStopped) {
                sendRequest();
            }
            else if (trackerCore.state() == CoreTrackerState.CoreTrackerStatePaused) {
                trackerCore.sendResume();
            }
        }
        else if (playWhenReady) {
            NRLog.d("\tVideo Not Playing");
        }
        else {
            NRLog.d("\tVideo Paused");

            if (trackerCore.state() == CoreTrackerState.CoreTrackerStatePlaying) {
                trackerCore.sendPause();
            }
        }

        NRLog.d("}");
    }

    @Override
    public void onPlayWhenReadyCommitted() {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        sendError(error);
    }

    // MediaCodecVideoTrackRenderer.EventListener

    @Override
    public void onDroppedFrames(int count, long elapsed) {
        NRLog.d("onDroppedVideoFrames analytics");
        AttrList attributes = new AttrList();
        attributes.set("lostFrames", new ValueHolder(count));
        attributes.set("lostFramesDuration", new ValueHolder(elapsed));

        String actionName = isAd() ? "AD_DROPPED_FRAMES" : "CONTENT_DROPPED_FRAMES";

        trackerCore.sendCustomAction(actionName, attributes);
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
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
}
