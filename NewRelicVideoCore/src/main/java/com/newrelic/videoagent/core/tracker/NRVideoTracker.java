package com.newrelic.videoagent.core.tracker;

import android.os.Handler;
import com.newrelic.videoagent.core.model.NRTimeSince;
import com.newrelic.videoagent.core.model.NRTrackerState;
import com.newrelic.videoagent.core.utils.NRLog;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.newrelic.videoagent.core.NRDef.*;

public class NRVideoTracker extends NRTracker {

    public final NRTrackerState state;

    private Integer heartbeatTimeInterval;
    private final Handler heartbeatHandler;
    private final Runnable heartbeatRunnable;
    private Boolean isHeartbeatRunning;
    private Integer numberOfVideos;
    private Integer numberOfAds;
    private Integer numberOfErrors;
    private final String viewSessionId;
    private Integer viewIdIndex;
    private Integer adBreakIdIndex;
    private Long playtimeSinceLastEventTimestamp;
    private Long totalPlaytime;
    private Long playtimeSinceLastEvent;
    private String bufferType;
    private NRTimeSince lastAdTimeSince;

    public NRVideoTracker() {
        state = new NRTrackerState();
        numberOfAds = 0;
        numberOfErrors = 0;
        numberOfVideos = 0;
        viewIdIndex = 0;
        adBreakIdIndex = 0;
        viewSessionId = getAgentSession() + "-" + (System.currentTimeMillis() / 1000) + "" + (int)((new Random()).nextDouble()*10000) ;
        playtimeSinceLastEventTimestamp = 0L;
        totalPlaytime = 0L;
        playtimeSinceLastEvent = 0L;
        bufferType = null;
        isHeartbeatRunning = false;
        setHeartbeatTime(30);
        heartbeatHandler = new Handler();
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHeartbeatRunning) {
                    sendHeartbeat();
                    heartbeatHandler.postDelayed(heartbeatRunnable, heartbeatTimeInterval * 1000);
                }
            }
        };
    }

    public void dispose() {
        super.dispose();
        stopHeartbeat();
    }

    public void setPlayer(Object player) {
        sendEvent(PLAYER_READY);
    }

    public void startHeartbeat() {
        NRLog.d("START HEARTBEAT");
        isHeartbeatRunning = true;
        heartbeatHandler.postDelayed(heartbeatRunnable, heartbeatTimeInterval * 1000);
    }

    public void stopHeartbeat() {
        NRLog.d("STOP HEARTBEAT");
        isHeartbeatRunning = false;
        heartbeatHandler.removeCallbacks(heartbeatRunnable, null);
    }

    public void setHeartbeatTime(int seconds) {
        if (seconds >= 1) {
            heartbeatTimeInterval = seconds;
            if (isHeartbeatRunning) {
                stopHeartbeat();
                startHeartbeat();
            }
        }
    }

    public void setNumberOfAds(int numberOfAds) {
        this.numberOfAds = numberOfAds;
    }

    public NRTrackerState getState() {
        return state;
    }

    @Override
    public Map<String, Object> getAttributes(String action, Map<String, Object> attributes) {
        Map<String, Object> attr = super.getAttributes(action, attributes);

        if (action.startsWith("CONTENT_")) {
            attr.put("playtimeSinceLastEvent", playtimeSinceLastEvent);
            attr.put("totalPlaytime", totalPlaytime);
        }

        if (action.endsWith("_BUFFER_START") || action.endsWith("_BUFFER_END")) {
            attr.put("bufferType", getBufferType());
        }

        attr.put("trackerName", getTrackerName());
        attr.put("trackerVersion", getTrackerVersion());
        attr.put("playerName", getPlayerName());
        attr.put("playerVersion", getPlayerVersion());
        attr.put("viewSession", getViewSession());
        attr.put("viewId", getViewId());
        attr.put("isAd", state.isAd);
        attr.put("numberOfAds", numberOfAds);
        attr.put("numberOfVideos", numberOfVideos);
        attr.put("numberOfErrors", numberOfErrors);

        if (state.isAd) {
            attr.put("adTitle", getTitle());
            attr.put("adBitrate", getBitrate());
            attr.put("adRenditionBitrate", getRenditionBitrate());
            attr.put("adRenditionWidth", getRenditionWidth());
            attr.put("adRenditionHeight", getRenditionHeight());
            attr.put("adDuration", getDuration());
            attr.put("adPlayhead", getPlayhead());
            attr.put("adLanguage", getLanguage());
            attr.put("adSrc", getSrc());
            attr.put("adIsMuted", getIsMuted());
            attr.put("adFps", getFps());
            attr.put("adId", getVideoId());
            attr.put("adCreativeId",getAdCreativeId());
            attr.put("adPosition", getAdPosition());
            attr.put("adQuartile", getAdQuartile());
            attr.put("adPartner", getAdPartner());
            attr.put("adBreakId", getAdBreakId());

            if (action.startsWith("AD_BREAK_")) {
                attr.remove("viewId");
            }
        }
        else {
            attr.put("contentTitle", getTitle());
            attr.put("contentBitrate", getBitrate());
            attr.put("contentRenditionBitrate", getRenditionBitrate());
            attr.put("contentRenditionWidth", getRenditionWidth());
            attr.put("contentRenditionHeight", getRenditionHeight());
            attr.put("contentDuration", getDuration());
            attr.put("contentPlayhead", getPlayhead());
            attr.put("contentLanguage", getLanguage());
            attr.put("contentSrc", getSrc());
            attr.put("contentIsMuted", getIsMuted());
            attr.put("contentFps", getFps());
            attr.put("contentId", getVideoId());
            attr.put("contentIsLive", getIsLive());
        }

        return attr;
    }

    @Override
    public void sendEvent(String action, Map<String, Object> attributes) {

        if (playtimeSinceLastEventTimestamp > 0) {
            playtimeSinceLastEvent = System.currentTimeMillis() - playtimeSinceLastEventTimestamp;
            totalPlaytime += playtimeSinceLastEvent;
            playtimeSinceLastEventTimestamp = System.currentTimeMillis();
        } else {
            playtimeSinceLastEvent = 0L;
        }

        super.sendEvent(action, attributes);
    }

    public void sendRequest() {
        if (state.goRequest()) {
            playtimeSinceLastEventTimestamp = 0L;
            viewIdIndex++;

            if (state.isAd) {
                sendEvent(AD_REQUEST);
            } else {
                sendEvent(CONTENT_REQUEST);
            }
        }
    }

    public void sendStart() {
        if (state.goStart()) {
            startHeartbeat();
            if (state.isAd) {
                numberOfAds++;
                if (linkedTracker instanceof NRVideoTracker) {
                    ((NRVideoTracker) linkedTracker).setNumberOfAds(numberOfAds);
                }
                sendEvent(AD_START);
            } else {
                numberOfVideos++;
                sendEvent(CONTENT_START);
            }
            playtimeSinceLastEventTimestamp = System.currentTimeMillis();
        }
    }

    public void sendPause() {
        if (state.goPause()) {
            if (state.isAd) {
                sendEvent(AD_PAUSE);
            } else {
                sendEvent(CONTENT_PAUSE);
            }
            playtimeSinceLastEventTimestamp = 0L;
        }
    }

    public void sendResume() {
        if (state.goResume()) {
            if (state.isAd) {
                sendEvent(AD_RESUME);
            } else {
                sendEvent(CONTENT_RESUME);
            }
            if (!state.isBuffering && !state.isSeeking) {
                playtimeSinceLastEventTimestamp = System.currentTimeMillis();
            }
        }
    }

    public void sendEnd() {
        if (state.goEnd()) {
            if (state.isAd) {
                sendEvent(AD_END);
                if (linkedTracker instanceof NRVideoTracker) {
                    ((NRVideoTracker) linkedTracker).adHappened();
                }
            } else {
                sendEvent(CONTENT_END);
            }

            stopHeartbeat();

            numberOfErrors = 0;
            playtimeSinceLastEventTimestamp = 0L;
            playtimeSinceLastEvent = 0L;
            totalPlaytime = 0L;
        }
    }

    public void sendSeekStart() {
        if (state.goSeekStart()) {
            if (state.isAd) {
                sendEvent(AD_SEEK_START);
            } else {
                sendEvent(CONTENT_SEEK_START);
            }
            playtimeSinceLastEventTimestamp = 0L;
        }
    }

    public void sendSeekEnd() {
        if (state.goSeekEnd()) {
            if (state.isAd) {
                sendEvent(AD_SEEK_END);
            } else {
                sendEvent(CONTENT_SEEK_END);
            }
            if (!state.isBuffering && !state.isPaused) {
                playtimeSinceLastEventTimestamp = System.currentTimeMillis();
            }
        }
    }

    public void sendBufferStart() {
        if (state.goBufferStart()) {
            bufferType = calculateBufferType();
            if (state.isAd) {
                sendEvent(AD_BUFFER_START);
            } else {
                sendEvent(CONTENT_BUFFER_START);
            }
            playtimeSinceLastEventTimestamp = 0L;
        }
    }

    public void sendBufferEnd() {
        if (state.goBufferEnd()) {
            if (bufferType == null) {
                bufferType = calculateBufferType();
            }
            if (state.isAd) {
                sendEvent(AD_BUFFER_END);
            } else {
                sendEvent(CONTENT_BUFFER_END);
            }
            if (!state.isSeeking && !state.isPaused) {
                playtimeSinceLastEventTimestamp = System.currentTimeMillis();
            }
            bufferType = null;
        }
    }

    public void sendHeartbeat() {
        if (state.isAd) {
            sendEvent(AD_HEARTBEAT);
        } else {
            sendEvent(CONTENT_HEARTBEAT);
        }
    }

    public void sendRenditionChange() {
        if (state.isAd) {
            sendEvent(AD_RENDITION_CHANGE);
        } else {
            sendEvent(CONTENT_RENDITION_CHANGE);
        }
    }

    public void sendError() {
        sendError((String) null);
    }

    public void sendError(Exception error) {
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

        sendError(msg);
    }

    public void sendError(String errorMessage) {
        if (errorMessage == null) {
            errorMessage = "<Unknown error>";
        }
        numberOfErrors++;
        Map<String, Object> errAttr = new HashMap<>();
        errAttr.put("errorMessage", errorMessage);

        if (state.isAd) {
            sendEvent(AD_ERROR, errAttr);
        }
        else {
            sendEvent(CONTENT_ERROR, errAttr);
        }
    }

    public void sendAdBreakStart() {
        if (state.isAd) {
            adBreakIdIndex++;
            sendEvent(AD_BREAK_START);
        }
    }

    public void sendAdBreakEnd() {
        if (state.isAd) {
            sendEvent(AD_BREAK_END);
        }
    }

    public void sendAdQuartile() {
        if (state.isAd) {
            sendEvent(AD_QUARTILE);
        }
    }

    public void sendAdClick() {
        if (state.isAd) {
            sendEvent(AD_CLICK);
        }
    }


    public Boolean getIsAd() {
        return state.isAd;
    }

    public String getTrackerVersion() {
        return null;
    }

    public String getTrackerName() {
        return null;
    }

    public String getPlayerVersion() {
        return null;
    }

    public String getPlayerName() {
        return null;
    }

    public String getTitle() {
        return null;
    }

    public Long getBitrate() {
        return null;
    }

    public Long getRenditionBitrate() {
        return null;
    }

    public Long getRenditionWidth() {
        return null;
    }

    public Long getRenditionHeight() {
        return null;
    }

    public Long getDuration() {
        return null;
    }

    public Long getPlayhead() {
        return null;
    }

    public String getLanguage() {
        return null;
    }

    public String getSrc() {
        return null;
    }

    public Boolean getIsMuted() {
        return null;
    }

    public Double getFps() {
        return null;
    }

    public Boolean getIsLive() {
        return null;
    }

    public String getAdCreativeId() {
        return null;
    }

    public String getAdPosition() {
        return null;
    }

    public Long getAdQuartile() {
        return null;
    }

    public String getAdPartner() {
        return null;
    }

    public String getAdBreakId() {
        return getViewSession() + "-" + adBreakIdIndex;
    }

    public String getViewSession() {
        return viewSessionId;
    }

    public String getViewId() {
        return getViewSession() + "-" + viewIdIndex;
    }

    public String getVideoId() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            return "";
        }
        String src = "";
        if (getSrc() != null) {
            src = getSrc();
        }
        md.update(src.getBytes());
        byte[] result = md.digest();
        return String.format("%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X",
                result[0], result[1], result[2], result[3],
                result[4], result[5], result[6], result[7],
                result[8], result[9], result[10], result[11],
                result[12], result[13], result[14], result[15]);
    }

    public String getBufferType() {
        return bufferType;
    }

    public void adHappened() {
        // Create an NRTimeSince entry without action (won't by updated by any action) and force a "now" to set the current timestamp reference
        if (lastAdTimeSince == null) {
            NRTimeSince ts = new NRTimeSince("", "timeSinceLastAd", "^CONTENT_[A-Z_]+$");
            addTimeSinceEntry(ts);
            lastAdTimeSince = ts;
        }
        lastAdTimeSince.now();
    }

    @Override
    public void generateTimeSinceTable() {
        super.generateTimeSinceTable();

        addTimeSinceEntry(CONTENT_HEARTBEAT, "timeSinceLastHeartbeat", "^CONTENT_[A-Z_]+$");
        addTimeSinceEntry(AD_HEARTBEAT, "timeSinceLastAdHeartbeat", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_REQUEST, "timeSinceRequested", "^CONTENT_[A-Z_]+$");
        addTimeSinceEntry(AD_REQUEST, "timeSinceAdRequested", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_START, "timeSinceStarted", "^CONTENT_[A-Z_]+$");
        addTimeSinceEntry(AD_START, "timeSinceAdStarted", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_PAUSE, "timeSincePaused", "^CONTENT_RESUME$");
        addTimeSinceEntry(AD_PAUSE, "timeSinceAdPaused", "^AD_RESUME$");

        addTimeSinceEntry(CONTENT_RESUME, "timeSinceResumed", "^CONTENT_BUFFER_(START|END)$");
        addTimeSinceEntry(AD_RESUME, "timeSinceAdResumed", "^AD_BUFFER_(START|END)$");

        addTimeSinceEntry(CONTENT_SEEK_START, "timeSinceSeekBegin", "^CONTENT_SEEK_END$");
        addTimeSinceEntry(AD_SEEK_START, "timeSinceAdSeekBegin", "^AD_SEEK_END$");

        addTimeSinceEntry(CONTENT_SEEK_END, "timeSinceSeekEnd", "^CONTENT_BUFFER_(START|END)$");
        addTimeSinceEntry(AD_SEEK_END, "timeSinceAdSeekEnd", "^AD_BUFFER_(START|END)$");

        addTimeSinceEntry(CONTENT_BUFFER_START, "timeSinceBufferBegin", "^CONTENT_BUFFER_END$");
        addTimeSinceEntry(AD_BUFFER_START, "timeSinceAdBufferBegin", "^AD_BUFFER_END$");

        addTimeSinceEntry(CONTENT_ERROR, "timeSinceLastError", "^CONTENT_[A-Z_]+$");
        addTimeSinceEntry(AD_ERROR, "timeSinceLastAdError", "^AD_[A-Z_]+$");

        addTimeSinceEntry(CONTENT_RENDITION_CHANGE, "timeSinceLastRenditionChange", "^CONTENT_RENDITION_CHANGE$");
        addTimeSinceEntry(AD_RENDITION_CHANGE, "timeSinceLastAdRenditionChange", "^AD_RENDITION_CHANGE$");

        addTimeSinceEntry(AD_BREAK_START, "timeSinceAdBreakBegin", "^AD_BREAK_END$");

        addTimeSinceEntry(AD_QUARTILE, "timeSinceLastAdQuartile", "^AD_QUARTILE$");
    }

    private String calculateBufferType() {
        Long playhead = getPlayhead();

        if (playhead == null) {
            playhead = 0L;
        }

        if (state.isSeeking) {
            return "seek";
        }

        if (state.isPaused) {
            return "pause";
        }

        //NOTE: the player starts counting contentPlayhead after buffering ends, and by the time we calculate BUFFER_END, playhead can be a bit higher than zero (few milliseconds).
        if (playhead < 10) {
            return "initial";
        }

        // If none of the above is true, it is a connection buffering
        return "connection";
    }
}