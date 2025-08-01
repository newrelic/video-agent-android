package com.newrelic.videoagent.core.harvest;

import android.os.Handler;
import android.os.HandlerThread;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.utils.NRLog;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android-optimized harvest scheduler using Handler instead of ScheduledExecutorService
 * Better for mobile/TV environments - respects Android lifecycle and power management
 * Uses NRVideoConfiguration for device type detection instead of redundant detection
 */
public class MultiTaskHarvestScheduler implements SchedulerInterface {

    private final Handler backgroundHandler;
    private final Runnable onDemandHarvestTask;
    private final Runnable liveHarvestTask;
    private final int onDemandIntervalMs;
    private final int liveIntervalMs;
    private final AtomicBoolean isOnDemandRunning = new AtomicBoolean(false);
    private final AtomicBoolean isLiveRunning = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final boolean isAndroidTVDevice;

    // Runnable wrappers for self-scheduling
    private final Runnable onDemandHarvestRunnable = new Runnable() {
        @Override
        public void run() {
            if (isOnDemandRunning.get() && !isShutdown.get() && onDemandHarvestTask != null) {
                try {
                    onDemandHarvestTask.run();
                } catch (Exception e) {
                    NRLog.e("OnDemand harvest task failed", e);
                }
                // Re-schedule next execution if still running
                if (isOnDemandRunning.get() && !isShutdown.get()) {
                    backgroundHandler.postDelayed(this, onDemandIntervalMs);
                }
            }
        }
    };

    private final Runnable liveHarvestRunnable = new Runnable() {
        @Override
        public void run() {
            if (isLiveRunning.get() && !isShutdown.get() && liveHarvestTask != null) {
                try {
                    liveHarvestTask.run();
                } catch (Exception e) {
                    NRLog.e("Live harvest task failed", e);
                }
                // Re-schedule next execution if still running
                if (isLiveRunning.get() && !isShutdown.get()) {
                    backgroundHandler.postDelayed(this, liveIntervalMs);
                }
            }
        }
    };

    public MultiTaskHarvestScheduler(Runnable onDemandHarvestTask, Runnable liveHarvestTask,
                                   NRVideoConfiguration configuration) {
        this.onDemandHarvestTask = onDemandHarvestTask;
        this.liveHarvestTask = liveHarvestTask;
        this.onDemandIntervalMs = configuration.getHarvestCycleSeconds() * 1000;
        this.liveIntervalMs = configuration.getLiveHarvestCycleSeconds() * 1000;
        this.isAndroidTVDevice = configuration.isTV();

        // Create background handler with platform-optimized thread priority
        HandlerThread backgroundThread = new HandlerThread(
            "NRVideo-Harvest",
            isAndroidTVDevice ? android.os.Process.THREAD_PRIORITY_DEFAULT
                              : android.os.Process.THREAD_PRIORITY_BACKGROUND
        );
        backgroundThread.start();
        this.backgroundHandler = new Handler(backgroundThread.getLooper());

        NRLog.d("Scheduler initialized for " + (isAndroidTVDevice ? "TV" : "Mobile") +
            " - OnDemand: " + configuration.getHarvestCycleSeconds() + "s, Live: " +
            configuration.getLiveHarvestCycleSeconds() + "s");
    }

    @Override
    public void start() {
        start(NRVideoConstants.EVENT_TYPE_ONDEMAND);
        start(NRVideoConstants.EVENT_TYPE_LIVE);
    }

    @Override
    public void start(String bufferType) {
        if (isShutdown.get()) {
            NRLog.w("Cannot start " + bufferType + " scheduler - already shutdown");
            return;
        }

        if (NRVideoConstants.EVENT_TYPE_LIVE.equals(bufferType)) {
            if (isLiveRunning.compareAndSet(false, true)) {
                // Live events need immediate processing - minimal delay
                backgroundHandler.postDelayed(liveHarvestRunnable, 500); // 0.5 seconds
                NRLog.d("Live scheduler started with immediate harvest");
            }
        } else if (NRVideoConstants.EVENT_TYPE_ONDEMAND.equals(bufferType)) {
            if (isOnDemandRunning.compareAndSet(false, true)) {
                // Immediate first harvest to prevent event loss during startup
                backgroundHandler.postDelayed(onDemandHarvestRunnable, 1000); // 1 second instead of 5
                NRLog.d("OnDemand scheduler started with quick first harvest");
            }
        }
    }

