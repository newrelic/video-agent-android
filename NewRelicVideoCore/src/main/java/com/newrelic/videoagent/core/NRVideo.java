package com.newrelic.videoagent.core;

import java.util.Map;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import androidx.media3.exoplayer.ExoPlayer;
import com.newrelic.videoagent.core.harvest.HarvestManager;
import com.newrelic.videoagent.core.lifecycle.NRVideoLifecycleObserver;
import com.newrelic.videoagent.core.storage.CrashSafeHarvestFactory;
import com.newrelic.videoagent.core.tracker.NRTracker;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;

/**
 * New Relic Video Agent - Android Mobile & TV Optimized
 * Singleton pattern with Builder for robust initialization
 * Supports ExoPlayer and IMA with automatic device detection
 */
public final class NRVideo {
    private static final String TAG = "NRVideo";

    // Singleton instance
    private static volatile NRVideo instance;
    private static final Object lock = new Object();

    private volatile HarvestManager harvestManager;
    private volatile boolean isInitialized = false;

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
        return instance != null && instance.isInitialized;
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
        if (instance != null && instance.isInitialized) {
            instance.harvestManager.recordCustomEvent(eventType, attributes);
        } else {
            Log.w(TAG, "recordEvent called before NRVideo is fully initialized - event dropped");
        }
    }

    /**
     * Static convenience method for recording custom video events
     *
     * @param attributes A map of attributes for the event.
     */
    public static void recordCustomEvent(Map<String, Object> attributes) {
        recordEvent("VideoCustomAction", attributes);
    }

    /**
     * Builder pattern for robust NRVideo initialization
     */
    public static class Builder {
        private final Context context;
        private NRVideoConfiguration config;
        private ExoPlayer player;

        private Builder(Context context) {
            this.context = context.getApplicationContext();
        }

        public Builder withConfiguration(NRVideoConfiguration config) {
            this.config = config;
            return this;
        }

        public Builder withPlayer(ExoPlayer player) {
            this.player = player;
            return this;
        }

        /**
         * Build and initialize NRVideo singleton
         * @return The tracker ID
         * @throws IllegalStateException if required parameters are missing
         * @throws RuntimeException if initialization fails or already initialized
         */
        public Integer build() {
            if (config == null) {
                throw new IllegalStateException("Configuration is required - call withConfiguration()");
            }
            if (player == null) {
                throw new IllegalStateException("ExoPlayer is required - call withPlayer()");
            }

            // Check if already initialized (fast path - no lock needed)
            if (instance != null && instance.isInitialized) {
                throw new RuntimeException("NRVideo is already initialized. Multiple initialization attempts are not allowed.");
            }

            synchronized (lock) {
                // Double-check after acquiring lock
                if (instance != null && instance.isInitialized) {
                    throw new RuntimeException("NRVideo is already initialized. Multiple initialization attempts are not allowed.");
                }

                instance = new NRVideo();
                return instance.initialize(player, context, config);
            }
        }
    }

    private Integer initialize(ExoPlayer player, Context context, NRVideoConfiguration config) {
        try {
            Context applicationContext = context.getApplicationContext();

            // Always use crash-safe storage - it's now the default behavior
            CrashSafeHarvestFactory factory = new CrashSafeHarvestFactory(config, applicationContext);
            harvestManager = new HarvestManager(factory);

            // Create and register lifecycle observer with crash-safe factory
            if (applicationContext instanceof Application) {
                Application app = (Application) applicationContext;
                NRVideoLifecycleObserver lifecycleObserver =
                    new NRVideoLifecycleObserver(
                        applicationContext,
                        config,
                        harvestManager,
                        factory.createScheduler(harvestManager::harvestOnDemand, harvestManager::harvestLive),
                        factory
                    );

                // Register with application
                app.registerActivityLifecycleCallbacks(lifecycleObserver);

                if (config.isDebugLoggingEnabled()) {
                    Log.d(TAG, "Lifecycle observer created and registered with crash-safe storage");
                }
            }

            // Create trackers
            NRTracker adsTracker = null;
            NRTracker tracker = createContentTracker(config);
            if (config.isAdTrackingEnabled()) {
                adsTracker = createAdTracker(config);
            }
            ((NRVideoTracker) tracker).setPlayer(player);

            // Mark as fully initialized BEFORE starting trackers
            isInitialized = true;

            // Now start the tracker system
            Integer trackerId = NewRelicVideoAgent.getInstance().start(adsTracker, tracker);

            if (config.isDebugLoggingEnabled()) {
                Log.d(TAG, "NRVideo initialization completed successfully with tracker ID: " + trackerId);
            }

            return trackerId;
        } catch (Exception e) {
            // Clean up on failure
            instance = null;
            throw new RuntimeException("Failed to initialize NRVideo components", e);
        }
    }

    private NRTracker createContentTracker(NRVideoConfiguration config) {
        try {
            // Create ExoPlayer tracker with player instance
            Class<?> exoTrackerClass = Class.forName("com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer");
            return (NRTracker) exoTrackerClass.newInstance();
        } catch (Exception e) {
            if (config.isDebugLoggingEnabled()) {
                Log.e(TAG, "Failed to create specific tracker, using default: " + e.getMessage());
            }
            // Fallback to basic video tracker
            throw new RuntimeException("Failed to create NRTrackerExoPlayer", e);
        }
    }

    private NRTracker createAdTracker(NRVideoConfiguration config) {

        try {
            // Always use IMA tracker for ads
            Class<?> imaTrackerClass = Class.forName("com.newrelic.videoagent.ima.tracker.NRTrackerIMA");
            return (NRTracker) imaTrackerClass.newInstance();
        } catch (Exception e) {
            if (config.isDebugLoggingEnabled()) {
                Log.e(TAG, "Failed to create IMA ad tracker: " + e.getMessage());
            }
            return null; // No ad tracking if IMA tracker can't be created
        }
    }

    /**
     * Sets the user ID.
     *
     * @param userId The user ID.
     */
    public void setUserId(String userId) {
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
    public void setAttribute(Integer trackerId, String key, Object value, String action) {
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
    public void setAdAttribute(Integer trackerId, String key, Object value, String action) {
        NewRelicVideoAgent.getInstance().setAdAttribute(trackerId, key, value, action);
    }

    /**
     * Sets a global attribute.
     *
     * @param key The attribute key.
     * @param value The attribute value.
     * @param action The action name to associate with the attribute.
     */
    public void setGlobalAttribute(String key, Object value, String action) {
        NewRelicVideoAgent.getInstance().setGlobalAttribute(key, value, action);
    }

    /**
     * Sets an attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     */
    public void setAttribute(Integer trackerId, String key, Object value) {
        NewRelicVideoAgent.getInstance().setAttribute(trackerId, key, value);
    }

    /**
     * Sets an ad attribute for a specific tracker.
     *
     * @param trackerId The tracker ID.
     * @param key The attribute key.
     * @param value The attribute value.
     */
    public void setAdAttribute(Integer trackerId, String key, Object value) {
        NewRelicVideoAgent.getInstance().setAdAttribute(trackerId, key, value);
    }

    /**
     * Sets a global attribute.
     *
     * @param key The attribute key.
     * @param value The attribute value.
     */
    public void setGlobalAttribute(String key, Object value) {
        NewRelicVideoAgent.getInstance().setGlobalAttribute(key, value);
    }
}
