package com.newrelic.videoagent.core.lifecycle;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import com.newrelic.videoagent.core.harvest.HarvestManager;
import com.newrelic.videoagent.core.harvest.SchedulerInterface;
import com.newrelic.videoagent.core.storage.CrashSafeHarvestFactory;
import com.newrelic.videoagent.core.NRVideoConfiguration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplified Android lifecycle observer optimized for video observability
 * Focuses on core responsibilities:
 * - App background/foreground detection
 * - Immediate harvest on background (regardless of harvest cycle)
 * - Crash detection and emergency storage
 * - Device-specific optimizations (Mobile vs TV)
 */
public class NRVideoLifecycleObserver implements Application.ActivityLifecycleCallbacks {

    private final AtomicInteger activeActivities = new AtomicInteger(0);
    private final AtomicBoolean isAppInBackground = new AtomicBoolean(false);
    private final NRVideoConfiguration configuration;
    private final boolean isAndroidTV;

    // Core integration components - required dependencies
    private final HarvestManager harvestManager;
    private final SchedulerInterface harvestScheduler;
    private final CrashSafeHarvestFactory crashSafeFactory; // For emergency storage operations

    // Emergency backup protection
    private final AtomicBoolean emergencyBackupInProgress = new AtomicBoolean(false);

    public NRVideoLifecycleObserver(Context context, NRVideoConfiguration configuration,
                                  HarvestManager harvestManager, SchedulerInterface harvestScheduler,
                                  CrashSafeHarvestFactory crashSafeFactory) {
        this.configuration = configuration;
        this.isAndroidTV = configuration.isTV();
        this.harvestManager = harvestManager;
        this.harvestScheduler = harvestScheduler;
        this.crashSafeFactory = crashSafeFactory;

        setupCrashDetection();

        if (configuration.isDebugLoggingEnabled()) {
            Log.d("LifecycleObserver", "Initialized for " +
                (isAndroidTV ? "Android TV" : "Mobile"));
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

            // Control scheduler directly using interface methods
            harvestScheduler.pause();
            // For TV: resume with extended intervals, Mobile: stay paused
            harvestScheduler.resume(isAndroidTV);

            // Device-specific background handling
            if (configuration.isDebugLoggingEnabled()) {
                Log.d("LifecycleObserver", (isAndroidTV ? "TV" : "Mobile") +
                    " backgrounded - immediate harvest triggered");
            }
        } catch (Exception e) {
            Log.e("LifecycleObserver", "Background handling error: " + e.getMessage());
        }
    }

    /**
     * Handle app foregrounding - Resume normal operation
     */
    private void handleAppForegrounded() {
        try {
            // Resume normal scheduler behavior using interface method
            harvestScheduler.resume(false); // Normal intervals

            // Check for recovery data
            if (harvestManager.isRecovering()) {
                Log.d("LifecycleObserver", "Recovery detected: " + harvestManager.getRecoveryStats());
            }

            if (configuration.isDebugLoggingEnabled()) {
                Log.d("LifecycleObserver", (isAndroidTV ? "TV" : "Mobile") +
                    " foregrounded - normal operation resumed");
            }
        } catch (Exception e) {
            Log.e("LifecycleObserver", "Foreground handling error: " + e.getMessage());
        }
    }

    /**
     * Emergency backup - prioritize SQLite storage over immediate network harvest
     * Network harvest during lifecycle transitions is unreliable
     */
    private void performEmergencyHarvest(String reason) {
        if (!emergencyBackupInProgress.compareAndSet(false, true)) {
            return; // Already in progress
        }

        try {
            // SKIP immediate network harvest - prioritize reliable SQLite backup
            // Immediate network operations during app lifecycle transitions often fail

            // Always perform emergency backup to SQLite (reliable)
            crashSafeFactory.performEmergencyBackup();

            if (configuration.isDebugLoggingEnabled()) {
                Log.d("LifecycleObserver", "Emergency backup to SQLite completed - " +
                    (isAndroidTV ? "TV" : "Mobile") + " - Reason: " + reason +
                    " (network harvest skipped for reliability)");
            }
        } catch (Exception e) {
            Log.e("LifecycleObserver", "Emergency backup failed: " + e.getMessage());
        } finally {
            emergencyBackupInProgress.set(false);
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
        }
    }

    // Unused lifecycle methods
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
}
