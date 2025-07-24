package com.newrelic.videoagent.core.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.newrelic.videoagent.core.NRVideoConfiguration;
import com.newrelic.videoagent.core.harvest.EventBufferInterface;
import com.newrelic.videoagent.core.harvest.PriorityEventBuffer;
import com.newrelic.videoagent.core.harvest.SizeEstimator;

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

    private static final String TAG = "NRVideo.CrashSafe";

    private final PriorityEventBuffer memoryBuffer;
    private final VideoEventStorage storage;
    private final SharedPreferences crashPrefs;
    private final boolean isTVDevice;

    // TV vs Mobile optimization
    private final int emergencyBackupThreshold;
    private final int recoveryBatchSize;
    private final int maxCapacity;

    // Crash detection
    private static final String CRASH_PREF_NAME = "nr_video_crash_detection";
    private static final String KEY_SESSION_ACTIVE = "session_active";
    private static final String KEY_LAST_EVENT_COUNT = "last_event_count";

    // Recovery state
    private volatile boolean isRecovering = false;
    private volatile boolean hasPendingRecovery = false; // New flag for deferred recovery
    private final AtomicInteger lastEventCount = new AtomicInteger(0);

    public CrashSafeEventBuffer(Context context, NRVideoConfiguration configuration, VideoEventStorage videoEventStorage) {
        this.memoryBuffer = new PriorityEventBuffer();
        this.storage = videoEventStorage; // Use injected storage instead of singleton
        this.crashPrefs = context.getSharedPreferences(CRASH_PREF_NAME, Context.MODE_PRIVATE);

        // Use the already detected device type from configuration instead of duplicating detection logic
        this.isTVDevice = configuration.isTV();

        // TV optimization: larger thresholds for better performance
        this.emergencyBackupThreshold = isTVDevice ? 200 : 100;
        this.recoveryBatchSize = isTVDevice ? 50 : 20;
        this.maxCapacity = isTVDevice ? 2000 : 1000;

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
    public int getMaxCapacity() {
        return maxCapacity;
    }

    @Override
    public boolean hasReachedCapacityThreshold(double threshold) {
        return memoryBuffer.hasReachedCapacityThreshold(threshold);
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

        // Recovery: gradually mix in backup events if recovering from crash
        if (isRecovering && batch.size() < getOptimalBatchSize(priority)) {
            batch.addAll(pollRecoveryEvents(priority));
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
            List<Map<String, Object>> liveEvents = memoryBuffer.pollBatchByPriority(Integer.MAX_VALUE, null, "live");
            List<Map<String, Object>> ondemandEvents = memoryBuffer.pollBatchByPriority(Integer.MAX_VALUE, null, "ondemand");

            if (!liveEvents.isEmpty() || !ondemandEvents.isEmpty()) {
                storage.backupEvents(liveEvents, ondemandEvents);
                Log.d(TAG, "Emergency backup: " + (liveEvents.size() + ondemandEvents.size()) + " events saved");
            }
        } catch (Exception e) {
            Log.e(TAG, "Emergency backup failed: " + e.getMessage(), e);
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
                    Log.d(TAG, "Recovery mode enabled for " + failedEvents.size() + " failed events");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to backup events: " + e.getMessage(), e);
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
                Log.w(TAG, "Crash detected - recovery will start after first successful harvest");
            }
        }
    }

    /**
     * Poll recovery events in small batches
     */
    private List<Map<String, Object>> pollRecoveryEvents(String priority) {
        try {
            List<Map<String, Object>> recoveryEvents = storage.pollEvents(priority, recoveryBatchSize);

            if (!recoveryEvents.isEmpty()) {
                Log.d(TAG, "Recovered " + recoveryEvents.size() + " events (" + priority + ")");

                // Check if recovery is complete
                if (storage.isEmpty()) {
                    isRecovering = false;
                    Log.i(TAG, "Recovery complete");
                }
            }

            return recoveryEvents;
        } catch (Exception e) {
            Log.e(TAG, "Recovery polling failed: " + e.getMessage(), e);
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
        int baseSize = "live".equals(priority) ? 50 : 100;
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
    public void onSuccessfulHarvest() {
        if (hasPendingRecovery && !isRecovering) {
            isRecovering = true;
            hasPendingRecovery = false;
            Log.i(TAG, "Starting recovery after successful harvest - scheduler is now active");
        }
    }

    /**
     * Check if recovery is pending (for debugging/monitoring)
     */
    public boolean isRecoveryPending() {
        return hasPendingRecovery;
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
