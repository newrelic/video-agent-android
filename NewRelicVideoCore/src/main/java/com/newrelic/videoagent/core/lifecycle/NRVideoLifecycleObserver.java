package com.newrelic.videoagent.core.lifecycle;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.newrelic.videoagent.core.harvest.HarvestManager;
import com.newrelic.videoagent.core.harvest.SchedulerInterface;
import com.newrelic.videoagent.core.storage.VideoEventStorage;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simplified Android lifecycle observer optimized for video observability
 * Focuses on core responsibilities:
 * - App background/foreground detection
 * - Immediate harvest on background (regardless of harvest cycle)
 * - Crash detection and emergency storage
 * - Device-specific optimizations (Mobile vs TV)
 */
public class NRVideoLifecycleObserver implements Application.ActivityLifecycleCallbacks {

    private final LifecycleListener listener;
    private final AtomicInteger activeActivities = new AtomicInteger(0);
    private final AtomicBoolean isAppInBackground = new AtomicBoolean(false);
    private final Context context;
    private final NRVideoConfiguration configuration;
    private final boolean isAndroidTV;

    // Core integration components
    private volatile HarvestManager harvestManager;
    private volatile SchedulerInterface harvestScheduler;
    private volatile VideoEventStorage videoEventStorage;

    // Emergency backup protection
    private final AtomicBoolean emergencyBackupInProgress = new AtomicBoolean(false);

    public interface LifecycleListener {
        void onAppBackgrounded();
        void onAppForegrounded();
        void onAppTerminating();
    }

    public NRVideoLifecycleObserver(LifecycleListener listener, Context context, NRVideoConfiguration configuration) {
        this.listener = listener;
        this.context = context;
        this.configuration = configuration;
        this.isAndroidTV = detectAndroidTV(context);

        if (context != null) {
            this.videoEventStorage = VideoEventStorage.getInstance(context);
        }

        setupCrashDetection();

        if (configuration.isDebugLoggingEnabled()) {
            System.out.println("[LifecycleObserver] Initialized for " +
                (isAndroidTV ? "Android TV" : "Mobile"));
        }
    }

    /**
     * Set harvest components for lifecycle coordination
     */
    public void setHarvestComponents(HarvestManager harvestManager, SchedulerInterface harvestScheduler) {
        this.harvestManager = harvestManager;
        this.harvestScheduler = harvestScheduler;

        if (configuration.isDebugLoggingEnabled()) {
            System.out.println("[LifecycleObserver] Harvest components set - Manager: " +
                (harvestManager != null) + ", Scheduler: " + (harvestScheduler != null));
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (activeActivities.incrementAndGet() == 1 && isAppInBackground.compareAndSet(true, false)) {
            handleAppForegrounded();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (activeActivities.decrementAndGet() == 0 && isAppInBackground.compareAndSet(false, true)) {
            handleAppBackgrounded();
        }
    }

    /**
     * Handle app backgrounding - CRITICAL for data preservation
     * Immediate harvest regardless of harvest cycle
     */
    private void handleAppBackgrounded() {
        try {
            // IMMEDIATE harvest regardless of harvest cycle (requirement)
            performEmergencyHarvest("APP_BACKGROUNDED");

            // Coordinate with scheduler for background behavior
            if (harvestScheduler != null) {
                harvestScheduler.onAppBackgrounded();
            }

            // Device-specific background handling
            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[LifecycleObserver] " + (isAndroidTV ? "TV" : "Mobile") +
                    " backgrounded - immediate harvest triggered");
            }

            // Notify listener
            if (listener != null) {
                listener.onAppBackgrounded();
            }
        } catch (Exception e) {
            System.err.println("[LifecycleObserver] Background handling error: " + e.getMessage());
        }
    }

    /**
     * Handle app foregrounding - Resume normal operation
     */
    private void handleAppForegrounded() {
        try {
            // Resume normal scheduler behavior
            if (harvestScheduler != null) {
                harvestScheduler.onAppForegrounded();
            }

            // Check for recovery data
            if (harvestManager != null && harvestManager.isRecovering()) {
                System.out.println("[LifecycleObserver] Recovery detected: " + harvestManager.getRecoveryStats());
            }

            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[LifecycleObserver] " + (isAndroidTV ? "TV" : "Mobile") +
                    " foregrounded - normal operation resumed");
            }

