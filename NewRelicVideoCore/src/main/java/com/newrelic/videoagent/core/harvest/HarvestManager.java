package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

import android.content.Context;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.storage.CrashSafeHarvestFactory;
import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.utils.NRLog;

/**
 * Enhanced HarvestManager with integrated crash-safe storage
 * - Seamless integration with crash detection and recovery
 * - TV-optimized performance with automatic app lifecycle handling
 * - Zero performance impact during normal operation
 * - Proper 60% capacity threshold scheduler startup
 * - OPTIMIZED: Always uses CrashSafeHarvestFactory for consistent behavior
 */
public class HarvestManager implements EventBufferInterface.CapacityCallback {

    private final CrashSafeHarvestFactory factory;

    public HarvestManager(NRVideoConfiguration configuration,
                          Context context) {
        this.factory = new CrashSafeHarvestFactory(configuration, context, this::harvestNow, this, this::harvestOnDemand, this::harvestLive);
    }

    /**
     * Implements CapacityCallback - called when buffer reaches 60% capacity
     */
    @Override
    public void onCapacityThresholdReached(double currentCapacity, String bufferType) {
        factory.getScheduler().start(bufferType);

        NRLog.d("Capacity threshold reached for " + bufferType + ": " +
            String.format(Locale.US, "%.1f%%", currentCapacity * 100) +
            " - Attempting to start scheduler.");
    }

    /**
     * Records a custom event with lazy scheduler initialization
     */
    public void recordEvent(String eventType, Map<String, Object> attributes) {
        if (eventType != null && !eventType.trim().isEmpty()) {
            Map<String, Object> event = new HashMap<>(attributes != null ? attributes : new HashMap<>());
            event.put("eventType", eventType);
            event.put("timestamp", System.currentTimeMillis());

            // Add to event buffer - this will trigger capacity monitoring
            factory.getEventBuffer().addEvent(event);
        }
    }

    /**
     * Harvest on-demand events with optimized batch sizes from configuration
     * Public to allow method references from NRVideo initialization
     */
    public void harvestOnDemand() {
        // Use configuration-defined batch size - no more hardcoded values
        int batchSizeBytes = factory.getConfiguration().getRegularBatchSizeBytes();
        harvest(batchSizeBytes, NRVideoConstants.EVENT_TYPE_ONDEMAND, NRVideoConstants.EVENT_TYPE_ONDEMAND);
    }

    /**
     * Harvest live events with optimized batch sizes from configuration
     * Public to allow method references from NRVideo initialization
     */
    public void harvestLive() {
        // Use configuration-defined batch size - no more hardcoded values
        int batchSizeBytes = factory.getConfiguration().getLiveBatchSizeBytes();
        harvest(batchSizeBytes, NRVideoConstants.EVENT_TYPE_LIVE, NRVideoConstants.EVENT_TYPE_LIVE);
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
            NRLog.e("Invalid buffer type for immediate harvest: " + bufferType +
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
     */
    private void harvest(int batchSizeBytes, String priorityFilter, String harvestType) {
        try {
            SizeEstimator sizeEstimator = new DefaultSizeEstimator();
            List<Map<String, Object>> events = factory.getEventBuffer().pollBatchByPriority(
                batchSizeBytes,
                sizeEstimator,
                priorityFilter
            );

            if (!events.isEmpty()) {
                boolean success = factory.getHttpClient().sendEvents(events, harvestType);
                if (!success) {
                    factory.getDeadLetterHandler().handleFailedEvents(events, harvestType);
                } else {
                    // Notify event buffer about successful harvest to trigger any pending recovery
                    factory.getEventBuffer().onSuccessfulHarvest();
                }

                // Debug logging for optimization monitoring
                if (factory.getConfiguration().isDebugLoggingEnabled()) {
                    NRLog.d(harvestType + " harvest: " +
                        events.size() + " events, ~" + (events.size() * 2) + "KB");
                }
            }

        } catch (Exception e) {
            NRLog.e(harvestType + " harvest failed: " + e.getMessage(), e);
        }
    }

    public HarvestComponentFactory getFactory() {
        return factory;
    }
}
