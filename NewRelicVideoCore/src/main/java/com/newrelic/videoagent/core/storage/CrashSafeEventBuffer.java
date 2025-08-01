package com.newrelic.videoagent.core.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.NRVideoConstants;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.PriorityEventBuffer;
import com.newrelic.videoagent.core.harvest.SizeEstimator;
import com.newrelic.videoagent.core.utils.NRLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Crash-safe event buffer optimized for mobile/TV environments
 *
 * Features:
 * - Normal operation uses fast in-memory buffer
 * - Automatic crash detection and recovery
 * - SQLite backup only on app kill/crash or retry exhaustion
 * - TV-optimized with larger buffers and background persistence
 * - Zero performance impact during normal operation
 * - Enhanced capacity monitoring for scheduler startup
 */
public class CrashSafeEventBuffer implements EventBufferInterface {

    private final PriorityEventBuffer memoryBuffer;
    private final VideoEventStorage storage;
    private final SharedPreferences crashPrefs;
    private final boolean isTVDevice;

    // TV vs Mobile optimization
    private final int emergencyBackupThreshold;

    // Crash detection
    private static final String CRASH_PREF_NAME = "nr_video_crash_detection";
    private static final String KEY_SESSION_ACTIVE = "session_active";
    private static final String KEY_LAST_EVENT_COUNT = "last_event_count";

    // Recovery state
    private volatile boolean isRecovering = false;
    private volatile boolean hasPendingRecovery = false; // New flag for deferred recovery
    private final AtomicInteger lastEventCount = new AtomicInteger(0);

    public CrashSafeEventBuffer(Context context, NRVideoConfiguration configuration, VideoEventStorage videoEventStorage) {
        this.memoryBuffer = new PriorityEventBuffer(configuration.isTV());
        this.storage = videoEventStorage; // Use injected storage instead of singleton
        this.crashPrefs = context.getSharedPreferences(CRASH_PREF_NAME, Context.MODE_PRIVATE);

        // Use the already detected device type from configuration instead of duplicating detection logic
        this.isTVDevice = configuration.isTV();

        // TV optimization: larger thresholds for better performance
        this.emergencyBackupThreshold = isTVDevice ? 200 : 100;

        // Check for crash recovery on startup
        checkCrashRecovery();
        markSessionStart();
    }

    @Override
    public void setOverflowCallback(OverflowCallback callback) {
        memoryBuffer.setOverflowCallback(callback);
    }

    @Override
    public void setCapacityCallback(CapacityCallback callback) {
        memoryBuffer.setCapacityCallback(callback);
    }

    @Override
    public void addEvent(Map<String, Object> event) {
        // Always add to memory buffer first (fast path)
        memoryBuffer.addEvent(event);
        lastEventCount.incrementAndGet();

        // TV optimization: periodic background backup to prevent large crash losses
        if (isTVDevice && lastEventCount.get() % emergencyBackupThreshold == 0) {
            updateCrashDetectionCounter();
        }
    }

    @Override
    public List<Map<String, Object>> pollBatchByPriority(int maxSizeBytes, SizeEstimator sizeEstimator, String priority) {
        // Primary: get events from memory (normal operation)
        List<Map<String, Object>> batch = new ArrayList<>(memoryBuffer.pollBatchByPriority(maxSizeBytes, sizeEstimator, priority));

        // Recovery: Always try to recover SQLite events when in recovery mode
        if (isRecovering) {
            // Calculate remaining capacity in the batch (size-based or count-based)
            int remainingCapacity = Math.max(0, getOptimalBatchSize(priority) - batch.size());

            // Always try to recover at least some events, even if batch is full
            int minRecoverySize = Math.max(remainingCapacity, isRecovering ? 5 : 0); // Minimum 5 events when recovering

            if (minRecoverySize > 0) {
                List<Map<String, Object>> recoveryEvents = pollRecoveryEvents(priority, minRecoverySize);
                batch.addAll(recoveryEvents);
            }
        }

        return batch;
    }

    @Override
    public int getEventCount() {
        // Return combined count during recovery, memory-only otherwise
        int memoryCount = memoryBuffer.getEventCount();
        return isRecovering ? memoryCount + storage.getEventCount() : memoryCount;
    }

    @Override
    public boolean isEmpty() {
        return memoryBuffer.isEmpty() && (!isRecovering || storage.isEmpty());
    }

    @Override
    public void cleanup() {
        memoryBuffer.cleanup();
        storage.cleanup();
        markSessionEnd();
    }

