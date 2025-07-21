package com.newrelic.videoagent.core;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import android.app.Application;
import com.newrelic.videoagent.core.harvest.HarvestManager;
import com.newrelic.videoagent.core.harvest.HarvestComponentFactory;
import com.newrelic.videoagent.core.lifecycle.NRVideoLifecycleObserver;

/**
 * Simplified New Relic Video Agent for mobile/TV environments
 * Uses builder pattern for flexible configuration
 * NOW WITH AUTOMATIC LIFECYCLE DETECTION - No manual integration required!
 *
 * Usage Examples:
 *
 * // Minimal setup with automatic lifecycle
 * NRVideo agent = NRVideo.start("your-token", player, application);
 *
 * // Custom configuration with automatic lifecycle
 * NRVideoConfiguration config = new NRVideoConfiguration.Builder("your-token")
 *     .liveHarvestCycle(5)
 *     .enableAdTracking(false)
 *     .build();
 * NRVideo agent = NRVideo.start(player, config, application);
 */
public final class NRVideo implements NRVideoLifecycleObserver.LifecycleListener {

    private static final AtomicReference<NRVideo> instanceRef = new AtomicReference<>();
    private final AtomicBoolean isActive = new AtomicBoolean(false);

    // Core configuration
    private final NRVideoConfiguration configuration;
    private final Object videoPlayer;
    private final Application application;

    // Runtime state
    private volatile HarvestManager harvestManager;
    private volatile NewRelicVideoAgent videoAgent;
    private volatile Integer trackerId;
    private volatile NRVideoLifecycleObserver lifecycleObserver;

    private NRVideo(Object player, NRVideoConfiguration config, Application app) {
        this.videoPlayer = player;
        this.configuration = config;
        this.application = app;
    }

    // ===========================================
    // STATIC FACTORY METHODS WITH AUTOMATIC LIFECYCLE
    // ===========================================

    /**
     * Start video agent with minimal configuration and automatic lifecycle detection
     */
    public static NRVideo start(String token, Object player, Application application) {
        NRVideoConfiguration config = NRVideoConfiguration.minimal(token);
        return start(player, config, application);
    }

    /**
     * Start video agent with custom configuration and automatic lifecycle detection (recommended)
     */
    public static NRVideo start(Object player, NRVideoConfiguration configuration, Application application) {
        if (player == null) {
            throw new IllegalArgumentException("Player is required");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration is required");
        }
        if (application == null) {
            throw new IllegalArgumentException("Application context is required for automatic lifecycle detection");
        }

        // Auto-cleanup previous instance
        NRVideo current = instanceRef.get();
        if (current != null && current.isActive.get()) {
            current.stop();
        }

        NRVideo instance = new NRVideo(player, configuration, application);
        instanceRef.set(instance);

        try {
            instance.initialize();
            instance.setupAutomaticLifecycle(); // NEW: Automatic lifecycle setup

            if (configuration.isAutoStartTracking()) {
                instance.startTracking();
            }

            instance.isActive.set(true);

            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[NRVideo] Started with automatic lifecycle - Tracker: " + instance.trackerId +
                    ", Region: " + configuration.getRegion());
            }

