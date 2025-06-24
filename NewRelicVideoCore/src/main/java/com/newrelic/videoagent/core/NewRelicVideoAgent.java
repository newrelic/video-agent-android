package com.newrelic.videoagent.core;

import android.content.Context;

import com.newrelic.videoagent.core.model.NRTrackerPair;
import com.newrelic.videoagent.core.telemetry.analytics.VideoAnalyticsController;
import com.newrelic.videoagent.core.telemetry.configuration.VideoAgentConfiguration;
import com.newrelic.videoagent.core.telemetry.harvest.VideoHarvest;
import com.newrelic.videoagent.core.tracker.NRTracker;
import com.newrelic.videoagent.core.tracker.NRVideoTracker;
import com.newrelic.videoagent.core.utils.NRLog;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * `NewRelicVideoAgent` contains the methods to start the Video Agent and access tracker instances.
 */
public class NewRelicVideoAgent {
    private static NewRelicVideoAgent singleton = null;

    private Map<Integer, NRTrackerPair> trackerPairs;
    private Integer trackerIdIndex;
    private String uuid; // Represents a session ID for the video agent
    private VideoAgentConfiguration agentConfiguration; // NEW: Holds the standalone configuration

    /**
     * Get shared instance.
     * @return Singleton instance of NewRelicVideoAgent.
     */
    public static NewRelicVideoAgent getInstance() {
        if (singleton == null) {
            singleton = new NewRelicVideoAgent();
        }
        return singleton;
    }

    /**
     * Private constructor to enforce Singleton pattern.
     * Initializes internal state.
     */
    private NewRelicVideoAgent() {
        trackerIdIndex = 0;
        uuid = UUID.randomUUID().toString(); // Generate a unique session ID
        trackerPairs = new HashMap<>();
        NRLog.d("NewRelicVideoAgent instance created."); // Updated log call
    }

    /**
     * Initializes the New Relic Video Agent as a standalone component.
     * This method sets up the internal configuration, harvest mechanism, and analytics
     * independently of the main New Relic Android Agent.
     *
     * @param applicationToken The New Relic application token for video monitoring.
     * @param context The Android application context, required for internal operations
     * like persistence and network checks within the harvest module.
     */
    public static synchronized void initialize(String applicationToken, Context context) {
        if (singleton == null) {
            getInstance(); // Ensure singleton is created if not already
        }

        if (singleton.agentConfiguration != null) {
            NRLog.d("Video Agent already initialized. Re-initializing."); // Updated log call
            // Optionally, call shutdown here if re-initialization is not expected behavior
            VideoHarvest.shutdown(); // Ensure previous harvest cycle is stopped cleanly
        }

        // Create the standalone configuration for the video agent
        singleton.agentConfiguration = new VideoAgentConfiguration(); // Uses hardcoded token from config class
        // If the applicationToken parameter needs to override the hardcoded one:
        // singleton.agentConfiguration.setApplicationToken(applicationToken); // Add setter if needed

        // Initialize the independent harvest mechanism
        VideoHarvest.initialize(singleton.agentConfiguration, context);

        // Start the periodic data harvesting
        VideoHarvest.start();

        // Set initial session attributes, if any
        VideoAnalyticsController.getInstance().setAttribute("sessionId", singleton.uuid);
        NRLog.d("NewRelicVideoAgent initialized successfully in standalone mode. Session ID: " + singleton.uuid); // Updated log call
    }

    /**
     * Retrieves the current session ID of the video agent.
     * This ID is generated when the agent is initialized.
     * @return The unique session ID as a String.
     */
    public String getSessionId() {
        return uuid;
    }

    /**
     * Starts tracking a content video playback.
     * @param contentTracker The {@link NRTracker} instance specifically designed for content playback.
     * @return A unique integer ID associated with this tracker pair.
     */
    public Integer start(NRTracker contentTracker) {
        return start(contentTracker, null);
    }

