package com.newrelic.videoagent.trackers;

import android.net.Uri;

import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.CastPlayer.SessionAvailabilityListener;
import com.google.android.gms.cast.framework.CastContext;
import com.newrelic.videoagent.BuildConfig;
import com.newrelic.videoagent.NRLog;
import com.newrelic.videoagent.basetrackers.ContentsTracker;
import com.newrelic.videoagent.jni.swig.CoreTrackerState;

import java.lang.reflect.Field;
import java.util.List;

public class CastPlayerContentsTracker extends ContentsTracker implements SessionAvailabilityListener {
    private ExoPlayer2BaseTracker baseTracker;

    public CastPlayerContentsTracker(CastPlayer player) {
        baseTracker = new ExoPlayer2BaseTracker(player, this);
        getPlayer().setSessionAvailabilityListener(this);
    }

    private CastPlayer getPlayer() {
        return (CastPlayer) baseTracker.player;
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

    public void generateCastAttributes() {
        CastContext castContext;

        // Try to access the private field "castContext" inside CastPlayer
        try {
            Field castContextField = CastPlayer.class.getDeclaredField("castContext");
            castContextField.setAccessible(true);
            castContext = (CastContext) castContextField.get(getPlayer());
        }
        catch (Exception e) {
            e.printStackTrace();
            castContext = null;
        }

        if (castContext != null) {
            if (castContext.getSessionManager().getCurrentCastSession() != null) {

                setOptionKey("castDeviceStatusText", castContext.getSessionManager().getCurrentCastSession().getApplicationStatus());
                setOptionKey("castSessionId", castContext.getSessionManager().getCurrentCastSession().getSessionId());
                setOptionKey("castDeviceCategory", castContext.getSessionManager().getCurrentCastSession().getCategory());

                if (castContext.getSessionManager().getCurrentCastSession().getApplicationMetadata() != null) {
                    setOptionKey("castAppId", castContext.getSessionManager().getCurrentCastSession().getApplicationMetadata().getApplicationId());
                    setOptionKey("castAppName", castContext.getSessionManager().getCurrentCastSession().getApplicationMetadata().getName());
                }

                if (castContext.getSessionManager().getCurrentCastSession().getCastDevice() != null) {
                    setOptionKey("castDeviceId", castContext.getSessionManager().getCurrentCastSession().getCastDevice().getDeviceId());
                    setOptionKey("castDeviceVersion", castContext.getSessionManager().getCurrentCastSession().getCastDevice().getDeviceVersion());
                    setOptionKey("castDeviceModelName", castContext.getSessionManager().getCurrentCastSession().getCastDevice().getModelName());
                    setOptionKey("castDeviceFriendlyName", castContext.getSessionManager().getCurrentCastSession().getCastDevice().getFriendlyName());
                }

                //TODO: castDeviceUniqueId not found
            }
        }
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

    /*  TODO: is it possible to implement the following getters in a CastPlayer?

        Object getRenditionWidth()
        Object getRenditionHeight()
        Object getFps()
        Object getIsMuted()
    */

    @Override
    public void setSrc(List<Uri> uris) {
        baseTracker.setPlaylist(uris);
    }

    // SessionAvailabilityListener

    @Override
    public void onCastSessionAvailable() {
        generateCastAttributes();
        sendCustomAction("CAST_START_SESSION");
    }

    @Override
    public void onCastSessionUnavailable() {
        generateCastAttributes();
        if (state() != CoreTrackerState.CoreTrackerStateStopped) {
            sendEnd();
        }
        sendCustomAction("CAST_END_SESSION");
    }

    @Override
    public void sendRequest() {
        generateCastAttributes();
        super.sendRequest();
    }

    @Override
    public void sendStart() {
        generateCastAttributes();
        super.sendStart();
    }

    @Override
    public void sendEnd() {
        generateCastAttributes();
        super.sendEnd();
    }

    @Override
    public void sendBufferStart() {
        generateCastAttributes();
        super.sendBufferStart();
    }

    @Override
    public void sendBufferEnd() {
        generateCastAttributes();
        super.sendBufferEnd();
    }

    @Override
    public void sendPause() {
        generateCastAttributes();
        super.sendPause();
    }

    @Override
    public void sendResume() {
        generateCastAttributes();
        super.sendResume();
    }

    @Override
    public void sendSeekStart() {
        generateCastAttributes();
        super.sendSeekStart();
    }

    @Override
    public void sendSeekEnd() {
        generateCastAttributes();
        super.sendSeekEnd();
    }

    @Override
    public void sendHeartbeat() {
        generateCastAttributes();
        super.sendHeartbeat();
    }

    @Override
    public void sendRenditionChange() {
        generateCastAttributes();
        super.sendRenditionChange();
    }

    @Override
    public void sendError(String message) {
        generateCastAttributes();
        super.sendError(message);
    }
}
