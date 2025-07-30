package com.newrelic.videoagent.core.storage;

import android.content.Context;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.harvest.HarvestComponentFactory;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;
import com.newrelic.videoagent.core.harvest.MultiTaskHarvestScheduler;
import com.newrelic.videoagent.core.harvest.OptimizedHttpClient;
import com.newrelic.videoagent.core.harvest.SchedulerInterface;
import com.newrelic.videoagent.core.utils.NRLog;

/**
 * Clean integration factory for crash-safe storage
 * Drop-in replacement that adds crash safety with zero performance impact
 */
public class CrashSafeHarvestFactory implements HarvestComponentFactory {
    private final Context context;
    private final CrashSafeEventBuffer crashSafeBuffer;
    private final IntegratedDeadLetterHandler integratedHandler;
    private final NRVideoConfiguration configuration;
    private final HttpClientInterface httpClient;
    private final SchedulerInterface scheduler;

    public CrashSafeHarvestFactory(NRVideoConfiguration configuration,
                                   Context context,
                                   EventBufferInterface.OverflowCallback overflowCallback,
                                   EventBufferInterface.CapacityCallback capacityCallback,
                                   Runnable onDemandTask,
                                   Runnable liveTask) {
        this.context = context;
        this.configuration = configuration;
        crashSafeBuffer = new CrashSafeEventBuffer(context, configuration, new VideoEventStorage(context));
        httpClient = new OptimizedHttpClient(getConfiguration(), context);
        integratedHandler = new IntegratedDeadLetterHandler(crashSafeBuffer, httpClient, configuration);
        scheduler = new MultiTaskHarvestScheduler(onDemandTask, liveTask, configuration);
        // Set overflow callback for immediate harvest when buffer is getting full
        crashSafeBuffer.setOverflowCallback(overflowCallback);
        // Set capacity callback for 60% threshold scheduler startup
        crashSafeBuffer.setCapacityCallback(capacityCallback);
    }


    /**
     * Emergency backup for app lifecycle events
     */
    @Override
    public void performEmergencyBackup() {
        try {
            crashSafeBuffer.emergencyBackup();
            integratedHandler.emergencyBackup();
        } catch (Exception e) {
            NRLog.w("[CrashSafeFactory] Emergency backup failed: " + e.getMessage());
        }
    }


    /**
     * Check if in recovery mode
     */
    @Override
    public boolean isRecovering() {
        return crashSafeBuffer.getRecoveryStats().isRecovering;
    }

    /**
     * Get recovery statistics
     */
    @Override
    public String getRecoveryStats() {
        return crashSafeBuffer.getRecoveryStats().toString();
    }

    @Override
    public NRVideoConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void cleanup() {
        crashSafeBuffer.cleanup();
        NRLog.d("CrashSafeHarvestFactory cleaned up successfully");
    }

    @Override
    public EventBufferInterface getEventBuffer() {
        return crashSafeBuffer;
    }

    @Override
    public HttpClientInterface getHttpClient() {
        return httpClient;
    }

    @Override
    public SchedulerInterface getScheduler() {
        return scheduler;
    }

    @Override
    public IntegratedDeadLetterHandler getDeadLetterHandler() {
        return integratedHandler;
    }
}
