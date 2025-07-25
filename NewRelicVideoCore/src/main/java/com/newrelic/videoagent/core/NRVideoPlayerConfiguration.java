package com.newrelic.videoagent.core;

import androidx.media3.exoplayer.ExoPlayer;

import java.util.Map;

public class NRVideoPlayerConfiguration {
    private static final String TAG = "NRVideo.PlayerConfiguration";
    private String playerName;
    private ExoPlayer player;
    private Map<String, Object> customAttributes;
    private boolean isAdEnabled = Boolean.FALSE;

    public NRVideoPlayerConfiguration(String playerName, ExoPlayer player, boolean isAdEnabled, Map<String, Object> customAttributes) {
        this.playerName = playerName;
        this.player = player;
        this.customAttributes = customAttributes;
        this.isAdEnabled = isAdEnabled;
    }
    public String getPlayerName() {
        return playerName;
    }
    public ExoPlayer getPlayer() {
        return player;
    }
    public Map<String, Object> getCustomAttributes() {
        return customAttributes;
    }
    public boolean isAdEnabled() {
        return isAdEnabled;
    }
}
