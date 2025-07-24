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
 * Supports ExoPlayer and IMA with automatic device detection
 */
public final class NRVideo {
    private static final String TAG = "NRVideo";

    private volatile HarvestManager harvestManager;

    /**
     * Sets up the New Relic Video agent.
     *
     * @param player The ExoPlayer instance.
     * @param context The application context.
     * @param config The video agent configuration.
     * @return The tracker ID.
     */
    public Integer setUP(ExoPlayer player, Context context, NRVideoConfiguration config) {
        return initialize(player, context, config);
    }

    /**
     * Records a custom event.
     *
     * @param eventType The event type.
     * @param attributes A map of attributes for the event.
     */
    public void recordEvent(String eventType, Map<String, Object> attributes) {
        if (harvestManager != null) {
            harvestManager.recordCustomEvent(eventType, attributes);
        }
    }

    private Integer initialize(ExoPlayer player, Context context, NRVideoConfiguration config) {
        try {
            Context applicationContext = context.getApplicationContext();

            // Always use crash-safe storage - it's now the default behavior
            CrashSafeHarvestFactory factory = new CrashSafeHarvestFactory(config, applicationContext);
            harvestManager = new HarvestManager(factory);

            // CRITICAL: Inject this NRVideo instance into NRTracker so trackers can record events
            NRTracker.injectVideoAgent(this);

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

            return NewRelicVideoAgent.getInstance().start(adsTracker, tracker);
        } catch (Exception e) {
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
