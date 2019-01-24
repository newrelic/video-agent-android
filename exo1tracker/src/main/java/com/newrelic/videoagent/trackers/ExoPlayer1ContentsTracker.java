package com.newrelic.videoagent.trackers;

import android.net.Uri;

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaFormat;
import com.newrelic.videoagent.BuildConfig;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.basetrackers.ContentsTracker;

import java.util.List;

public class ExoPlayer1ContentsTracker extends ContentsTracker {
    private ExoPlayer1BaseTracker baseTracker;

    public ExoPlayer1ContentsTracker(ExoPlayer player) {
        baseTracker = new ExoPlayer1BaseTracker(player, this);
    }

    @Override
    public void setup() {
        super.setup();
        baseTracker.setup();
    }

    @Override
    public void reset() {
        super.reset();
        baseTracker.reset();
    }

    public Object getPlayerName() {
        return "ExoPlayer1";
    }

    public Object getPlayerVersion() {
        return "1.x";
    }

    public Object getTrackerName() {
        return "ExoPlayer1Tracker";
    }

    public Object getTrackerVersion() {
        return BuildConfig.VERSION_NAME;
    }

    public Object getBitrate() {
        return new Long(getTrackFormat(baseTracker.player).bitrate);
    }

    public Object getRenditionBitrate() {
        return getBitrate();
    }

    public Object getRenditionWidth() {
        return new Long(getTrackFormat(baseTracker.player).width);
    }

    public Object getRenditionHeight() {
        return new Long(getTrackFormat(baseTracker.player).height);
    }

    public Object getDuration() {
        return new Long(baseTracker.player.getDuration());
    }

    public Object getPlayhead() {
        return new Long(baseTracker.player.getCurrentPosition());
    }

    public Object getSrc() {
        if (baseTracker.getPlaylist() != null) {
            NRLog.d("Current track index = " + getCurrentTrackIndex());

            try {
                Uri src = baseTracker.getPlaylist().get(getCurrentTrackIndex());
                return src.toString();
            }
            catch (Exception e) {
                return "";
            }
        }
        else {
            return "";
        }
    }

    /*
    // Not possible with Exo1
    public Object getPlayrate() {
        return new Double(baseTracker.player.getPlaybackParameters().speed);
    }

    // Not possible with Exo1
    public Object getFps() {
        if (baseTracker.player.getVideoFormat() != null) {
            if (baseTracker.player.getVideoFormat().frameRate > 0) {
                return new Double(baseTracker.player.getVideoFormat().frameRate);
            }
        }

        return null;
    }

    // Probably not possible with Exo1
    public Object getIsMuted() {
        if (baseTracker.player.getVolume() == 0) {
            return new Long(1);
        }
        else {
            return new Long(0);
        }
    }
    */

    public ExoPlayer1BaseTracker getEventListener() {
        return baseTracker;
    }

    @Override
    public void setSrc(List<Uri> uris) {
        baseTracker.setPlaylist(uris);
    }

    private MediaFormat getTrackFormat(ExoPlayer exoPlayer) {
        return exoPlayer.getTrackFormat(getRendererIndex(), getCurrentTrackIndex());
    }

    // TODO: Fix it -> renderer index index HARDCODED!!
    private int getRendererIndex() {
        return 0;
    }

    private int getCurrentTrackIndex() {
        return baseTracker.player.getSelectedTrack(getRendererIndex());
    }
}
