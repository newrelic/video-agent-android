package com.newrelic.videoagent.core.harvest;

/**
 * Interface for harvest scheduling implementations
 * Includes lifecycle control methods for proper Android lifecycle management
 */
public interface SchedulerInterface {

    /**
     * Start the scheduled harvest tasks
     */
    void start();

    /**
     * Start a specific harvest task based on buffer type
     * @param bufferType The type of buffer ("live" or "ondemand")
     */
    void start(String bufferType);

    /**
     * Stop and shutdown the scheduled harvest tasks
     * Should perform immediate harvest before shutdown to prevent data loss
     */
    void shutdown();

    /**
     * Force immediate harvest of all pending events
     * Used for manual triggering or emergency harvesting
     */
    void forceHarvest();

    /**
     * Check if scheduler is currently running
     */
    boolean isRunning();

    /**
     * Pause all scheduled harvests (used during app lifecycle changes)
     */
    void pause();

    /**
     * Resume scheduled harvests with optional extended intervals
     * @param useExtendedIntervals true for background/TV behavior, false for normal intervals
     */
    void resume(boolean useExtendedIntervals);
}
