package com.newrelic.videoagent.core;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import android.content.Context;
import androidx.media3.exoplayer.ExoPlayer;
import com.newrelic.videoagent.core.harvest.HarvestManager;
import com.newrelic.videoagent.core.harvest.HarvestComponentFactory;
import com.newrelic.videoagent.core.storage.CrashSafeHarvestFactory;
import com.newrelic.videoagent.core.tracker.NRTracker;

/**
 * New Relic Video Agent - Android Mobile & TV Optimized
 * Supports ExoPlayer and IMA with automatic device detection
 */
public final class NRVideo {
    private static final AtomicReference<NRVideo> instanceRef = new AtomicReference<>();
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final Context context;
    private final NRVideoConfiguration configuration;
    private final ExoPlayer player;
    private volatile HarvestManager harvestManager;
    private volatile NewRelicVideoAgent videoAgent;
    private volatile Integer trackerId;

    // Private constructor for player-specific initialization
    private NRVideo(ExoPlayer player, Context context, NRVideoConfiguration config) {
        this.context = context;
        this.configuration = config;
        this.player = player;
    }

    /**
     * Start the video agent
     */
    public NRVideo start() {
        if (isActive.compareAndSet(false, true)) {
            NRVideo current = instanceRef.getAndSet(this);
            if (current != null && current.isActive.get()) {
                current.stop();
            }

            try {
                initialize();
                startTracking();

                if (configuration.isDebugLoggingEnabled()) {
                    System.out.println("[NRVideo] Started - Device: " + (isTVDevice() ? "TV" : "Mobile") +
                                     ", Player: " + configuration.getPlayerType() +
                                     ", Ads: " + configuration.isAdTrackingEnabled());
                }
            } catch (Exception e) {
                isActive.set(false);
                throw new RuntimeException("Failed to start NRVideo: " + e.getMessage(), e);
            }
        }
        return this;
    }

    /**
     * Stop the video agent
     */
    public void stop() {
        if (isActive.compareAndSet(true, false)) {
            try {
                if (trackerId != null && videoAgent != null) {
                    videoAgent.releaseTracker(trackerId);
                    trackerId = null;
                }

                if (harvestManager != null) {
                    harvestManager.forceHarvestAll();
                }

                instanceRef.compareAndSet(this, null);
            } catch (Exception e) {
                System.err.println("[NRVideo] Error during stop: " + e.getMessage());
            }
        }
    }

    /**
     * Start video tracking with appropriate tracker based on configuration
     */
    public Integer startTracking() {
        ensureInitialized();

        if (trackerId == null && videoAgent != null) {
            NRTracker contentTracker = createContentTracker();
            NRTracker adTracker = configuration.isAdTrackingEnabled() ? createAdTracker() : null;

            // Pass both trackers to NewRelicVideoAgent
            trackerId = videoAgent.start(contentTracker, adTracker);

            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[NRVideo] Started tracking - ID: " + trackerId +
                                 ", Content: " + (contentTracker != null ? contentTracker.getClass().getSimpleName() : "null") +
                                 ", Ads: " + (adTracker != null ? adTracker.getClass().getSimpleName() : "disabled"));
            }
        }

        return trackerId;
    }

    /**
     * Get content tracker for video events
     */
    public NRTracker getContentTracker() {
        if (trackerId != null && videoAgent != null) {
            return videoAgent.getContentTracker(trackerId);
        }
        return null;
    }

    /**
     * Get ad tracker for ad events (only if ads are enabled)
     */
    public NRTracker getAdTracker() {
        if (trackerId != null && videoAgent != null && configuration.isAdTrackingEnabled()) {
            return videoAgent.getAdTracker(trackerId);
        }
        return null;
    }

    /**
     * Record custom video event
     */
    public void recordCustomEvent(String eventType, Map<String, Object> attributes) {
        ensureInitialized();
        if (harvestManager != null) {
            harvestManager.recordCustomEvent(eventType, attributes);
        }
    }

    /**
     * Check if video agent is active
     */
    public boolean isActive() {
        return isActive.get();
    }

    /**
     * Get configuration
     */
    public NRVideoConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Get tracker ID
     */
    public Integer getTrackerId() {
        return trackerId;
    }

    // ===========================================
    // APP LIFECYCLE (for crash safety)
    // ===========================================

    /**
     * Call when app goes to background
     */
    public void onAppBackgrounded() {
        if (harvestManager != null) {
            harvestManager.forceHarvestAll();
        }
    }

    /**
     * Emergency shutdown for current instance
     */
    public static void emergencyShutdownCurrent() {
        NRVideo current = instanceRef.get();
        if (current != null && current.harvestManager != null) {
            current.harvestManager.forceHarvestAll();
        }
    }

    private void initialize() {
        try {
            HarvestComponentFactory factory;
            if (configuration.isCrashSafety() && context != null) {
                factory = new CrashSafeHarvestFactory(configuration, context);
            } else {
                factory = new HarvestComponentFactory(configuration, context);
            }

            harvestManager = new HarvestManager(factory);
            videoAgent = NewRelicVideoAgent.getInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NRVideo components", e);
        }
    }

    /**
     * Create content tracker based on player type
     */
    private NRTracker createContentTracker() {
        try {
            switch (configuration.getPlayerType()) {
                case EXOPLAYER:
                    if (player != null) {
                        // Create ExoPlayer tracker with player instance
                        Class<?> exoTrackerClass = Class.forName("com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer");
                        return (NRTracker) exoTrackerClass.getConstructor(Object.class).newInstance(player);
                    } else {
                        // Create basic video tracker if no player instance provided
                        return new com.newrelic.videoagent.core.tracker.NRVideoTracker();
                    }

                case IMA:
                    // IMA is primarily for ads, but can also handle content
                    if (player != null) {
                        Class<?> imaTrackerClass = Class.forName("com.newrelic.videoagent.ima.tracker.NRTrackerIMA");
                        return (NRTracker) imaTrackerClass.getConstructor(Object.class).newInstance(player);
                    } else {
                        return new com.newrelic.videoagent.core.tracker.NRVideoTracker();
                    }

                default:
                    return new com.newrelic.videoagent.core.tracker.NRVideoTracker();
            }
        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                System.err.println("[NRVideo] Failed to create specific tracker, using default: " + e.getMessage());
            }
            // Fallback to basic video tracker
            return new com.newrelic.videoagent.core.tracker.NRVideoTracker();
        }
    }

    /**
     * Create ad tracker (IMA) if ads are enabled
     */
    private NRTracker createAdTracker() {
        if (!configuration.isAdTrackingEnabled()) {
            return null;
        }

        try {
            // Always use IMA tracker for ads
            Class<?> imaTrackerClass = Class.forName("com.newrelic.videoagent.ima.tracker.NRTrackerIMA");
            if (player != null) {
                return (NRTracker) imaTrackerClass.getConstructor(Object.class).newInstance(player);
            } else {
                return (NRTracker) imaTrackerClass.newInstance();
            }
        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                System.err.println("[NRVideo] Failed to create IMA ad tracker: " + e.getMessage());
            }
            return null; // No ad tracking if IMA tracker can't be created
        }
    }

    private void ensureInitialized() {
        if (!isActive.get()) {
            throw new IllegalStateException("NRVideo is not started. Call start() first.");
        }
        if (harvestManager == null) {
            throw new IllegalStateException("HarvestManager not initialized");
        }
    }

}