    /**
     * Starts tracking content and optionally ad video playback.
     * This method registers the provided trackers and links them if both are present.
     * @param contentTracker The {@link NRTracker} instance for content playback.
     * @param adTracker An optional {@link NRTracker} instance for ad playback. Can be null.
     * @return A unique integer ID associated with this tracker pair.
     */
    public Integer start(NRTracker contentTracker, NRTracker adTracker) {
        NRTrackerPair pair = new NRTrackerPair(contentTracker, adTracker);
        Integer trackerId = trackerIdIndex++;
        trackerPairs.put(trackerId, pair);

        if (adTracker instanceof NRVideoTracker) {
            ((NRVideoTracker)adTracker).getState().isAd = true;
        }

        // Link trackers so they can access each other's state or attributes if needed.
        if (contentTracker != null && adTracker != null) {
            contentTracker.linkedTracker = adTracker;
            adTracker.linkedTracker = contentTracker;
        }

        // Notify trackers that they are ready to begin tracking.
        if (contentTracker != null) {
            contentTracker.trackerReady();
        }
        if (adTracker != null) {
            adTracker.trackerReady();
        }

        NRLog.d("Started tracker pair with ID: " + trackerId + // Updated log call
                " (Content: " + (contentTracker != null ? contentTracker.getClass().getSimpleName() : "None") +
                ", Ad: " + (adTracker != null ? adTracker.getClass().getSimpleName() : "None") + ")");
        return trackerId;
    }

    /**
     * Releases and disposes of the trackers associated with a given tracker ID.
     * This should be called when video playback finishes or the trackers are no longer needed.
     * @param trackerId The ID of the tracker pair to release.
     */
    public void releaseTracker(Integer trackerId) {
        NRTrackerPair pair = trackerPairs.get(trackerId);
        if (pair != null) {
            if (pair.getFirst() != null) {
                pair.getFirst().dispose();
                NRLog.d("Disposed content tracker for ID: " + trackerId); // Updated log call
            }
            if (pair.getSecond() != null) {
                pair.getSecond().dispose();
                NRLog.d("Disposed ad tracker for ID: " + trackerId); // Updated log call
            }
            trackerPairs.remove(trackerId);
            NRLog.d("Released tracker pair with ID: " + trackerId); // Updated log call
        } else {
            NRLog.d("Attempted to release non-existent tracker ID: " + trackerId); // Updated log call
        }
    }

    /**
     * Retrieves the content tracker associated with a given tracker ID.
     * @param trackerId The ID of the tracker pair.
     * @return The content {@link NRTracker}, or null if not found.
     */
    public NRTracker getContentTracker(Integer trackerId) {
        NRTrackerPair pair = trackerPairs.get(trackerId);
        return (pair != null) ? pair.getFirst() : null;
    }

    /**
     * Retrieves the ad tracker associated with a given tracker ID.
     * @param trackerId The ID of the tracker pair.
     * @return The ad {@link NRTracker}, or null if not found.
     */
    public NRTracker getAdTracker(Integer trackerId) {
        NRTrackerPair pair = trackerPairs.get(trackerId);
        return (pair != null) ? pair.getSecond() : null;
    }

    /**
     * Sets a user ID attribute that will be attached to all subsequent analytics events
     * generated by the video agent.
     * @param userId The user ID to set. Can be null or empty to remove the attribute.
     */
    public void setUserId(String userId) {
        // Direct interaction with the standalone VideoAnalyticsController
        boolean success = VideoAnalyticsController.getInstance().setAttribute("enduser.id", userId);
        if (success) {
            NRLog.d("Set user ID to: " + (userId != null ? userId : "null")); // Updated log call
        } else {
            NRLog.e("Failed to set user ID attribute."); // Updated log call
        }
    }

    /**
     * Shuts down the New Relic Video Agent.
     * This will stop all active harvesting and release resources.
     * Call this when the application is terminating or video monitoring is no longer required.
     */
    public static synchronized void shutdown() {
        if (singleton != null) {
            VideoHarvest.shutdown(); // Shut down the harvest system
            singleton.trackerPairs.clear();
            singleton.trackerIdIndex = 0;
            singleton.uuid = null;
            singleton.agentConfiguration = null;
            singleton = null; // Clear the singleton instance
            NRLog.d("NewRelicVideoAgent shut down."); // Updated log call
        } else {
            NRLog.d("NewRelicVideoAgent is already shut down or not initialized."); // Updated log call
        }
    }

    /**
     * Retrieves the internal VideoAgentConfiguration.
     * @return The VideoAgentConfiguration instance.
     */
    public VideoAgentConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }
}