            return instance;

        } catch (Exception e) {
            System.err.println("[NRVideo] Startup failed: " + e.getMessage());
            instance.cleanup();
            throw new RuntimeException("NRVideo startup failed", e);
        }
    }

    /**
     * Start with live streaming optimized configuration
     */
    public static NRVideo startForLiveStreaming(String token, Object player, Application application) {
        NRVideoConfiguration config = NRVideoConfiguration.forLiveStreaming(token);
        return start(player, config, application);
    }

    /**
     * Start with on-demand content optimized configuration
     */
    public static NRVideo startForOnDemandContent(String token, Object player, Application application) {
        NRVideoConfiguration config = NRVideoConfiguration.forOnDemandContent(token);
        return start(player, config, application);
    }

    // ===========================================
    // BUILDER FOR ADVANCED CONFIGURATION
    // ===========================================

    /**
     * Get a configuration builder for advanced customization
     */
    public static NRVideoConfiguration.Builder configure(String applicationToken) {
        return new NRVideoConfiguration.Builder(applicationToken);
    }

    // ===========================================
    // INSTANCE METHODS
    // ===========================================

    /**
     * Start tracking manually (if autoStartTracking was disabled)
     */
    public void startTracking() {
        if (!isActive.get()) {
            throw new IllegalStateException("NRVideo agent not started");
        }

        if (trackerId != null) {
            System.err.println("[NRVideo] Tracking already started");
            return;
        }

        try {
            startTrackingInternal();
            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[NRVideo] Tracking started - ID: " + trackerId);
            }
        } catch (Exception e) {
            System.err.println("[NRVideo] Failed to start tracking: " + e.getMessage());
            throw new RuntimeException("Failed to start tracking", e);
        }
    }

    /**
     * Stop and cleanup
     */
    public void stop() {
        if (!isActive.get()) return;

        try {
            // CRITICAL: Harvest any remaining events before stopping
            if (harvestManager != null) {
                harvestManager.forceHarvestAll(); // Immediate harvest to prevent data loss
            }

            cleanup();
            isActive.set(false);
            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[NRVideo] Stopped with immediate harvest");
            }
        } catch (Exception e) {
            System.err.println("[NRVideo] Stop error: " + e.getMessage());
        }
    }

    /**
     * Called automatically when app goes to background - implements LifecycleListener
     */
    @Override
    public void onAppBackgrounded() {
        if (isActive.get() && harvestManager != null) {
            harvestManager.forceHarvestAll(); // Immediate harvest
            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[NRVideo] Auto-detected: App backgrounded - immediate harvest triggered");
            }
        }
    }

    /**
     * Called automatically when app comes to foreground - implements LifecycleListener
     */
    @Override
    public void onAppForegrounded() {
        if (isActive.get() && configuration.isDebugLoggingEnabled()) {
            System.out.println("[NRVideo] Auto-detected: App foregrounded - normal harvesting resumed");
        }
    }

    /**
     * NEW: Called automatically when app is terminating (crash, kill, force close)
     * This is CRITICAL for preventing data loss in emergency scenarios
     */
    @Override
    public void onAppTerminating() {
        if (isActive.get() && harvestManager != null) {
            try {
                // EMERGENCY HARVEST: Immediate, synchronous harvest to prevent data loss
                harvestManager.forceHarvestAll();

                if (configuration.isDebugLoggingEnabled()) {
                    System.out.println("[NRVideo] EMERGENCY: App terminating - immediate harvest completed");
                }
            } catch (Exception e) {
                // Don't let harvest failure prevent app termination
                System.err.println("[NRVideo] Emergency harvest failed: " + e.getMessage());
            }
        }
    }

    /**
     * Record custom event - used by trackers to send video events
     */
    public void recordEvent(String eventType, Map<String, Object> attributes) {
        if (!isActive.get()) {
            if (configuration.isDebugLoggingEnabled()) {
                System.err.println("[NRVideo] Cannot record event - agent not active");
            }
            return;
        }

        if (harvestManager != null) {
            harvestManager.recordCustomEvent(eventType, attributes);
        } else {
            System.err.println("[NRVideo] Cannot record event - harvest manager not initialized");
        }
    }

    /**
     * Get current configuration (read-only)
     */
    public NRVideoConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Check if agent is currently active
     */
    public boolean isActive() {
        return isActive.get();
    }

    // ===========================================
    // PRIVATE IMPLEMENTATION
    // ===========================================

    private void initialize() {
        // Direct use of unified configuration - now with context for token management
        HarvestComponentFactory factory = new HarvestComponentFactory(configuration, application);
        harvestManager = new HarvestManager(factory);
    }

    private void startTrackingInternal() {
        videoAgent = NewRelicVideoAgent.getInstance();

        Object contentTracker = createTracker(videoPlayer);
        if (contentTracker == null) {
            throw new RuntimeException("Unsupported player: " + videoPlayer.getClass().getSimpleName());
        }

        Object adTracker = configuration.isAdTrackingEnabled() ? createAdTracker() : null;

        trackerId = adTracker != null
            ? videoAgent.start(
                (com.newrelic.videoagent.core.tracker.NRTracker) contentTracker,
                (com.newrelic.videoagent.core.tracker.NRTracker) adTracker)
            : videoAgent.start((com.newrelic.videoagent.core.tracker.NRTracker) contentTracker);
    }

    private Object createTracker(Object player) {
        String className = player.getClass().getName().toLowerCase();

        if (className.contains("exoplayer") || className.contains("media3")) {
            return createCachedTracker("com.newrelic.videoagent.exoplayer.tracker.NRTrackerExoPlayer", player);
        }

        return null;
    }

    private Object createAdTracker() {
        return createCachedTracker("com.newrelic.videoagent.ima.tracker.NRTrackerIMA", null);
    }

    private Object createCachedTracker(String className, Object player) {
        try {
            Class<?> clazz = Class.forName(className);

            Object tracker = clazz.getDeclaredConstructor().newInstance();

            if (player != null) {
                clazz.getMethod("setPlayer", Object.class).invoke(tracker, player);
            }

            return tracker;
        } catch (Exception e) {
            if (configuration.isDebugLoggingEnabled()) {
                System.err.println("[NRVideo] Failed to create tracker: " + className + " - " + e.getMessage());
            }
            return null;
        }
    }

    private void setupAutomaticLifecycle() {
        if (application != null) {
            lifecycleObserver = new NRVideoLifecycleObserver(this);
            application.registerActivityLifecycleCallbacks(lifecycleObserver);

            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[NRVideo] Automatic lifecycle detection enabled");
            }
        }
    }

    private void cleanup() {
        // Unregister automatic lifecycle observer
        if (application != null && lifecycleObserver != null) {
            application.unregisterActivityLifecycleCallbacks(lifecycleObserver);
            lifecycleObserver = null;
        }

        if (videoAgent != null && trackerId != null) {
            videoAgent.releaseTracker(trackerId);
        }

        // Note: Scheduler shutdown is now handled by lifecycle observer
        // harvestManager only handles event harvesting, not lifecycle management

        harvestManager = null;
        videoAgent = null;
        trackerId = null;
    }
}