    /**
     * CRITICAL: Emergency backup for app kill/crash scenarios
     */
    public void emergencyBackup() {
        try {
            List<Map<String, Object>> liveEvents = memoryBuffer.pollBatchByPriority(Integer.MAX_VALUE, null, NRVideoConstants.EVENT_TYPE_LIVE);
            List<Map<String, Object>> ondemandEvents = memoryBuffer.pollBatchByPriority(Integer.MAX_VALUE, null, NRVideoConstants.EVENT_TYPE_ONDEMAND);

            if (!liveEvents.isEmpty() || !ondemandEvents.isEmpty()) {
                storage.backupEvents(liveEvents, ondemandEvents);
                NRLog.d("Emergency backup: " + (liveEvents.size() + ondemandEvents.size()) + " events saved");
            }
        } catch (Exception e) {
            NRLog.e("Emergency backup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Backup failed events when retries exhausted
     */
    public void backupFailedEvents(List<Map<String, Object>> failedEvents) {
        if (failedEvents != null && !failedEvents.isEmpty()) {
            try {
                storage.backupFailedEvents(failedEvents);
                if (!isRecovering) {
                    isRecovering = true;
                    NRLog.d("Recovery mode enabled for " + failedEvents.size() + " failed events");
                }
            } catch (Exception e) {
                NRLog.e("Failed to backup events: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Check for crash recovery on app startup - defer until first successful harvest
     */
    private void checkCrashRecovery() {
        boolean wasSessionActive = crashPrefs.getBoolean(KEY_SESSION_ACTIVE, false);

        if (wasSessionActive) {
            // Previous session didn't end cleanly - likely a crash
            if (storage.hasBackupData()) {
                hasPendingRecovery = true; // Set pending instead of immediate recovery
                NRLog.w("Crash detected - recovery will start after first successful harvest");
            }
        }
    }

    /**
     * Poll recovery events in small batches
     */
    private List<Map<String, Object>> pollRecoveryEvents(String priority, int maxSize) {
        try {
            List<Map<String, Object>> recoveryEvents = storage.pollEvents(priority, maxSize);

            if (!recoveryEvents.isEmpty()) {
                NRLog.d("Recovered " + recoveryEvents.size() + " events (" + priority + ")");

                // Check if recovery is complete
                if (storage.isEmpty()) {
                    isRecovering = false;
                    NRLog.i("Recovery complete");
                }
            }

            return recoveryEvents;
        } catch (Exception e) {
            NRLog.e("Recovery polling failed: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Mark session start for crash detection
     */
    private void markSessionStart() {
        crashPrefs.edit().putBoolean(KEY_SESSION_ACTIVE, true).apply();
    }

    /**
     * Mark session end (clean shutdown)
     */
    private void markSessionEnd() {
        crashPrefs.edit().putBoolean(KEY_SESSION_ACTIVE, false).apply();
    }

    /**
     * Update crash detection counter for TV optimization
     */
    private void updateCrashDetectionCounter() {
        crashPrefs.edit().putInt(KEY_LAST_EVENT_COUNT, lastEventCount.get()).apply();
    }

    // Helper methods
    private int getOptimalBatchSize(String priority) {
        int baseSize = NRVideoConstants.EVENT_TYPE_LIVE.equals(priority) ? 50 : 100;
        return isTVDevice ? baseSize * 2 : baseSize; // TV can handle larger batches
    }

    /**
     * Get recovery statistics
     */
    public RecoveryStats getRecoveryStats() {
        return new RecoveryStats(
            isRecovering,
            storage.getEventCount(),
            memoryBuffer.getEventCount(),
            isTVDevice
        );
    }

    /**
     * Called by HarvestManager after first successful harvest to start pending recovery
     * This ensures scheduler is running before recovery begins
     */
    @Override
    public void onSuccessfulHarvest() {
        boolean shouldStartRecovery = false;

        // Case 1: Pending recovery from crash detection
        if (hasPendingRecovery && !isRecovering) {
            hasPendingRecovery = false;
            shouldStartRecovery = true;
            NRLog.i("Starting crash recovery after successful harvest - scheduler is now active");
        }

        // Case 2: Check if there's SQLite data to recover (even without pending flag)
        if (!isRecovering && storage.hasBackupData()) {
            shouldStartRecovery = true;
            NRLog.i("Starting SQLite recovery after successful harvest - backup data detected");
        }

        if (shouldStartRecovery) {
            isRecovering = true;
            NRLog.i("Recovery mode activated - SQLite events will be included in future harvests");
        }
    }

    public static class RecoveryStats {
        public final boolean isRecovering;
        public final int backupEvents;
        public final int memoryEvents;
        public final boolean isTVDevice;

        RecoveryStats(boolean recovering, int backup, int memory, boolean tv) {
            this.isRecovering = recovering;
            this.backupEvents = backup;
            this.memoryEvents = memory;
            this.isTVDevice = tv;
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.US, "Recovery{recovering=%s, backup=%d, memory=%d, tv=%s}",
                               isRecovering, backupEvents, memoryEvents, isTVDevice);
        }
    }
}
