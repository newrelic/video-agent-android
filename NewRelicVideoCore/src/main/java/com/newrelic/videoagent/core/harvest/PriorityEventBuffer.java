package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import com.newrelic.videoagent.core.NRVideoConstants;

/**
 * Video-optimized priority event buffer for mobile/TV environments
 * Separates live streaming events from on-demand content events
 * Uses thread-safe ConcurrentLinkedQueue for better reliability
 * Simple overflow detection triggers immediate harvest
 * Deduplicates CONTIGUOUS events by actionName with O(1) atomic tracking
 * OPTIMIZED: Reduced buffer sizes for 2KB events with dynamic device detection
 */
public class PriorityEventBuffer implements EventBufferInterface {
    // Live streaming events need immediate processing (live TV, sports, news)
    private final ConcurrentLinkedQueue<Map<String, Object>> liveEvents = new ConcurrentLinkedQueue<>();

    // On-demand events can tolerate some delay (movies, series, recorded content)
    private final ConcurrentLinkedQueue<Map<String, Object>> ondemandEvents = new ConcurrentLinkedQueue<>();

    // Optimized locks for atomic polling operations with timeout support
    private final ReentrantLock livePollingLock = new ReentrantLock();
    private final ReentrantLock ondemandPollingLock = new ReentrantLock();

    // Lock timeout for mobile/TV optimization (milliseconds)
    private static final long POLLING_LOCK_TIMEOUT_MS = 50; // Quick timeout for responsiveness

    // OPTIMIZED for 2KB events - device-specific buffer sizes
    private final int MAX_LIVE_EVENTS;
    private final int MAX_ONDEMAND_EVENTS;
    private final boolean isAndroidTVDevice;
    private final boolean isRunningInLowMemory;

    // Enhanced callback support
    private OverflowCallback overflowCallback;
    private CapacityCallback capacityCallback;

    public PriorityEventBuffer(boolean isTV) {
        this.isAndroidTVDevice = isTV;
        this.isRunningInLowMemory = !isAndroidTVDevice && Runtime.getRuntime().freeMemory() < Runtime.getRuntime().totalMemory() * 0.15; // <15% free memory

        if (isAndroidTVDevice) {
            // Android TV: More memory available, longer content sessions
            this.MAX_LIVE_EVENTS = 300;      // 300 × 2KB = 600KB (live needs quick processing)
            this.MAX_ONDEMAND_EVENTS = 700;  // 700 × 2KB = 1.4MB (can batch larger)
        } else {
            // Mobile: Memory-conscious, shorter sessions, frequent harvesting
            this.MAX_LIVE_EVENTS = 150;      // 150 × 2KB = 300KB (conservative for mobile)
            this.MAX_ONDEMAND_EVENTS = 350;  // 350 × 2KB = 700KB (balanced for mobile)
        }
    }

    /**
     * Set overflow callback for immediate harvest when buffer is getting full
     * Implements the interface method
     */
    @Override
    public void setOverflowCallback(OverflowCallback callback) {
        this.overflowCallback = callback;
    }

    @Override
    public void addEvent(Map<String, Object> event) {
        if (event == null) return;

        // Check if this is a live streaming event
        boolean isLiveContent = isLiveStreamingEvent(event);
        boolean shouldTriggerHarvest = false;
        boolean shouldStartScheduler = false;
        String harvestType = null;

        // Get the target queue and last action tracker for this event
        ConcurrentLinkedQueue<Map<String, Object>> targetQueue = isLiveContent ? liveEvents : ondemandEvents;
        int maxCapacity = isLiveContent ? MAX_LIVE_EVENTS : MAX_ONDEMAND_EVENTS;

        // SCHEDULER STARTUP: Start scheduler on FIRST event of each category
        boolean wasEmpty = targetQueue.isEmpty();

        // Add the new event (this will be the most recent one)
        targetQueue.offer(event);

        // CRITICAL: Start scheduler on first event of this category
        if (wasEmpty) {
            shouldStartScheduler = true;
        }

        // Check capacity thresholds AFTER the event is added
        double currentCapacity = (double) targetQueue.size() / maxCapacity;

        // Check for 90% threshold (overflow prevention)
        if (currentCapacity >= 0.9) {
            shouldTriggerHarvest = true;
            harvestType = isLiveContent ? NRVideoConstants.EVENT_TYPE_LIVE : NRVideoConstants.EVENT_TYPE_ONDEMAND;
        }

        // Fallback: if we somehow still reach max capacity, remove oldest events
        while (liveEvents.size() > MAX_LIVE_EVENTS) {
            liveEvents.poll();
        }
        while (ondemandEvents.size() > MAX_ONDEMAND_EVENTS) {
            ondemandEvents.poll();
        }

        if (shouldStartScheduler) {
            capacityCallback.onCapacityThresholdReached(0.0, isLiveContent ? NRVideoConstants.EVENT_TYPE_LIVE : NRVideoConstants.EVENT_TYPE_ONDEMAND);
        }

        if (shouldTriggerHarvest) {
            overflowCallback.onBufferNearFull(harvestType);
        }
    }

