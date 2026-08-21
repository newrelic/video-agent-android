package com.newrelic.videoagent.core;

import java.util.HashMap;
import java.util.Map;
import android.app.Application;
import android.content.Context;
import com.newrelic.videoagent.core.harvest.HarvestManager;
import com.newrelic.videoagent.core.lifecycle.NRVideoLifecycleObserver;
import com.newrelic.videoagent.core.tracker.NRTracker;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;

/**
 * New Relic Video Agent - Android Mobile & TV Optimized
 * Singleton pattern with Builder for robust initialization
 * Supports ExoPlayer and IMA with automatic device detection
 */
public final class NRVideo {

    // Singleton instance
    private static volatile NRVideo instance;
    private static final Object lock = new Object();

    private volatile HarvestManager harvestManager;
    private volatile NRVideoConfiguration configuration;
    private final Map<String, Integer> trackerIds = new HashMap<>();

    // Private constructor for singleton
    private NRVideo() {}

    /**
     * Get the singleton instance
     * @return NRVideo instance, or null if not initialized yet
     */
    public static NRVideo getInstance() {
        return instance;
    }

    /**
     * Check if NRVideo is initialized and ready for use
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Get configured harvest cycle in seconds
     * @return harvest cycle seconds, or default 60 if not initialized
     */
    public static int getHarvestCycleSeconds() {
        if (instance != null && instance.harvestManager != null) {
            return instance.harvestManager.getFactory().getConfiguration().getHarvestCycleSeconds();
        }
        return 60; // Default harvest cycle
    }

    /**
     * Get the HarvestManager instance for QOE provider registration
     * @return HarvestManager instance, or null if not initialized
     */
    public HarvestManager getHarvestManager() {
        return harvestManager;
    }

    public NRVideoConfiguration getConfiguration() {
        return configuration;
    }

    public static Integer addPlayer(NRVideoPlayerConfiguration config) {
        if (!isInitialized()) {
            NRLog.w("NRVideo not initialized - cannot add player");
            throw new IllegalStateException("NRVideo is not initialized. Call NRVideo.newBuilder(context).withConfiguration(config).build() first.");
        }

        NRTracker contentTracker;

        if (config.getTracker() != null) {
            // ── Path A: pre-built tracker supplied directly (Approach 1 — explicit construction)
            contentTracker = config.getTracker();
            NRLog.d("[NRVideo] using pre-built tracker: " + contentTracker.getClass().getSimpleName());

        } else if (config.getPlayerType() != null) {
            // ── Path B: config-driven — resolve tracker class from playerType string
            contentTracker = createTrackerForType(
                    config.getPlayerType(), instance.configuration, config.getPlayer());
            NRLog.d("[NRVideo] config-driven tracker resolved for playerType='" + config.getPlayerType() + "'");

        } else {
            // ── Path C: legacy — create NRTrackerExoPlayer internally via reflection
            contentTracker = createContentTracker(instance.configuration);
            ((NRVideoTracker) contentTracker).setPlayer(config.getPlayer());
            NRLog.d("[NRVideo] created NRTrackerExoPlayer for ExoPlayer instance (legacy path)");
        }

        NRTracker adsTracker = null;
        NRAdConfig adConfig = config.getAdConfig();
        if (adConfig != null) {
            adsTracker = createAdTracker(instance.configuration, adConfig);
            NRLog.d("[NRVideo] ad tracker created for " + adConfig);
        } else {
            NRLog.d("[NRVideo] no adConfig supplied — ad tracking disabled for player '"
                    + config.getPlayerName() + "'");
        }

        Integer trackerId = NewRelicVideoAgent.getInstance().start(contentTracker, adsTracker);

        // MediaTailor registers a Player.Listener so it needs the player reference.
        // IMA wires via AdEventListener externally and does not need setPlayer here.
        if (adsTracker instanceof NRVideoTracker
                && adConfig != null
                && adConfig.type == NRAdConfig.Type.SSAI_MT) {
            ((NRVideoTracker) adsTracker).setPlayer(config.getPlayer());
        }
        NRLog.i("NRVideo initialization completed successfully with tracker ID: " + trackerId + " and player name:" + config.getPlayerName());
        if (config.getCustomAttributes() != null && !config.getCustomAttributes().isEmpty()) {
            for (Map.Entry<String, Object> entry : config.getCustomAttributes().entrySet()) {
                NRVideo.setAttribute(trackerId, entry.getKey(), entry.getValue());
                NRLog.d("Set custom attribute for tracker " + trackerId + ": " + entry.getKey() + " = " + entry.getValue());
            }
        }
        NRVideo.getInstance().trackerIds.put(config.getPlayerName(),  trackerId);
        return trackerId;
    }