            // Notify listener
            if (listener != null) {
                listener.onAppForegrounded();
            }
        } catch (Exception e) {
            System.err.println("[LifecycleObserver] Foreground handling error: " + e.getMessage());
        }
    }

    /**
     * Emergency harvest - used for background, crashes, and critical scenarios
     */
    private void performEmergencyHarvest(String reason) {
        if (!emergencyBackupInProgress.compareAndSet(false, true)) {
            return; // Already in progress
        }

        try {
            // 1. Immediate scheduler harvest (highest priority)
            if (harvestScheduler != null) {
                harvestScheduler.forceHarvest();
            }

            // 2. Force harvest manager to send all pending events
            if (harvestManager != null) {
                harvestManager.forceHarvestAll();
            }

            if (configuration.isDebugLoggingEnabled()) {
                System.out.println("[LifecycleObserver] Emergency harvest completed - " +
                    (isAndroidTV ? "TV" : "Mobile") + " - Reason: " + reason);
            }
        } catch (Exception e) {
            System.err.println("[LifecycleObserver] Emergency harvest failed: " + e.getMessage());
            // Last resort: direct storage backup
            performDirectStorageBackup(reason);
        } finally {
            emergencyBackupInProgress.set(false);
        }
    }

    /**
     * Direct storage backup as last resort during crashes
     */
    private void performDirectStorageBackup(String reason) {
        try {
            if (videoEventStorage != null) {
                // Create emergency crash event
                java.util.Map<String, Object> emergencyEvent = new java.util.HashMap<>();
                emergencyEvent.put("eventType", "EMERGENCY_BACKUP");
                emergencyEvent.put("reason", reason);
                emergencyEvent.put("timestamp", System.currentTimeMillis());
                emergencyEvent.put("deviceType", isAndroidTV ? "TV" : "Mobile");
                emergencyEvent.put("activeActivities", activeActivities.get());

                java.util.List<java.util.Map<String, Object>> events = new java.util.ArrayList<>();
                events.add(emergencyEvent);

                videoEventStorage.backupFailedEvents(events);
                System.out.println("[LifecycleObserver] Direct storage backup completed - Reason: " + reason);
            }
        } catch (Exception e) {
            System.err.println("[LifecycleObserver] Direct storage backup failed: " + e.getMessage());
        }
    }

    /**
     * Setup crash detection with immediate storage
     */
    private void setupCrashDetection() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            private final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

            @Override
            public void uncaughtException(Thread thread, Throwable exception) {
                // CRITICAL: Immediate emergency harvest and storage before crash
                performEmergencyHarvest("APP_CRASH");
                performDirectStorageBackup("APP_CRASH");

                if (listener != null) {
                    try {
                        listener.onAppTerminating();
                    } catch (Exception e) {
                        // Don't let harvest failure prevent crash reporting
                    }
                }

                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, exception);
                } else {
                    System.exit(1);
                }
            }
        });
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        // System might kill app - emergency harvest
        performEmergencyHarvest("SAVE_INSTANCE_STATE");
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (activeActivities.get() == 0) {
            performEmergencyHarvest("APP_TERMINATING");
            if (listener != null) {
                try {
                    listener.onAppTerminating();
                } catch (Exception e) {
                    // Don't interrupt destruction
                }
            }
        }
    }

    // Unused lifecycle methods
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}

    private boolean detectAndroidTV(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.hasSystemFeature("android.software.leanback") ||
                   pm.hasSystemFeature("android.hardware.type.television");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get lifecycle statistics
     */
    public String getLifecycleStats() {
        return String.format("LifecycleObserver{device=%s, background=%s, activities=%d, " +
            "harvestManager=%s, scheduler=%s}",
            isAndroidTV ? "TV" : "Mobile",
            isAppInBackground.get(),
            activeActivities.get(),
            harvestManager != null,
            harvestScheduler != null);
    }
}
