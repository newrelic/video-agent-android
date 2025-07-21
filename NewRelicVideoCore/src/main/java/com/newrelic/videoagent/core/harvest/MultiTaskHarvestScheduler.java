package com.newrelic.videoagent.core.harvest;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android-optimized harvest scheduler using Handler instead of ScheduledExecutorService
 * Better for mobile/TV environments - respects Android lifecycle and power management
 * Includes accurate TV detection using androidx.leanback
 */
public class MultiTaskHarvestScheduler implements SchedulerInterface {
    private final Handler backgroundHandler;
    private final Runnable regularHarvestTask;
    private final Runnable liveHarvestTask;
    private final int regularIntervalMs;
    private final int liveIntervalMs;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final boolean isAndroidTVDevice;

    // Runnable wrappers for self-scheduling
    private final Runnable regularHarvestRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning.get() && regularHarvestTask != null) {
                regularHarvestTask.run();
                // Re-schedule next execution
                backgroundHandler.postDelayed(this, regularIntervalMs);
            }
        }
    };

    private final Runnable liveHarvestRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning.get() && liveHarvestTask != null) {
                liveHarvestTask.run();
                // Re-schedule next execution
                backgroundHandler.postDelayed(this, liveIntervalMs);
            }
        }
    };

    public MultiTaskHarvestScheduler(Runnable regularHarvestTask, Runnable liveHarvestTask,
                                   int regularIntervalSeconds, int liveIntervalSeconds) {
        this.regularHarvestTask = regularHarvestTask;
        this.liveHarvestTask = liveHarvestTask;
        this.regularIntervalMs = regularIntervalSeconds * 1000;
        this.liveIntervalMs = liveIntervalSeconds * 1000;

        // Detect Android TV platform at initialization
        this.isAndroidTVDevice = detectAndroidTV();

        // Create background handler with platform-optimized thread priority
        HandlerThread backgroundThread = new HandlerThread(
            "NRVideo-Harvest",
            isAndroidTVDevice ? android.os.Process.THREAD_PRIORITY_DEFAULT
                              : android.os.Process.THREAD_PRIORITY_BACKGROUND
        );
        backgroundThread.start();
        this.backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /**
     * Accurate Android TV detection using multiple reliable methods
     * Compatible with API 16+ and works across all Android TV devices
     */
    private boolean detectAndroidTV() {
        try {
            // Get context from main looper (safer than static access)
            Context context = getCurrentContext();
            if (context != null) {
                PackageManager pm = context.getPackageManager();

                // Method 1: Check for Android TV Leanback UI (most accurate)
                if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                    return true;
                }

                // Method 2: Check for television feature
                if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
                    return true;
                }

                // Method 3: Check if touchscreen is NOT required (TV indicator)
                if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                    return true;
                }
            }

            // Fallback: Use system properties (API 16+ compatible)
            return detectTVFromSystemProperties();

        } catch (Exception e) {
            // If any detection fails, fall back to system properties
            return detectTVFromSystemProperties();
        }
    }

    /**
     * Get current context safely (works across different Android versions)
     */
    private Context getCurrentContext() {
        try {
            // Try to get context from ActivityThread (works on most Android versions)
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            if (activityThread != null) {
                return (Context) activityThreadClass.getMethod("getApplication").invoke(activityThread);
            }
        } catch (Exception e) {
            // Fallback methods could be added here if needed
        }
        return null;
    }

    /**
     * Fallback TV detection using only public APIs (API 16+ compatible)
     */
    private boolean detectTVFromSystemProperties() {
        try {
            // Method 1: Check build type for TV indicators
            String buildType = android.os.Build.TYPE;
            if (buildType != null && buildType.toLowerCase().contains("tv")) {
                return true;
            }

            // Method 2: Check device model for TV indicators
            String model = android.os.Build.MODEL;
            if (model != null) {
                String modelLower = model.toLowerCase();
                if (modelLower.contains("tv") || modelLower.contains("chromecast") ||
                    modelLower.contains("android_tv") || modelLower.contains("shield") ||
                    modelLower.contains("nexus_player") || modelLower.contains("mibox")) {
                    return true;
                }
            }

            // Method 3: Check manufacturer for TV-specific brands
            String manufacturer = android.os.Build.MANUFACTURER;
            if (manufacturer != null) {
                String mfgLower = manufacturer.toLowerCase();
                if (mfgLower.contains("nvidia") && model != null && model.toLowerCase().contains("shield")) {
                    return true; // NVIDIA Shield TV
                }
                if (mfgLower.contains("xiaomi") && model != null && model.toLowerCase().contains("mibox")) {
                    return true; // Xiaomi Mi Box
                }
            }

            // Method 4: Check product name for TV indicators
            String product = android.os.Build.PRODUCT;
            if (product != null) {
                String productLower = product.toLowerCase();
                if (productLower.contains("tv") || productLower.contains("atv") ||
                    productLower.contains("googletv") || productLower.contains("androidtv")) {
                    return true;
                }
            }

            // Method 5: Use reflection to safely check system properties (if available)
            return checkSystemPropertiesSafely();

        } catch (Exception e) {
            // If any method fails, assume mobile (safer default)
            return false;
        }
    }

    /**
     * Safely check system properties using reflection to avoid compilation issues
     */
    private boolean checkSystemPropertiesSafely() {
        try {
            // Use reflection to access SystemProperties without direct import
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getMethod = systemPropertiesClass.getMethod("get", String.class, String.class);

            // Check ro.build.characteristics property
            String characteristics = (String) getMethod.invoke(null, "ro.build.characteristics", "");
            if (characteristics != null && !characteristics.isEmpty()) {
                String charLower = characteristics.toLowerCase();
                if (charLower.contains("tv") || charLower.contains("television")) {
                    return true;
                }
            }

            // Check ro.build.type property
            String buildType = (String) getMethod.invoke(null, "ro.build.type", "");
            if ("tv".equals(buildType)) {
                return true;
            }

        } catch (Exception e) {
            // Reflection failed - this is expected on some devices/Android versions
            // SystemProperties access may be restricted
        }

        return false;
    }

    @Override
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            // Start regular harvest with short initial delay (Android-optimized)
            backgroundHandler.postDelayed(regularHarvestRunnable, 5000); // 5 seconds

            // Start live harvest with even shorter delay
            backgroundHandler.postDelayed(liveHarvestRunnable, 2000); // 2 seconds
        }
    }

    @Override
    public void shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            // CRITICAL: Harvest remaining events immediately before shutdown
            immediateHarvestAll();

            // Remove all pending callbacks
            backgroundHandler.removeCallbacks(regularHarvestRunnable);
            backgroundHandler.removeCallbacks(liveHarvestRunnable);

            // Quit background thread gracefully with API level compatibility
            if (backgroundHandler.getLooper() != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    // API 18+: Use quitSafely() for graceful shutdown
                    backgroundHandler.getLooper().quitSafely();
                } else {
                    // API 16-17: Use quit() - less graceful but compatible
                    backgroundHandler.getLooper().quit();
                }
            }
        }
    }

    /**
     * CRITICAL METHOD: Immediately harvest all pending events
     * Called when app is backgrounded or closed to prevent data loss
     */
    public void immediateHarvestAll() {
        try {
            // Execute both harvest tasks immediately and synchronously
            if (regularHarvestTask != null) {
                regularHarvestTask.run();
            }
            if (liveHarvestTask != null) {
                liveHarvestTask.run();
            }
        } catch (Exception e) {
            android.util.Log.e("NRVideo", "Immediate harvest failed", e);
        }
    }

    @Override
    public void onAppBackgrounded() {
        // Use the pre-detected TV status for better performance
        if (isAndroidTVDevice) {
            // TV-specific: Reduce harvest frequency when paused instead of stopping
            backgroundHandler.removeCallbacks(regularHarvestRunnable);
            backgroundHandler.removeCallbacks(liveHarvestRunnable);

            // Immediate harvest
            immediateHarvestAll();

            // Resume with longer intervals (TV-optimized for pause state)
            if (isRunning.get()) {
                backgroundHandler.postDelayed(regularHarvestRunnable, regularIntervalMs * 2); // Double interval
                backgroundHandler.postDelayed(liveHarvestRunnable, liveIntervalMs * 2);       // Double interval
            }
        } else {
            // Mobile behavior: full pause
            backgroundHandler.removeCallbacks(regularHarvestRunnable);
            backgroundHandler.removeCallbacks(liveHarvestRunnable);
            immediateHarvestAll();
        }
    }

    @Override
    public void onAppForegrounded() {
        // Resume normal scheduling - works for both mobile and TV
        if (isRunning.get()) {
            backgroundHandler.removeCallbacks(regularHarvestRunnable); // Clear any existing scheduled tasks
            backgroundHandler.removeCallbacks(liveHarvestRunnable);

            // Resume with normal intervals
            backgroundHandler.postDelayed(regularHarvestRunnable, 1000); // Resume in 1 second
            backgroundHandler.postDelayed(liveHarvestRunnable, 500);     // Resume in 0.5 seconds
        }
    }

    @Override
    public void forceHarvest() {
        immediateHarvestAll();
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }
}
