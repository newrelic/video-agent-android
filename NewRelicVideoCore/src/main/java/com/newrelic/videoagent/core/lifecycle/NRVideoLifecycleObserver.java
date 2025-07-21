package com.newrelic.videoagent.core.lifecycle;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive Android lifecycle detector for NRVideo
 * Handles normal lifecycle + critical scenarios (crashes, kills, errors)
 */
public class NRVideoLifecycleObserver implements Application.ActivityLifecycleCallbacks {

    private final LifecycleListener listener;
    private final AtomicInteger activeActivities = new AtomicInteger(0);
    private final AtomicInteger pausedActivities = new AtomicInteger(0);
    private volatile boolean isAppInBackground = false;

    public interface LifecycleListener {
        void onAppBackgrounded();
        void onAppForegrounded();
        void onAppTerminating(); // NEW: For crashes/kills
    }

    public NRVideoLifecycleObserver(LifecycleListener listener) {
        this.listener = listener;
        setupCrashDetection();
    }

    /**
     * Set up detection for app crashes and unexpected termination
     */
    private void setupCrashDetection() {
        // Handle uncaught exceptions (app crashes)
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            private final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

            @Override
            public void uncaughtException(Thread thread, Throwable exception) {
                // CRITICAL: Immediate harvest before crash
                if (listener != null) {
                    try {
                        listener.onAppTerminating();
                    } catch (Exception e) {
                        // Don't let harvest failure prevent crash reporting
                    }
                }

                // Call the original handler (important for crash reporting)
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, exception);
                } else {
                    System.exit(1);
                }
            }
        });

        // Handle app termination via shutdown hook (when possible)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (listener != null) {
                try {
                    listener.onAppTerminating();
                } catch (Exception e) {
                    // Silent failure in shutdown hook
                }
            }
        }));
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        // Not needed for our use case
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (activeActivities.incrementAndGet() == 1 && isAppInBackground) {
            // First activity started - app came to foreground
            isAppInBackground = false;
            if (listener != null) {
                listener.onAppForegrounded();
            }
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        pausedActivities.decrementAndGet();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        pausedActivities.incrementAndGet();
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (activeActivities.decrementAndGet() == 0 && !isAppInBackground) {
            // Last activity stopped - app went to background
            isAppInBackground = true;
            if (listener != null) {
                listener.onAppBackgrounded();
            }
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        // CRITICAL: This is called before the app might be killed by system
        // Trigger immediate harvest to prevent data loss
        if (listener != null && isAppInBackground) {
            try {
                listener.onAppTerminating();
            } catch (Exception e) {
                // Don't interrupt the save state process
            }
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        // Check if this is the last activity being destroyed
        if (activeActivities.get() == 0 && pausedActivities.get() == 0) {
            // App is likely terminating completely
            if (listener != null) {
                try {
                    listener.onAppTerminating();
                } catch (Exception e) {
                    // Don't interrupt destruction
                }
            }
        }
    }
}