    @Override
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            NRLog.d("Shutting down scheduler");

            // Stop both schedulers
            stopAllSchedulers();

            // CRITICAL: Harvest remaining events immediately before shutdown
            executeImmediateHarvest("SHUTDOWN");

            // Cleanup handler thread
            cleanupHandlerThread();
        }
    }

    @Override
    public void forceHarvest() {
        executeImmediateHarvest("FORCE_HARVEST");
    }

    @Override
    public boolean isRunning() {
        return !isShutdown.get() && (isOnDemandRunning.get() || isLiveRunning.get());
    }

    /**
     * Pause scheduling (used by lifecycle observer for background behavior)
     */
    public void pause() {
        if (isShutdown.get()) return;

        NRLog.d("Pausing scheduler");

        removeAllCallbacks();
    }

    /**
     * Resume scheduling with specified behavior (used by lifecycle observer)
     */
    public void resume(boolean useExtendedIntervals) {
        if (isShutdown.get()) return;

        NRLog.d("Resuming scheduler - Extended intervals: " + useExtendedIntervals);

        removeAllCallbacks(); // Clear any existing callbacks

        if (useExtendedIntervals && isAndroidTVDevice) {
            // TV background behavior: extended intervals
            resumeWithExtendedIntervals();
        } else {
            // Normal foreground behavior
            resumeWithNormalIntervals();
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Centralized method for immediate harvest execution
     * Reduces code duplication and ensures consistent error handling
     */
    private void executeImmediateHarvest(String reason) {
        NRLog.d("Executing immediate harvest - Reason: " + reason);

        try {
            // Execute both harvest tasks immediately and synchronously
            if (onDemandHarvestTask != null) {
                onDemandHarvestTask.run();
            }
            if (liveHarvestTask != null) {
                liveHarvestTask.run();
            }
        } catch (Exception e) {
            NRLog.e("Immediate harvest failed for reason: " + reason, e);
        }
    }

    /**
     * Stop all running schedulers without executing harvest
     */
    private void stopAllSchedulers() {
        isOnDemandRunning.set(false);
        isLiveRunning.set(false);
        removeAllCallbacks();
    }

    /**
     * Remove all pending callbacks from the handler
     */
    private void removeAllCallbacks() {
        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacks(onDemandHarvestRunnable);
            backgroundHandler.removeCallbacks(liveHarvestRunnable);
        }
    }

    /**
     * Resume scheduling with extended intervals (TV background behavior)
     */
    private void resumeWithExtendedIntervals() {
        if (isOnDemandRunning.get()) {
            backgroundHandler.postDelayed(onDemandHarvestRunnable, onDemandIntervalMs * 2);
        }
        if (isLiveRunning.get()) {
            backgroundHandler.postDelayed(liveHarvestRunnable, liveIntervalMs * 2);
        }
    }

    /**
     * Resume scheduling with normal intervals (foreground behavior)
     */
    private void resumeWithNormalIntervals() {
        if (isOnDemandRunning.get()) {
            backgroundHandler.postDelayed(onDemandHarvestRunnable, 1000); // Resume in 1 second
        }
        if (isLiveRunning.get()) {
            backgroundHandler.postDelayed(liveHarvestRunnable, 500);     // Resume in 0.5 seconds
        }
    }

    /**
     * Cleanup handler thread gracefully
     */
    private void cleanupHandlerThread() {
        if (backgroundHandler != null && backgroundHandler.getLooper() != null) {
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
