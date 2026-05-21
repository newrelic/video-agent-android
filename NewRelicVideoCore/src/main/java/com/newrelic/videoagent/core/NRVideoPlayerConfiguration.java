package com.newrelic.videoagent.core;

import androidx.media3.exoplayer.ExoPlayer;

import java.util.Map;

public class NRVideoPlayerConfiguration {

    /**
     * Selects which ad tracker implementation is paired with the content
     * tracker. Mutually exclusive per player — only one ad tracker slot
     * exists in {@code NRTrackerPair}.
     */
    public enum AdTrackerType {
        NONE,
        IMA,
        MEDIA_TAILOR
    }

    private final String playerName;
    private final ExoPlayer player;
    private final Map<String, Object> customAttributes;
    private final AdTrackerType adTrackerType;

    public NRVideoPlayerConfiguration(String playerName, ExoPlayer player, boolean isAdEnabled, Map<String, Object> customAttributes) {
        this(playerName, player, isAdEnabled ? AdTrackerType.IMA : AdTrackerType.NONE, customAttributes);
    }

    public NRVideoPlayerConfiguration(String playerName, ExoPlayer player, AdTrackerType adTrackerType, Map<String, Object> customAttributes) {
        this.playerName = playerName;
        this.player = player;
        this.adTrackerType = adTrackerType != null ? adTrackerType : AdTrackerType.NONE;
        this.customAttributes = customAttributes;
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

    public AdTrackerType getAdTrackerType() {
        return adTrackerType;
    }

    /**
     * @deprecated Use {@link #getAdTrackerType()}. Kept for backward compat —
     * returns {@code true} when any ad tracker is selected.
     */
    @Deprecated
    public boolean isAdEnabled() {
        return adTrackerType != AdTrackerType.NONE;
    }
}
