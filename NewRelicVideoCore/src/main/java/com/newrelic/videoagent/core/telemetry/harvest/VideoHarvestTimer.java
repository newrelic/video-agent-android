package com.newrelic.videoagent.core.telemetry.harvest;

import com.newrelic.videoagent.core.telemetry.configuration.VideoAgentConfiguration;
import com.newrelic.videoagent.core.utils.NRLog;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class VideoHarvestTimer {
    private static final long DEFAULT_HARVEST_INTERVAL_SECONDS = 60;

    private final VideoHarvester harvester;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> scheduledFuture;

    private long harvestIntervalSeconds;
    private long lastHarvestStartTime;

    /**
     * Constructs a new VideoHarvestTimer.
     * @param harvester The VideoHarvester instance to trigger.
     */
    public VideoHarvestTimer(VideoHarvester harvester) {
        this.harvester = harvester;
        this.harvestIntervalSeconds = DEFAULT_HARVEST_INTERVAL_SECONDS;
        NRLog.d("VideoHarvestTimer initialized with default interval: " + harvestIntervalSeconds + " seconds."); // Updated log call
    }

    /**
     * Starts the periodic harvest timer. If already running, it will be reset.
     */
    public void start() {
        stop(); // Ensure any existing task is stopped
        lastHarvestStartTime = System.currentTimeMillis();
        scheduledFuture = scheduler.scheduleAtFixedRate(
                () -> {
                    NRLog.d("VideoHarvestTimer triggered harvest."); // Updated log call
                    harvester.harvest();
                    lastHarvestStartTime = System.currentTimeMillis();
                },
                harvestIntervalSeconds, // Initial delay
                harvestIntervalSeconds, // Periodic interval
                TimeUnit.SECONDS
        );
        NRLog.d("VideoHarvestTimer started with interval: " + harvestIntervalSeconds + " seconds."); // Updated log call
    }

    /**
     * Stops the periodic harvest timer.
     */
    public void stop() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true); // Attempt to interrupt if running
            NRLog.d("VideoHarvestTimer stopped."); // Updated log call
        }
    }

    /**
     * Triggers an immediate harvest cycle, in addition to the scheduled ones.
     * This is a non-blocking call.
     */
    public void tickNow() {
        NRLog.d("Triggering immediate video harvest (tickNow)."); // Updated log call
        scheduler.submit(() -> harvester.harvest());
    }

    /**
     * Triggers an immediate harvest cycle and waits for it to complete.
     * This is a blocking call.
     * @param bWait If true, waits for the harvest to complete.
     */
    public void tickNow(boolean bWait) {
        if (!bWait) {
            tickNow();
            return;
        }

        NRLog.d("Triggering immediate video harvest and waiting (tickNow with wait)."); // Updated log call
        Future<?> future = scheduler.submit(() -> harvester.harvest());
        try {
            future.get(10, TimeUnit.SECONDS); // Wait up to 10 seconds for the harvest to complete
        } catch (Exception e) {
            // Modified: NRLog.e no longer takes Throwable, logging message only
            NRLog.e("Timed out or failed waiting for immediate harvest to complete: " + e.getMessage());
            future.cancel(true); // Try to cancel the task
        }
    }

    /**
     * Updates the harvest timer's interval based on new configuration.
     * The timer will be restarted with the new interval if it was running.
     * @param newConfiguration The new VideoAgentConfiguration.
     */
    public void updateConfiguration(VideoAgentConfiguration newConfiguration) {
        // Currently, harvest interval is fixed. If config supported changing it,
        // this is where the logic would go:
        // this.harvestIntervalSeconds = newConfiguration.getHarvestIntervalSeconds(); // Example
        // If the interval changed and timer was running, restart it.
        NRLog.d("VideoHarvestTimer configuration updated (interval remains " + harvestIntervalSeconds + "s)."); // Updated log call
    }

    /**
     * Shuts down the timer's executor service.
     * This should be called when the application is terminating.
     */
    public void shutdown() {
        stop(); // Ensure tasks are stopped first
        scheduler.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    NRLog.e("VideoHarvestTimer scheduler did not terminate."); // Updated log call
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            scheduler.shutdownNow();
            Thread.currentThread().interrupt(); // Preserve interrupt status
            NRLog.e("VideoHarvestTimer shutdown interrupted."); // Updated log call
        }
        NRLog.d("VideoHarvestTimer shut down."); // Updated log call
    }

    /**
     * Returns the time elapsed since the last harvest started.
     * @return Time in milliseconds since last harvest started.
     */
    public long timeSinceLastHarvestStarted() {
        // This method depends on VideoAgentConfiguration to get INVALID_SESSION_DURATION.
        // To avoid circular dependency or passing the config, we'll assume 0 for invalid here.
        // If needed, the config could be passed to the constructor or a getter in Harvester could expose it.
        if (lastHarvestStartTime == 0) {
            // A more robust solution might retrieve this from configuration
            // or pass it down from VideoHarvest to Harvester, then to Timer.
            return 0; // Represents an invalid duration if start has not been called
        }
        return System.currentTimeMillis() - lastHarvestStartTime;
    }
}
