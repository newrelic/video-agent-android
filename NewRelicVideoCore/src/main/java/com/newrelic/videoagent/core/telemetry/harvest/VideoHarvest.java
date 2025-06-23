package com.newrelic.videoagent.core.telemetry.harvest;

import android.content.Context;

import com.newrelic.videoagent.core.telemetry.configuration.VideoAgentConfiguration;
import com.newrelic.videoagent.core.utils.NRLog;

public class VideoHarvest {
    private static VideoHarvest instance; // Singleton instance
    private VideoHarvester harvester;
    private VideoHarvestTimer harvestTimer;
    private VideoAgentConfiguration configuration; // The video agent's own configuration

    /**
     * Private constructor to enforce singleton pattern.
     */
    private VideoHarvest() {
        NRLog.d("VideoHarvest instance created."); // Updated log call
    }

    /**
     * Initializes the VideoHarvest system. This method sets up the harvester and timer
     * using the provided configuration and application context.
     * This is the entry point for starting the independent video data collection.
     *
     * @param config The VideoAgentConfiguration for the current video agent.
     * @param context The application context.
     */
    public static synchronized void initialize(VideoAgentConfiguration config, Context context) {
        if (instance == null) {
            instance = new VideoHarvest();
        }

        if (instance.harvester != null) {
            NRLog.d("VideoHarvest is already initialized. Re-initializing."); // Updated log call
            instance.shutdown(); // Shut down existing components before re-init
        }

        instance.configuration = config;
        instance.harvester = new VideoHarvester(config, context); // Pass context to harvester
        instance.harvestTimer = new VideoHarvestTimer(instance.harvester);
        NRLog.d("VideoHarvest initialized with configuration."); // Updated log call
    }

    /**
     * Starts the periodic harvesting process.
     * This method will begin sending collected video data at regular intervals.
     */
    public static void start() {
        if (instance == null || instance.harvestTimer == null) {
            NRLog.e("VideoHarvest not initialized. Call initialize() first."); // Updated log call
            return;
        }
        instance.harvestTimer.start();
        NRLog.d("VideoHarvest started."); // Updated log call
    }

    /**
     * Stops the periodic harvesting process.
     * Data will no longer be automatically sent.
     */
    public static void stop() {
        if (instance == null || instance.harvestTimer == null) {
            NRLog.d("VideoHarvest not initialized or already stopped."); // Updated log call
            return;
        }
        instance.harvestTimer.stop();
        NRLog.d("VideoHarvest stopped."); // Updated log call
    }

    /**
     * Triggers an immediate harvest cycle.
     * This is a non-blocking call.
     */
    public static void harvestNow() {
        if (instance == null || instance.harvestTimer == null) {
            NRLog.e("VideoHarvest not initialized. Call initialize() first."); // Updated log call
            return;
        }
        instance.harvestTimer.tickNow();
        NRLog.d("VideoHarvest immediate tick triggered."); // Updated log call
    }

    /**
     * Triggers an immediate harvest cycle and optionally waits for its completion.
     * @param wait If true, this call will block until the harvest attempt finishes or times out.
     */
    public static void harvestNow(boolean wait) {
        if (instance == null || instance.harvestTimer == null) {
            NRLog.e("VideoHarvest not initialized. Call initialize() first."); // Updated log call
            return;
        }
        instance.harvestTimer.tickNow(wait);
        NRLog.d("VideoHarvest immediate tick triggered (wait: " + wait + ")."); // Updated log call
    }

    /**
     * Shuts down the entire VideoHarvest system, stopping timers and executors.
     * This should be called when the application is terminating or the video agent
     * is no longer needed to release resources.
     */
    public static synchronized void shutdown() {
        if (instance != null) {
            if (instance.harvestTimer != null) {
                instance.harvestTimer.shutdown();
                instance.harvestTimer = null;
            }
            if (instance.harvester != null) {
                instance.harvester.shutdown();
                instance.harvester = null;
            }
            instance.configuration = null;
            instance = null; // Reset singleton instance
            NRLog.d("VideoHarvest system shut down."); // Updated log call
        } else {
            NRLog.d("VideoHarvest already shut down or not initialized."); // Updated log call
        }
    }

    /**
     * Returns the current instance of VideoHarvest.
     * @return The singleton VideoHarvest instance.
     */
    public static VideoHarvest getInstance() {
        return instance;
    }

    /**
     * Checks if the VideoHarvest system has been initialized.
     * @return true if initialized, false otherwise.
     */
    public static boolean isInitialized() {
        return instance != null && instance.harvester != null && instance.harvestTimer != null;
    }

    /**
     * Retrieves the current configuration of the video agent.
     * @return The VideoAgentConfiguration.
     */
    public VideoAgentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Updates the VideoHarvest system with a new configuration.
     * This will propagate the new configuration to the harvester and timer.
     * @param newConfiguration The updated VideoAgentConfiguration.
     */
    public void updateConfiguration(VideoAgentConfiguration newConfiguration) {
        this.configuration = newConfiguration;
        if (harvester != null) {
            harvester.updateConfiguration(newConfiguration);
        }
        if (harvestTimer != null) {
            harvestTimer.updateConfiguration(newConfiguration);
        }
        NRLog.d("VideoHarvest configuration updated."); // Updated log call
    }

}
