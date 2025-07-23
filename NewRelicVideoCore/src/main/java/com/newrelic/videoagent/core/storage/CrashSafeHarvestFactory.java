package com.newrelic.videoagent.core.storage;

import android.content.Context;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.harvest.HarvestComponentFactory;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;
import com.newrelic.videoagent.core.harvest.SchedulerInterface;
import com.newrelic.videoagent.core.harvest.OptimizedHttpClient;
import com.newrelic.videoagent.core.harvest.PriorityEventBuffer;

/**
 * Clean integration factory for crash-safe storage
 * Drop-in replacement that adds crash safety with zero performance impact
 */
public class CrashSafeHarvestFactory extends HarvestComponentFactory {

    private final Context context;
    private CrashSafeEventBuffer crashSafeBuffer;
    private IntegratedDeadLetterHandler integratedHandler;

    public CrashSafeHarvestFactory(NRVideoConfiguration configuration, Context context) {
        super(configuration, context);
        this.context = context;
    }

    @Override
    public EventBufferInterface createEventBuffer() {
        if (crashSafeBuffer == null) {
            crashSafeBuffer = new CrashSafeEventBuffer(context, super.getConfiguration());
        }
        return crashSafeBuffer;
    }

    @Override
    public EventBufferInterface createDeadLetterQueue() {
        // Regular in-memory queue for dead letter operations
        return new PriorityEventBuffer();
    }

    @Override
    public HttpClientInterface createHttpClient() {
        return new OptimizedHttpClient(getConfiguration(), context);
    }

    /**
     * Create integrated dead letter handler that works with crash-safe buffer
     */
    public IntegratedDeadLetterHandler createIntegratedDeadLetterHandler(HttpClientInterface httpClient) {
        if (integratedHandler == null && crashSafeBuffer != null) {
            EventBufferInterface deadLetterQueue = createDeadLetterQueue();
            integratedHandler = new IntegratedDeadLetterHandler(
                deadLetterQueue,
                crashSafeBuffer,
                httpClient,
                getConfiguration(),
                context  // Added missing context parameter
            );
        }
        return integratedHandler;
    }

    /**
     * Emergency backup for app lifecycle events
     */
    public void performEmergencyBackup() {
        try {
            if (crashSafeBuffer != null) {
                crashSafeBuffer.emergencyBackup();
            }
            if (integratedHandler != null) {
                integratedHandler.emergencyBackup();
            }
        } catch (Exception e) {
            System.err.println("[CrashSafeFactory] Emergency backup failed: " + e.getMessage());
        }
    }

    /**
     * Check if in recovery mode
     */
    public boolean isRecovering() {
        return crashSafeBuffer != null && crashSafeBuffer.getRecoveryStats().isRecovering;
    }

    /**
     * Get recovery statistics
     */
    public String getRecoveryStats() {
        if (crashSafeBuffer != null) {
            return crashSafeBuffer.getRecoveryStats().toString();
        }
        return "No crash-safe buffer initialized";
    }
}
