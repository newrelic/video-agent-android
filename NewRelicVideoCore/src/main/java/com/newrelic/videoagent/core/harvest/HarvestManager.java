package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

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
 * - QOE Provider support for harvest-time QOE_AGGREGATE injection
 */
public class HarvestManager implements EventBufferInterface.CapacityCallback {

    private final CrashSafeHarvestFactory factory;
    private final CopyOnWriteArrayList<QoeProvider> qoeProviders = new CopyOnWriteArrayList<>();
    private int harvestCycleNumber = 0;

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
     * Register a QOE provider to be called during harvest cycles.
     * Called by NRVideoTracker at CONTENT_START.
     *
     * @param provider The QOE provider to register
     */
    public void registerQoeProvider(QoeProvider provider) {
        if (provider != null && !qoeProviders.contains(provider)) {
            qoeProviders.add(provider);
            NRLog.d("QOE provider registered. Total providers: " + qoeProviders.size());
        }
    }

    /**
     * Unregister a QOE provider.
     * Called by NRVideoTracker at CONTENT_END or tracker disposal.
     * Also clears any pending QOE for this provider.
     *
     * @param provider The QOE provider to unregister
     */
    public void unregisterQoeProvider(QoeProvider provider) {
        if (provider != null) {
            qoeProviders.remove(provider);
            NRLog.d("QOE provider unregistered. Remaining providers: " + qoeProviders.size());
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
            // Increment harvest cycle number
            harvestCycleNumber++;

            SizeEstimator sizeEstimator = new DefaultSizeEstimator();
            List<Map<String, Object>> events = factory.getEventBuffer().pollBatchByPriority(
                batchSizeBytes,
                sizeEstimator,
                priorityFilter
            );

            // Inject QOE events BEFORE checking if batch is empty
            // QOE should be generated based on cycle number, even if there are no other events
            injectQoeEventsIfNeeded(events, harvestCycleNumber);

            if (!events.isEmpty()) {
                boolean success = factory.getHttpClient().sendEvents(events, harvestType);
                if (!success) {
                    factory.getDeadLetterHandler().handleFailedEvents(events, harvestType);
                } else {
                    // Notify event buffer about successful harvest to trigger any pending recovery
                    factory.getEventBuffer().onSuccessfulHarvest();
                }

                // Debug logging for optimization monitoring
                NRLog.d(harvestType + " harvest: " +
                    events.size() + " events, ~" + (events.size() * 2) + "KB");
            }

        } catch (Exception e) {
            NRLog.e(harvestType + " harvest failed: " + e.getMessage(), e);
        }
    }

    /**
     * Inject QOE_AGGREGATE events into harvest batch if conditions are met.
     * Like iOS: QOE is sent independently on each qualified harvest cycle,
     * regardless of whether the batch contains other VideoAction events.
     *
     * Final QOE from CONTENT_END (marked with "isFinalQoe" flag) is always injected
     * and triggers provider unregistration.
     *
     * @param batch The harvest batch to potentially inject QOE events into
     * @param cycleNumber The current harvest cycle number
     */
    private void injectQoeEventsIfNeeded(List<Map<String, Object>> batch, int cycleNumber) {
        if (batch == null || qoeProviders.isEmpty()) {
            return;
        }

        // Process each registered QOE provider (use ArrayList copy to allow removal during iteration)
        List<QoeProvider> providersSnapshot = new ArrayList<>(qoeProviders);
        for (QoeProvider provider : providersSnapshot) {
            try {
                // Check if current cycle should send QOE
                Map<String, Object> currentQoeEvent = provider.generateQoeIfNeeded(batch, cycleNumber);

                if (currentQoeEvent != null) {
                    // Check if this is the final QOE from CONTENT_END
                    if (Boolean.TRUE.equals(currentQoeEvent.get("isFinalQoe"))) {
                        // FINAL QOE - remove internal flag before sending
                        currentQoeEvent.remove("isFinalQoe");
                        batch.add(currentQoeEvent);
                        NRLog.d("Final QOE_AGGREGATE from CONTENT_END injected - provider will be unregistered");

                        // Unregister provider after injecting final QOE
                        qoeProviders.remove(provider);
                        continue;
                    }

                    // Regular QOE - send independently (like iOS)
                    batch.add(currentQoeEvent);
                    NRLog.d("QOE_AGGREGATE injected into harvest batch (cycle " + cycleNumber + ")");
                }
            } catch (Exception e) {
                NRLog.e("Error generating QOE from provider: " + e.getMessage(), e);
            }
        }
    }

    public HarvestComponentFactory getFactory() {
        return factory;
    }
}