    public static void releaseTracker(Integer trackerId) {
        if (!isInitialized()) {
            NRLog.w("NRVideo not initialized - cannot release tracker");
            throw new IllegalStateException("NRVideo is not initialized. Call NRVideo.newBuilder(context).withConfiguration(config).build() first.");
        }
        NewRelicVideoAgent.getInstance().releaseTracker(trackerId);
        NRLog.i("Released tracker with ID: " + trackerId);
    }

    public static void releaseTracker(String playerName) {
        if (!isInitialized()) {
            NRLog.w("NRVideo not initialized - cannot release tracker");
            throw new IllegalStateException("NRVideo is not initialized. Call NRVideo.newBuilder(context).withConfiguration(config).build() first.");
        }
        Integer trackerId = NRVideo.getInstance().trackerIds.get(playerName);
        if (trackerId != null) {
            NewRelicVideoAgent.getInstance().releaseTracker(trackerId);
        }
        NRLog.i("Released tracker with ID: " + trackerId);
    }

    /**
     * Create a new builder for setting up NRVideo
     * @param context The application context
     * @return Builder instance
     */
    public static Builder newBuilder(Context context) {
        return new Builder(context);
    }

    /**
     * Static convenience method for recording events without needing getInstance()
     *
     * @param eventType The event type.
     * @param attributes A map of attributes for the event.
     */
    public static void recordEvent(String eventType, Map<String, Object> attributes) {
        if (isInitialized()) {
            instance.harvestManager.recordEvent(eventType, attributes);
        } else {
            NRLog.w("recordEvent called before NRVideo is fully initialized - event dropped");
        }
    }

    /**
     * Static convenience method for recording custom video events to all active trackers
     *
     * @param attributes A map of attributes for the event. Must contain an "actionName" key.
     */
    public static void recordCustomEvent(Map<String, Object> attributes) {
        recordCustomEvent(attributes, null);
    }

    /**
     * Static convenience method for recording custom video events with tracker ID support
     *
     * @param attributes A map of attributes for the event. Must contain an "action" key.
     * @param trackerId The tracker ID to send the event to. If null, event is sent to all active trackers.
     */
    public static void recordCustomEvent(Map<String, Object> attributes, Integer trackerId) {
        if (!isInitialized()) {
            NRLog.w("recordCustomEvent called before NRVideo is fully initialized - event dropped");
            return;
        }

        if (attributes == null || attributes.isEmpty()) {
            NRLog.w("Attributes parameter is mandatory for custom events");
            return;
        }

        // Extract and validate action from attributes
        Object actionObj = attributes.get("actionName");
        if (actionObj == null || actionObj.toString().isEmpty()) {
            NRLog.w("Action attribute is mandatory for custom events - must be included in attributes map with key 'actionName'");
            return;
        }
        String action = actionObj.toString();

        if (trackerId != null) {
            NRTracker contentTracker = NewRelicVideoAgent.getInstance().getContentTracker(trackerId);
            if (contentTracker != null) {
                contentTracker.sendEvent(action, attributes);
            }
        } else {
            // Global event - send to all trackers
            NRVideo videoInstance = getInstance();
            if (videoInstance == null || videoInstance.trackerIds.isEmpty()) {
                return;
            }

            // Send to all trackers
            for (Integer currentTrackerId : videoInstance.trackerIds.values()) {
                NRTracker contentTracker = NewRelicVideoAgent.getInstance().getContentTracker(currentTrackerId);
                if (contentTracker != null) {
                    contentTracker.sendEvent(action, attributes);
                }
            }
        }
    }

