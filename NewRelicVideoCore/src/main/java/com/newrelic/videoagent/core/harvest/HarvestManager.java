package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import android.util.Log;
import com.newrelic.videoagent.core.storage.CrashSafeHarvestFactory;
import com.newrelic.videoagent.core.storage.IntegratedDeadLetterHandler;
import com.newrelic.videoagent.core.NRVideoConstants;

/**
 * Enhanced HarvestManager with integrated crash-safe storage
 * - Seamless integration with crash detection and recovery
 * - TV-optimized performance with automatic app lifecycle handling
 * - Zero performance impact during normal operation
 * - Proper 60% capacity threshold scheduler startup
 * - OPTIMIZED: Always uses CrashSafeHarvestFactory for consistent behavior
 */
public class HarvestManager implements EventBufferInterface.CapacityCallback {

    private static final String TAG = "NRVideo.HarvestManager";

    private final EventBufferInterface eventBuffer;
    private final SchedulerInterface scheduler;
    private final HttpClientInterface httpClient;
    private final IntegratedDeadLetterHandler deadLetterHandler;
    private final CrashSafeHarvestFactory factory;

    public HarvestManager(CrashSafeHarvestFactory factory) {
        this.factory = factory;

        // Always use crash-safe components
        this.eventBuffer = factory.createEventBuffer();
        this.httpClient = factory.createHttpClient();

        // Use factory method to create IntegratedDeadLetterHandler for proper integration
        this.deadLetterHandler = factory.createIntegratedDeadLetterHandler(httpClient);

        this.scheduler = factory.createScheduler(this::harvestOnDemand, this::harvestLive);

        // Set up event buffer monitoring for 60% threshold scheduler startup
        setupEventBufferMonitoring();
    }

    /**
     * Set up event buffer monitoring for 60% capacity threshold
     */
    private void setupEventBufferMonitoring() {
        // Set overflow callback for immediate harvest when buffer is getting full
        eventBuffer.setOverflowCallback(this::harvestNow);

        // Set capacity callback for 60% threshold scheduler startup
        eventBuffer.setCapacityCallback(this);
    }

    /**
     * Implements CapacityCallback - called when buffer reaches 60% capacity
     */
    @Override
    public void onCapacityThresholdReached(double currentCapacity, String bufferType) {
        scheduler.start(bufferType);

        if (factory.getConfiguration().isDebugLoggingEnabled()) {
            Log.d(TAG, "Capacity threshold reached for " + bufferType + ": " +
                String.format(Locale.US, "%.1f%%", currentCapacity * 100) +
                " - Attempting to start scheduler.");
        }
    }

    /**
     * Records a custom event with lazy scheduler initialization
     */
    public void recordCustomEvent(String eventType, Map<String, Object> attributes) {
        if (eventType != null && !eventType.trim().isEmpty()) {
            Map<String, Object> event = new HashMap<>(attributes != null ? attributes : new HashMap<>());
            event.put("eventType", eventType);
            event.put("timestamp", System.currentTimeMillis());

            // Add to event buffer - this will trigger capacity monitoring
            eventBuffer.addEvent(event);
        }
    }

    /**
     * Harvest on-demand events with optimized batch sizes from configuration
     * Public to allow method references from NRVideo initialization
     */
    public void harvestOnDemand() {
        // Use configuration-defined batch size - no more hardcoded values
        int batchSizeBytes = factory.getConfiguration().getRegularBatchSizeBytes();
        harvest(batchSizeBytes, NRVideoConstants.EVENT_TYPE_ONDEMAND, NRVideoConstants.EVENT_TYPE_ONDEMAND, true);
    }

    /**
     * Harvest live events with optimized batch sizes from configuration
     * Public to allow method references from NRVideo initialization
     */
    public void harvestLive() {
        // Use configuration-defined batch size - no more hardcoded values
        int batchSizeBytes = factory.getConfiguration().getLiveBatchSizeBytes();
        harvest(batchSizeBytes, NRVideoConstants.EVENT_TYPE_LIVE, NRVideoConstants.EVENT_TYPE_LIVE, false);
    }

    /**
     * Force immediate harvest - strict buffer type validation
     * Each session should know exactly what type of content is being watched
     */
    private void harvestNow(String bufferType) {
        if (NRVideoConstants.EVENT_TYPE_LIVE.equals(bufferType)) {
            harvestLive();
        } else if (NRVideoConstants.EVENT_TYPE_ONDEMAND.equals(bufferType)) {
            harvestOnDemand();
        } else {
            // STRICT: Log error for invalid buffer type - this should never happen
            Log.e(TAG, "Invalid buffer type for immediate harvest: " + bufferType +
                     ". Sessions must be either 'live' or 'ondemand', not both.");

            // Do nothing - force the caller to provide correct buffer type
            // This prevents incorrect dual harvesting that wastes resources
        }
    }

    /**
     * Generic harvest method for both regular and live events
     * OPTIMIZED: Updated for 2KB events with device-specific batch sizes
     * @param batchSizeBytes Maximum batch size in bytes (optimized for 2KB events)
     * @param priorityFilter Priority level to filter ("ondemand", "live")
     * @param harvestType HTTP endpoint type ("regular", "live")
     * @param retryFailures Whether to retry failed events after harvest
     */
    private void harvest(int batchSizeBytes, String priorityFilter, String harvestType, boolean retryFailures) {
        try {
            SizeEstimator sizeEstimator = new DefaultSizeEstimator();
            List<Map<String, Object>> events = eventBuffer.pollBatchByPriority(
                batchSizeBytes,
                sizeEstimator,
                priorityFilter
            );

            if (!events.isEmpty()) {
                boolean success = httpClient.sendEvents(events, harvestType);
                if (!success) {
                    deadLetterHandler.handleFailedEvents(events, harvestType);
                } else {
                    // Notify event buffer about successful harvest to trigger any pending recovery
                    eventBuffer.onSuccessfulHarvest();
                }

                // Debug logging for optimization monitoring
                if (factory.getConfiguration().isDebugLoggingEnabled()) {
                    Log.d(TAG, harvestType + " harvest: " +
                        events.size() + " events, ~" + (events.size() * 2) + "KB");
                }
            }

            // Retry failed events if requested (typically only for regular harvest)
            if (retryFailures) {
                deadLetterHandler.retryFailedEvents();
            }

        } catch (Exception e) {
            Log.e(TAG, harvestType + " harvest failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get recovery statistics
     */
    public String getRecoveryStats() {
        return factory.getRecoveryStats();
    }

    /**
     * Check if currently recovering from crash
     */
    public boolean isRecovering() {
        return factory.isRecovering();
    }
}
