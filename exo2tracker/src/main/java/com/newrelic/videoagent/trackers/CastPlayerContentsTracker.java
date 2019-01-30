package com.newrelic.videoagent.trackers;

import android.net.Uri;

import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.newrelic.videoagent.BuildConfig;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.basetrackers.ContentsTracker;

import java.util.List;

public class CastPlayerContentsTracker extends ContentsTracker {
    private ExoPlayer2BaseTracker baseTracker;

    public CastPlayerContentsTracker(CastPlayer player) {
        baseTracker = new ExoPlayer2BaseTracker(player, this);
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
        return "CastPlayer";
    }

    public Object getPlayerVersion() {
        return "2.x";
    }

    public Object getTrackerName() {
        return "CastPlayerTracker";
    }

    public Object getTrackerVersion() {
        return BuildConfig.VERSION_NAME;
    }

    public Object getBitrate() {
        return new Long(baseTracker.getBitrateEstimate());
    }

    public Object getRenditionBitrate() {
        return getBitrate();
    }

    /*
    public Object getRenditionWidth() {
        return new Long((long)getPlayer().getVideoFormat().width);
    }

    public Object getRenditionHeight() {
        return new Long((long)getPlayer().getVideoFormat().height);
    }
    */

    public Object getDuration() {
        return new Long(baseTracker.player.getDuration());
    }

    public Object getPlayhead() {
        return new Long(baseTracker.player.getContentPosition());
    }

    public Object getSrc() {
        if (baseTracker.getPlaylist() != null) {
            NRLog.d("Current window index = " + baseTracker.player.getCurrentWindowIndex());
            try {
                Uri src = baseTracker.getPlaylist().get(baseTracker.player.getCurrentWindowIndex());
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

    public Object getPlayrate() {
        return new Double(baseTracker.player.getPlaybackParameters().speed);
    }

    /*
    public Object getFps() {
        if (getPlayer().getVideoFormat() != null) {
            if (getPlayer().getVideoFormat().frameRate > 0) {
                return new Double(getPlayer().getVideoFormat().frameRate);
            }
        }

        return null;
    }

    public Object getIsMuted() {
        if (getPlayer().getVolume() == 0) {
            return new Long(1);
        }
        else {
            return new Long(0);
        }
    }
    */

    private CastPlayer getPlayer() {
        return (CastPlayer) baseTracker.player;
    }

    @Override
    public void setSrc(List<Uri> uris) {
        baseTracker.setPlaylist(uris);
    }
}