    /**
     * Builder pattern for robust NRVideo initialization
     */
    public static class Builder {
        private final Context context;
        private NRVideoConfiguration config;

        private Builder(Context context) {
            this.context = context.getApplicationContext();
        }

        public Builder withConfiguration(NRVideoConfiguration config) {
            this.config = config;
            return this;
        }

        /**
         * Build and initialize NRVideo singleton
         * @return The tracker ID
         * @throws IllegalStateException if required parameters are missing
         * @throws RuntimeException if initialization fails or already initialized
         */
        public NRVideo build() {
            if (config == null) {
                throw new IllegalStateException("Configuration is required - call withConfiguration()");
            }
            // Check if already initialized (fast path - no lock needed)
            if (isInitialized()) {
                throw new RuntimeException("NRVideo is already initialized. Multiple initialization attempts are not allowed.");
            }

            synchronized (lock) {
                // Double-check after acquiring lock
                if (instance != null) {
                    throw new RuntimeException("NRVideo is already initialized. Multiple initialization attempts are not allowed.");
                }

                instance = new NRVideo();
                return instance.initialize(context, config);
            }
        }
    }

    private NRVideo initialize(Context context, NRVideoConfiguration config) {
        try {
            Context applicationContext = context.getApplicationContext();

            // Store configuration for tracker creation
            this.configuration = config;

            // Always use crash-safe storage - it's now the default behavior
            harvestManager = new HarvestManager(config, applicationContext);

            // Create and register lifecycle observer with crash-safe factory
            if (applicationContext instanceof Application) {
                Application app = (Application) applicationContext;
                NRVideoLifecycleObserver lifecycleObserver =
                        new NRVideoLifecycleObserver(harvestManager.getFactory());

                // Register with application
                app.registerActivityLifecycleCallbacks(lifecycleObserver);

                NRLog.d("Lifecycle observer created and registered with crash-safe storage");
            }
            if (config.isDebugLoggingEnabled()) {
                NRLog.enable();
            }
            if (config.isTV()) {
                NewRelicVideoAgent.getInstance().setTv();
            }
            return this;
        } catch (Exception e) {
            // Clean up on failure
            instance = null;
            throw new RuntimeException("Failed to initialize NRVideo components", e);
        }
    }

    private static NRTracker createTrackerForType(
            String playerType, NRVideoConfiguration config, Object playerObject) {
        String className;
        switch (playerType.toLowerCase()) {
            case NRVideoPlayerConfiguration.PLAYER_TYPE_EXO:
                className = "com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer";
                break;
            case NRVideoPlayerConfiguration.PLAYER_TYPE_THEO:
                className = "com.newrelic.videoagent.theoplayer.tracker.NRTrackerTHEOPlayer";
                break;
            default:
                throw new IllegalArgumentException(
                    "[NRVideo] Unknown playerType '" + playerType + "'. " +
                    "Use NRVideoPlayerConfiguration.PLAYER_TYPE_EXO or PLAYER_TYPE_THEO.");
        }
        try {
            Class<?> clazz = Class.forName(className);
            try {
                return (NRTracker) clazz
                        .getConstructor(NRVideoConfiguration.class, Object.class)
                        .newInstance(config, playerObject);
            } catch (NoSuchMethodException ignored) {
                NRTracker tracker = (NRTracker) clazz
                        .getConstructor(NRVideoConfiguration.class)
                        .newInstance(config);
                ((NRVideoTracker) tracker).setPlayer(playerObject);
                return tracker;
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "[NRVideo] Tracker class not found for playerType='" + playerType + "'. " +
                "Make sure you have added the correct tracker module to your build.gradle. " +
                "Expected class: " + className, e);
        } catch (Exception e) {
            throw new RuntimeException(
                "[NRVideo] Failed to create tracker for playerType='" + playerType + "'", e);
        }
    }

