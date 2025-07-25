package com.newrelic.videoagent.core.storage;

import android.content.Context;
import android.util.Log;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.harvest.HarvestComponentFactory;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.HttpClientInterface;
import com.newrelic.videoagent.core.harvest.OptimizedHttpClient;
import com.newrelic.videoagent.core.harvest.DeadLetterEventBuffer;

/**
 * Clean integration factory for crash-safe storage
 * Drop-in replacement that adds crash safety with zero performance impact
 */
public class CrashSafeHarvestFactory extends HarvestComponentFactory {
    private static final String TAG = "NRVideo.CrashSHFactory";
    private final Context context;
    private CrashSafeEventBuffer crashSafeBuffer;
    private IntegratedDeadLetterHandler integratedHandler;
    private VideoEventStorage videoEventStorage; // Managed storage instance

    public CrashSafeHarvestFactory(NRVideoConfiguration configuration, Context context) {
        super(configuration, context);
        this.context = context;
        this.videoEventStorage = new VideoEventStorage(context); // Create instance here
    }

    @Override
    public EventBufferInterface createEventBuffer() {
        if (crashSafeBuffer == null) {
            crashSafeBuffer = new CrashSafeEventBuffer(context, super.getConfiguration(), videoEventStorage);
        }
        return crashSafeBuffer;
    }

    @Override
    public EventBufferInterface createDeadLetterQueue() {
        // Use dedicated dead letter buffer - cleaner separation of concerns
        return new DeadLetterEventBuffer(getConfiguration().isTV());
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
                getConfiguration()
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
            Log.w(TAG, "[CrashSafeFactory] Emergency backup failed: " + e.getMessage());
        }
    }


    /**
     * Check if in recovery mode
     */
    public boolean isRecovering() {
        return crashSafeBuffer.getRecoveryStats().isRecovering;
    }

    /**
     * Get recovery statistics
     */
    public String getRecoveryStats() {
        return crashSafeBuffer.getRecoveryStats().toString();
    }
}
