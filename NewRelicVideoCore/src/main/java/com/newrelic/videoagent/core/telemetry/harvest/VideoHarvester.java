package com.newrelic.videoagent.core.telemetry.harvest;

import android.content.Context;

import com.newrelic.videoagent.core.telemetry.analytics.VideoAnalyticsController;
import com.newrelic.videoagent.core.telemetry.configuration.VideoAgentConfiguration;
import com.newrelic.videoagent.core.utils.NRLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class VideoHarvester {
    private VideoAgentConfiguration configuration;
    private VideoHarvestData harvestData;
    private VideoHarvestConnection harvestConnection;
    private VideoAnalyticsController analyticsController;
    private Context context; // Application context for potential future use (e.g., fetching device info)

    // A single-threaded executor for harvest tasks to ensure sequential processing and avoid race conditions
    // during data extraction and sending.
    private final ExecutorService harvestExecutor = Executors.newSingleThreadExecutor();
    private Future<?> currentHarvestTask; // To track the current running harvest task

    /**
     * Constructs a new VideoHarvester instance.
     * @param configuration The VideoAgentConfiguration for the agent.
     * @param context The application context.
     */
    public VideoHarvester(VideoAgentConfiguration configuration, Context context) {
        this.configuration = configuration;
        this.context = context;
        this.harvestData = new VideoHarvestData();
        this.harvestConnection = new VideoHarvestConnection(configuration);
        this.analyticsController = VideoAnalyticsController.getInstance(); // Get existing singleton
        NRLog.d("VideoHarvester initialized."); // Updated log call
    }

    /**
     * Executes a harvest cycle, collecting data, packaging it, and sending it.
     * This method is designed to be called by the `VideoHarvestTimer`.
     * It ensures only one harvest task runs at a time.
     */
    public void harvest() {
        // Submit the harvest task to the single-threaded executor.
        // This ensures sequential execution of harvest operations.
        currentHarvestTask = harvestExecutor.submit(this::doHarvest);
    }

    /**
     * The actual harvest logic, executed within the harvestExecutor.
     */
    private void doHarvest() {
        NRLog.d("Starting video data harvest cycle."); // Updated log call

        // Step 1: Collect data from various sources (e.g., analytics controller, trackers)
        collectData();

        // Step 2: Check if there's any data to send
        if (!harvestData.hasData()) {
            NRLog.d("No video data to send in this harvest cycle."); // Updated log call
            return;
        }

        // Step 3: Prepare the payload in the curl's specific array format
        String payload = buildPayload();
        if (payload == null) {
            NRLog.e("Failed to build harvest payload. Skipping send."); // Updated log call
            return;
        }

        // Step 4: Send the payload
        boolean success = harvestConnection.sendData(payload);

        // Step 5: Handle post-send logic (clear data on success)
        if (success) {
            harvestData.clear(); // Clear data only if successfully sent
            NRLog.d("Video data harvest cycle completed successfully."); // Updated log call
        } else {
            NRLog.e("Video data harvest failed. Data will be re-attempted on next cycle."); // Updated log call
            // Data is not cleared so it will be included in the next harvest attempt (retry mechanism).
        }
    }

    /**
     * Collects data from various sources into the `VideoHarvestData` instance.
     */
    private void collectData() {
        NRLog.d("Collecting data for harvest..."); // Updated log call

        // Collect events from the VideoAnalyticsController
        // The getEventQueue() method from VideoAnalyticsController provides the ConcurrentLinkedQueue directly.
        // In the buildPayload method, we'll poll from this queue via harvestData.getAndClearEvents()
        // which effectively moves them from the queue for processing.
        // No explicit copy is needed here, as harvestData.addEvent directly adds to its own queue.
        // However, for clarity and to ensure all current events are scooped, we'll iterate and add.
        while (!analyticsController.getEventQueue().isEmpty()) {
            harvestData.addEvent(analyticsController.getEventQueue().poll());
        }

        // TODO: In a real implementation, you would also collect other data types, e.g.:
        // - Metrics from individual video trackers
        // - Error events
        // - Any other session-specific data
        // For example:
        // harvestData.addVideoMetric(tracker.getMetrics());
    }

    /**
     * Builds the JSON payload in the specific array-of-arrays format
     * as shown in the provided curl command.
     * @return A JSON string representing the harvest payload, or null if an error occurs.
     */
    private String buildPayload() {
        JSONArray mainArray = new JSONArray();
        try {
            // Element 0: Data Token (mocked based on curl)
            JSONArray dataToken = new JSONArray();
            dataToken.put(212108994); // Placeholder
            dataToken.put(212109335); // Placeholder
            mainArray.put(dataToken);

            // Element 1: Device Info (mocked based on curl)
            JSONArray deviceInfo = new JSONArray();
            deviceInfo.put("Android"); // OS Name
            deviceInfo.put("14");      // OS Version
            deviceInfo.put("sdk_gphone64_arm64"); // Device Model
            deviceInfo.put("AndroidAgent");       // Agent Name
            deviceInfo.put("7.4.0-alpha01");      // Agent Version (mocked to match curl)
            deviceInfo.put("b797aee6-aa69-4879-9ba3-1f4aed1a7777"); // Device ID (mocked to match curl)
            deviceInfo.put(""); // Referrer
            deviceInfo.put(""); // Agent Build Id
            deviceInfo.put("Google"); // Manufacturer
            JSONObject platformInfo = new JSONObject(); // Platform Attributes
            platformInfo.put("size", "normal");
            platformInfo.put("platform", "Flutter");
            platformInfo.put("platformVersion", "1.0.8");
            deviceInfo.put(platformInfo);
            mainArray.put(deviceInfo);

            // Element 2: Harvest Session Duration (mocked to 0 for simplicity, curl had 0)
            mainArray.put(0);

            // Element 3: Activity Traces (empty array as in curl)
            mainArray.put(new JSONArray());

            // Element 4: Http Transactions (empty array as in curl)
            mainArray.put(new JSONArray());

            // Element 5: Agent Health (empty array as in curl)
            mainArray.put(new JSONArray());

            // Element 6: Errors (empty array as in curl)
            mainArray.put(new JSONArray());

            // Element 7: Analytics Events (empty array as in curl, will add our events here if any)
            // Note: The curl example has an empty array at index 7, but then a custom event at index 9.
            // This suggests the payload schema might vary or that custom events are at a specific index.
            // For now, I'll put collected events into index 9 as per the example.
            mainArray.put(new JSONArray()); // Keeping this empty to match curl structure exactly at index 7

            // Element 8: Custom Metrics (empty object as in curl)
            mainArray.put(new JSONObject());

            // Element 9: Events (This is where the custom events from VideoAnalyticsController go)
            JSONArray eventsArray = new JSONArray();
            Collection<Map<String, Object>> collectedEvents = harvestData.getAndClearEvents();
            for (Map<String, Object> event : collectedEvents) {
                // Add some default attributes from analyticsController's global attributes if missing
                // This ensures that "sessionId" and other common attributes are present on events.
                Map<String, Object> finalEvent = new HashMap<>(analyticsController.getAttributes());
                finalEvent.putAll(event); // Event specific attributes can override global ones

                // Mock timeSinceLoad and timestamp if they are not explicitly set by the tracker
                if (!finalEvent.containsKey("timeSinceLoad")) {
                    finalEvent.put("timeSinceLoad", System.currentTimeMillis() / 1000.0); // Seconds
                }
                if (!finalEvent.containsKey("timestamp")) {
                    finalEvent.put("timestamp", System.currentTimeMillis());
                }
                // Add fixed instrumentation attributes to match the curl example
                finalEvent.put("instrumentation.provider", "video-agent"); // Updated from "curl testing"
                finalEvent.put("instrumentation.name", "NewRelicVideoAgent"); // Updated from "curl testing"
                finalEvent.put("instrumentation.version", "1.0.0"); // Updated

                eventsArray.put(new JSONObject(finalEvent));
            }
            if (eventsArray.length() > 0) {
                mainArray.put(eventsArray);
            } else {
                mainArray.put(new JSONArray()); // Ensure it's always a JSONArray even if empty
            }


            // Optional: Include videoMetrics from harvestData into a separate, non-standard section
            // As the curl doesn't explicitly show where custom metrics like "videoMetrics" would go in this array structure,
            // we'll keep them separate or integrate into the custom events if appropriate for the schema.
            // For now, they are collected by harvestData but not included in this curl-matching payload.
            // If New Relic ingest expects them, they would need a dedicated index in the mainArray.
            Collection<Map<String, Object>> collectedMetrics = harvestData.getAndClearVideoMetrics();
            if (!collectedMetrics.isEmpty()) {
                NRLog.d("Video metrics collected but not included in current payload format " +
                        "to match curl example structure. Metrics count: " + collectedMetrics.size()); // Updated log call
                // For a proper solution, define a new index in the mainArray for video metrics.
            }

            return mainArray.toString();

        } catch (JSONException e) {
            NRLog.e("Error building JSON payload: " + e.getMessage()); // Updated log call
            return null;
        }
    }

    /**
     * Updates the harvester's configuration.
     * @param newConfiguration The new VideoAgentConfiguration.
     */
    public void updateConfiguration(VideoAgentConfiguration newConfiguration) {
        this.configuration = newConfiguration;
        this.harvestConnection.updateConfiguration(newConfiguration);
        this.harvestData.updateConfiguration(newConfiguration);
        NRLog.d("VideoHarvester configuration updated."); // Updated log call
    }

    /**
     * Shuts down the harvester's executor service.
     * This method should be called when the application is terminating or the video agent is no longer needed.
     */
    public void shutdown() {
        NRLog.d("Shutting down VideoHarvester."); // Updated log call
        harvestExecutor.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!harvestExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                harvestExecutor.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!harvestExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    NRLog.e("VideoHarvester executor did not terminate."); // Updated log call
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            harvestExecutor.shutdownNow();
            Thread.currentThread().interrupt(); // Preserve interrupt status
            NRLog.e("VideoHarvester shutdown interrupted."); // Updated log call
        }
    }

    /**
     * Checks if the harvester is currently disabled (e.g., due to configuration).
     * @return Always returns false for POC, but can be configured based on `VideoAgentConfiguration`.
     */
    public boolean isDisabled() {
        // In a real scenario, this might check a flag in configuration.
        // For now, assuming it's always enabled unless explicitly shut down.
        return harvestExecutor.isShutdown();
    }
}