    private static NRTracker createContentTracker(NRVideoConfiguration config) {
        try {
            // Create ExoPlayer tracker with configuration
            Class<?> exoTrackerClass = Class.forName("com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer");
            return (NRTracker) exoTrackerClass.getConstructor(NRVideoConfiguration.class).newInstance(config);
        } catch (Exception e) {
            // Fallback to deprecated constructor for backward compatibility
            try {
                Class<?> exoTrackerClass = Class.forName("com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer");
                return (NRTracker) exoTrackerClass.newInstance();
            } catch (Exception fallbackException) {
                throw new RuntimeException("Failed to create NRTrackerExoPlayer", fallbackException);
            }
        }
    }

    private static NRTracker createAdTracker(NRVideoConfiguration config, NRAdConfig adConfig) {
        String className;
        switch (adConfig.type) {
            case SSAI_MT:
                className = "com.newrelic.videoagent.mediatailor.tracker.NRTrackerMediaTailor";
                break;
            case CSAI:
            default:
                className = "com.newrelic.videoagent.ima.tracker.NRTrackerIMA";
                break;
        }
        NRLog.d("[NRVideo] loading ad tracker class: " + className);
        try {
            Class<?> clazz = Class.forName(className);
            // Prefer the two-arg constructor (NRVideoConfiguration, NRAdConfig) so the
            // tracker receives its full configuration (segmentPrefix, trackingUrl) at
            // construction time. Fall back to the one-arg constructor for trackers
            // (e.g. NRTrackerIMA) that do not need NRAdConfig.
            try {
                return (NRTracker) clazz
                        .getConstructor(NRVideoConfiguration.class, NRAdConfig.class)
                        .newInstance(config, adConfig);
            } catch (NoSuchMethodException ignored) {
                return (NRTracker) clazz
                        .getConstructor(NRVideoConfiguration.class)
                        .newInstance(config);
            }
        } catch (Exception e) {
            NRLog.w("[NRVideo] failed to load ad tracker " + className + ": " + e);
            return null;
        }
    }

    /**
     * Sets the user ID.
     *
     * @param userId The user ID.
     */
    public static void setUserId(String userId) {
        NewRelicVideoAgent.getInstance().setUserId(userId);
    }

    /**
     * Sets an attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     * @param action The action name to associate with the attribute.
     */
    public static void setAttribute(Integer trackerId, String key, Object value, String action) {
        NewRelicVideoAgent.getInstance().setAttribute(trackerId, key, value, action);
    }

    /**
     * Sets an ad attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     * @param action The action name to associate with the attribute.
     */
    public static void setAdAttribute(Integer trackerId, String key, Object value, String action) {
        NewRelicVideoAgent.getInstance().setAdAttribute(trackerId, key, value, action);
    }

    /**
     * Sets a global attribute.
     *
     * @param key The attribute key.
     * @param value The attribute value.
     * @param action The action name to associate with the attribute.
     */
    public static void setGlobalAttribute(String key, Object value, String action) {
        NewRelicVideoAgent.getInstance().setGlobalAttribute(key, value, action);
    }

    /**
     * Sets an attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     */
    public static void setAttribute(Integer trackerId, String key, Object value) {
        NewRelicVideoAgent.getInstance().setAttribute(trackerId, key, value);
    }

    /**
     * Sets an ad attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     */
    public static void setAdAttribute(Integer trackerId, String key, Object value) {
        NewRelicVideoAgent.getInstance().setAdAttribute(trackerId, key, value);
    }

    /**
     * Sets a global attribute.
     *
     * @param key The attribute key.
     * @param value The attribute value.
     */
    public static void setGlobalAttribute(String key, Object value) {
        NewRelicVideoAgent.getInstance().setGlobalAttribute(key, value);
    }
}