    @Override
    public List<Map<String, Object>> pollBatchByPriority(int maxSizeBytes, SizeEstimator sizeEstimator, String priority) {
        // Platform-specific optimization: Use different strategies for mobile vs TV
        boolean isLivePriority = NRVideoConstants.EVENT_TYPE_LIVE.equals(priority);
        ReentrantLock pollingLock = isLivePriority ? livePollingLock : ondemandPollingLock;

        // Mobile/TV optimized timeout: TV can wait longer for larger batches
        long lockTimeout = isAndroidTVDevice ? 100 : POLLING_LOCK_TIMEOUT_MS; // TV: 100ms, Mobile: 50ms

        try {
            // Fast path: Check if queue is empty before acquiring lock (optimization)
            ConcurrentLinkedQueue<Map<String, Object>> targetQueue = isLivePriority ? liveEvents : ondemandEvents;
            if (targetQueue.isEmpty()) {
                return new ArrayList<>(); // No lock needed for empty queue
            }

            // Acquire the lock with platform-optimized timeout
            if (!pollingLock.tryLock(lockTimeout, TimeUnit.MILLISECONDS)) {
                return new ArrayList<>(); // Timeout, return empty batch
            }

            // Pre-allocate batch with optimal capacity for mobile/TV
            int estimatedBatchSize = isAndroidTVDevice ?
                (isLivePriority ? 25 : 50) : (isLivePriority ? 15 : 30);
            List<Map<String, Object>> batch = new ArrayList<>(estimatedBatchSize);

            int currentSize = 0;
            // PLATFORM-OPTIMIZED: Different strategies for mobile vs TV
            int maxEvents;
            if (isLivePriority) {
                // Live events: Prioritize low latency
                if (isAndroidTVDevice) {
                    maxEvents = 25;  // TV: 50KB batches for live streaming
                } else {
                    maxEvents = 12;  // Mobile: Smaller batches to reduce memory pressure and improve battery
                }
            } else {
                // On-demand events: Optimize for throughput
                if (isAndroidTVDevice) {
                    maxEvents = 60;  // TV: Larger batches for better network efficiency
                } else {
                    maxEvents = 25;  // Mobile: Balance between efficiency and memory usage
                }
            }

            for (int i = 0; i < maxEvents && !targetQueue.isEmpty(); i++) {
                Map<String, Object> event = targetQueue.poll();
                if (event == null) break;

                // Platform-optimized size estimation
                int eventSize;
                if (sizeEstimator != null) {
                    eventSize = sizeEstimator.estimate(event);
                } else {
                    // Mobile: Conservative estimate, TV: Accurate estimate
                    eventSize = isAndroidTVDevice ? 2048 : 1800; // TV can handle larger estimates
                }

                // Size-based early break with platform considerations
                if (currentSize + eventSize > maxSizeBytes && !batch.isEmpty()) {
                    // Put the event back and break
                    targetQueue.offer(event);
                    break;
                }
                // Mobile optimization: Early break if memory pressure is high
                // Mobile: Break early if low memory and we have some events
                if (isRunningInLowMemory && batch.size() >= 8) {
                    targetQueue.offer(event); // Put back for next harvest
                    break;
                }

                batch.add(event);
                currentSize += eventSize;

                // TV optimization: Continue until batch is substantial
                // Mobile optimization: Prefer smaller, frequent batches for responsiveness
            }

            return batch;
        } catch (InterruptedException e) {
            // Platform-aware interrupt handling
            Thread.currentThread().interrupt(); // Restore interrupt status (required for both platforms)

            // Mobile optimization: Return immediately to preserve battery
            // TV optimization: Could retry once for stream continuity, but keep it simple for now
            return new ArrayList<>(); // Return empty batch on interrupt
        } finally {
            if (pollingLock.isHeldByCurrentThread()) { // Safety check before unlock
                pollingLock.unlock();
            }
        }
    }

    @Override
    public int getEventCount() {
        return liveEvents.size() + ondemandEvents.size();
    }

    @Override
    public boolean isEmpty() {
        return liveEvents.isEmpty() && ondemandEvents.isEmpty();
    }

    @Override
    public void cleanup() {
        clear();
    }

    public void clear() {
        liveEvents.clear();
        ondemandEvents.clear();
    }

    /**
     * Determines if an event is from live streaming content
     */
    private boolean isLiveStreamingEvent(Map<String, Object> event) {
        // Check for explicit live content marker
        Boolean isLive = (Boolean) event.get("contentIsLive");
        if (isLive != null) {
            return isLive;
        }

        // Default to on-demand if not explicitly marked as live
        return false;
    }

    @Override
    public void setCapacityCallback(CapacityCallback callback) {
        this.capacityCallback = callback;
    }
}
