package com.newrelic.videoagent.core.harvest;

import android.content.Context;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.storage.IntegratedDeadLetterHandler;

/**
 * Factory for creating harvest-related components
 * Uses dependency injection pattern for better testability
 * Optimized for mobile/TV environments
 */
public interface HarvestComponentFactory {

    /**
     * Gets the configuration for other components
     */
    NRVideoConfiguration getConfiguration();

    /**
     * Gets the context for other components
     */
    Context getContext();

    void cleanup();

    EventBufferInterface getEventBuffer();
    HttpClientInterface getHttpClient();
    SchedulerInterface getScheduler();
    IntegratedDeadLetterHandler getDeadLetterHandler();
    void performEmergencyBackup();
    boolean isRecovering();
    String getRecoveryStats();
}
