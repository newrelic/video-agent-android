package com.newrelic.videoagent.core.harvest;

/**
 * Interface for harvest scheduling implementations
 * Includes app lifecycle awareness for data loss prevention
 */
public interface SchedulerInterface {

    /**
     * Start the scheduled harvest tasks
     */
    void start();

    /**
     * Stop and shutdown the scheduled harvest tasks
     * Should perform immediate harvest before shutdown to prevent data loss
     */
    void shutdown();

    /**
     * Called when app goes to background
     * Should immediately harvest all pending events to prevent data loss
     */
    void onAppBackgrounded();

    /**
     * Called when app comes to foreground
     * Can resume normal scheduling behavior
     */
    void onAppForegrounded();

    /**
     * Force immediate harvest of all pending events
     * Used for manual triggering or emergency harvesting
     */
    void forceHarvest();

    /**
     * Check if scheduler is currently running
     */
    boolean isRunning();
}
