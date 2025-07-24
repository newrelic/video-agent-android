package com.newrelic.videoagent.core.harvest;

import android.content.Context;
import com.newrelic.videoagent.core.NRVideoConfiguration;

/**
 * Factory for creating harvest-related components
 * Uses dependency injection pattern for better testability
 * Optimized for mobile/TV environments
 */
public class HarvestComponentFactory {
    private final NRVideoConfiguration configuration;
    private final Context context;

    public HarvestComponentFactory(NRVideoConfiguration configuration, android.content.Context context) {
        this.configuration = configuration;
        this.context = context;
    }

    /**
     * Creates an optimized event buffer based on configuration
     * Uses isTV flag from configuration instead of detecting device
     */
    public EventBufferInterface createEventBuffer() {
        return new PriorityEventBuffer(configuration.isTV());
    }

    /**
     * Creates a dead letter queue for failed events
     * Uses isTV flag from configuration for consistency
     */
    public EventBufferInterface createDeadLetterQueue() {
        return new PriorityEventBuffer(configuration.isTV());
    }

    /**
     * Creates a multi-task scheduler for different harvest intervals
     */
    public SchedulerInterface createScheduler(Runnable onDemandTask, Runnable liveTask) {
        return new MultiTaskHarvestScheduler(
            onDemandTask,
            liveTask,
            configuration
        );
    }

    /**
     * Creates an HTTP client optimized for mobile/TV networks with token management
     */
    public HttpClientInterface createHttpClient() {
        return new OptimizedHttpClient(configuration, context);
    }

    /**
     * Gets the configuration for other components
     */
    public NRVideoConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Gets the context for other components
     */
    public android.content.Context getContext() {
        return context;
    }
}
