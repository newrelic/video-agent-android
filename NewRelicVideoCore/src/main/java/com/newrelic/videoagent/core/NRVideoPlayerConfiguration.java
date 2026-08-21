package com.newrelic.videoagent.core;

import androidx.annotation.Nullable;

import com.newrelic.videoagent.core.tracker.NRVideoTracker;

import java.util.Map;

/**
 * Per-player configuration passed to {@link NRVideo#addPlayer}.
 *
 * <p>Combines the player instance, an optional ad configuration, and optional
 * custom attributes into a single object.</p>
 *
 * <pre>
 *   // No ads
 *   new NRVideoPlayerConfiguration("player", exoPlayer, null, null);
 *
 *   // Google IMA
 *   new NRVideoPlayerConfiguration("player", exoPlayer, NRAdConfig.csai(), null);
 *
 *   // AWS MediaTailor — default detection
 *   new NRVideoPlayerConfiguration("player", exoPlayer, NRAdConfig.mediaTailor(), null);
 *
 *   // AWS MediaTailor — custom CDN
 *   new NRVideoPlayerConfiguration("player", exoPlayer, NRAdConfig.mediaTailor("/tm/"), null);
 * </pre>
 */
public class NRVideoPlayerConfiguration {

    /**
     * Ad tracker selector — kept for source compatibility with integrations
     * built against v4.2.0 and earlier.
     *
     * @deprecated Construct an {@link NRAdConfig} via its factory methods and
     * pass it to {@link #NRVideoPlayerConfiguration(String, ExoPlayer, NRAdConfig, Map)}.
     * <pre>
     *   AdTrackerType.NONE          →  null
     *   AdTrackerType.IMA           →  NRAdConfig.csai()
     *   AdTrackerType.MEDIA_TAILOR  →  NRAdConfig.mediaTailor()
     * </pre>
     */
    @Deprecated
    public enum AdTrackerType {
        NONE,
        /** @deprecated Use {@link NRAdConfig#csai()} */
        IMA,
        /** @deprecated Use {@link NRAdConfig#mediaTailor()} */
        MEDIA_TAILOR
    }

    public static final String PLAYER_TYPE_EXO  = "exo";
    public static final String PLAYER_TYPE_THEO = "theo";

    private final String playerName;
    private final String playerType;
    private final Object player;
    private final NRVideoTracker tracker;
    private final NRAdConfig adConfig;
    private final Map<String, Object> customAttributes;

    /**
     * @param playerName       Display name for this player — used as a key in
     *                         tracker ID lookups.
     * @param player           ExoPlayer instance to track.
     * @param adConfig         Ad framework configuration. Use {@link NRAdConfig#csai()}
     *                         for IMA or {@link NRAdConfig#mediaTailor()} for MediaTailor.
     *                         Pass {@code (NRAdConfig) null} to disable ad tracking
     *                         (the cast is required to resolve overload ambiguity).
     * @param customAttributes Optional key-value pairs attached to every event
     *                         emitted by this player's tracker. Pass {@code null}
     *                         if not needed.
     */
    /** Approach 1: pre-built tracker — customer creates tracker explicitly. */
    public NRVideoPlayerConfiguration(String playerName,
                                       NRVideoTracker tracker,
                                       @Nullable NRAdConfig adConfig,
                                       @Nullable Map<String, Object> customAttributes) {
        this.playerName       = playerName;
        this.playerType       = null;
        this.player           = null;
        this.tracker          = tracker;
        this.adConfig         = adConfig;
        this.customAttributes = customAttributes;
    }

    /** Config-driven: customer passes playerType string ("exo" or "theo"). */
    public NRVideoPlayerConfiguration(String playerName,
                                       String playerType,
                                       Object player,
                                       @Nullable NRAdConfig adConfig,
                                       @Nullable Map<String, Object> customAttributes) {
        this.playerName       = playerName;
        this.playerType       = playerType;
        this.player           = player;
        this.tracker          = null;
        this.adConfig         = adConfig;
        this.customAttributes = customAttributes;
    }

    /** Legacy: pass player instance directly — NRVideo creates NRTrackerExoPlayer internally. */
    public NRVideoPlayerConfiguration(String playerName,
                                       Object player,
                                       @Nullable NRAdConfig adConfig,
                                       @Nullable Map<String, Object> customAttributes) {
        this.playerName       = playerName;
        this.playerType       = null;
        this.player           = player;
        this.tracker          = null;
        this.adConfig         = adConfig;
        this.customAttributes = customAttributes;
    }

    /**
     * @deprecated Use {@link #NRVideoPlayerConfiguration(String, ExoPlayer, NRAdConfig, Map)}
     * with {@link NRAdConfig#csai()} for ads or {@code null} for no ads.
     */
    @Deprecated
    public NRVideoPlayerConfiguration(String playerName,
                                       Object player,
                                       boolean isAdEnabled,
                                       @Nullable Map<String, Object> customAttributes) {
        this(playerName, player, isAdEnabled ? NRAdConfig.csai() : null, customAttributes);
    }

    /**
     * @deprecated Use {@link #NRVideoPlayerConfiguration(String, ExoPlayer, NRAdConfig, Map)}.
     * Map values: {@code NONE} → {@code null}, {@code IMA} → {@link NRAdConfig#csai()},
     * {@code MEDIA_TAILOR} → {@link NRAdConfig#mediaTailor()}.
     */
    @Deprecated
    public NRVideoPlayerConfiguration(String playerName,
                                       Object player,
                                       AdTrackerType adTrackerType,
                                       @Nullable Map<String, Object> customAttributes) {
        this(playerName, player, toAdConfig(adTrackerType), customAttributes);
    }

    private static NRAdConfig toAdConfig(AdTrackerType type) {
        if (type == null || type == AdTrackerType.NONE) return null;
        if (type == AdTrackerType.MEDIA_TAILOR) return NRAdConfig.mediaTailor();
        return NRAdConfig.csai(); // IMA and any future values default to CSAI
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerType() {
        return playerType;
    }

    public Object getPlayer() {
        return player;
    }

    public NRVideoTracker getTracker() {
        return tracker;
    }

    /** Returns the ad configuration, or {@code null} if ad tracking is disabled. */
    @Nullable
    public NRAdConfig getAdConfig() {
        return adConfig;
    }

    @Nullable
    public Map<String, Object> getCustomAttributes() {
        return customAttributes;
    }

    /**
     * @deprecated Use {@link #getAdConfig()} instead.
     */
    @Deprecated
    public AdTrackerType getAdTrackerType() {
        if (adConfig == null) return AdTrackerType.NONE;
        if (adConfig.type == NRAdConfig.Type.SSAI_MT) return AdTrackerType.MEDIA_TAILOR;
        return AdTrackerType.IMA;
    }

    /**
     * @deprecated Use {@code getAdConfig() != null} instead.
     */
    @Deprecated
    public boolean isAdEnabled() {
        return adConfig != null;
    }
}
