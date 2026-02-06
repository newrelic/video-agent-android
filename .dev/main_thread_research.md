# Root Cause Analysis - GitHub Issue #44

## **Issue Summary**
The New Relic Video Agent for Android is reporting consecutive `CONTENT_DROPPED_FRAMES` events individually instead of batching them, and this is causing Application Not Responding (ANR) errors when frames are dropped continuously.

---

## **Root Causes Identified**

### **1. No Batching/Debouncing Mechanism**

**Location:** `NRTrackerExoPlayer.java:535-540`

```java
@Override
public void onDroppedVideoFrames(@NonNull AnalyticsListener.EventTime eventTime, int droppedFrames, long elapsedMs) {
    NRLog.d("onDroppedVideoFrames analytics");
    if (!player.isPlayingAd()) {
        sendDroppedFrame(droppedFrames, (int) elapsedMs);
    }
}
```

**Problem:** The ExoPlayer `AnalyticsListener.onDroppedVideoFrames()` callback is invoked **every time frames are dropped**. The current implementation immediately sends an event for each callback without any accumulation, debouncing, or time-window batching.

**Impact:** If the player drops frames continuously (e.g., during poor network conditions or device performance issues), each individual drop triggers a separate event. This results in potentially hundreds of events per second.

---

### **2. Main Thread Blocking (Critical ANR Cause)**

**Location:** `NRVideoTracker.java:60`

```java
heartbeatHandler = new Handler();
```

**Problem:** The `Handler` is instantiated **without a Looper argument**, which means it defaults to using the **main thread's Looper**. This causes all event processing to happen on the Android main thread.

**Event Flow Chain:**
1. `onDroppedVideoFrames()` → called on **main thread** (ExoPlayer's AnalyticsListener callbacks run on main thread)
2. `sendDroppedFrame()` → executes on **main thread**
3. `sendVideoEvent()` → executes on **main thread**
4. `NRTracker.sendEvent()` → executes on **main thread**
5. `NRVideo.recordEvent()` → executes on **main thread**
6. `HarvestManager.recordEvent()` → executes on **main thread**
7. `EventBuffer.addEvent()` → executes on **main thread**

**Impact:** When frames are dropped continuously:
- Each dropped frame event blocks the main thread
- The main thread becomes saturated with event processing
- UI becomes unresponsive
- More frames drop due to the blocked main thread
- This creates a **cascade failure** → more dropped frames → more events → worse ANR

---

### **3. Synchronous Event Recording**

**Location:** `HarvestManager.java:46-54`

```java
public void recordEvent(String eventType, Map<String, Object> attributes) {
    if (eventType != null && !eventType.trim().isEmpty()) {
        Map<String, Object> event = new HashMap<>(attributes != null ? attributes : new HashMap<>());
        event.put("eventType", eventType);
        event.put("timestamp", System.currentTimeMillis());

        // Add to event buffer - this will trigger capacity monitoring
        factory.getEventBuffer().addEvent(event);
    }
}
```

**Problem:** Event recording involves:
- Creating new HashMap objects
- Copying attributes
- Adding to ConcurrentLinkedQueue
- Checking capacity thresholds
- Potentially triggering overflow callbacks

All of these operations happen **synchronously** on the calling thread (which is the main thread).

---

### **4. ExoPlayer's Reporting Behavior**

According to ExoPlayer's `AnalyticsListener` documentation, `onDroppedVideoFrames()` can be called:
- **Periodically** during playback with accumulated counts
- **Per rendering cycle** in some implementations
- **Whenever the renderer detects dropped frames**

The current implementation treats each callback as a separate event, which doesn't align with the intended batching behavior that ExoPlayer already provides.

---

## **Why This Causes ANRs**

### **ANR Cascade Effect:**

```
High frame drops → onDroppedVideoFrames() called frequently
                 ↓
        sendDroppedFrame() on main thread
                 ↓
        Event processing blocks main thread (HashMap creation, queue operations)
                 ↓
        Main thread unable to process UI/rendering
                 ↓
        More frames drop due to blocked main thread
                 ↓
        More onDroppedVideoFrames() callbacks
                 ↓
        CYCLE REPEATS → ANR after 5 seconds
```

Android triggers an ANR when the main thread is blocked for more than **5 seconds** for foreground activities. During continuous frame dropping, the accumulation of synchronous event processing can easily exceed this threshold.

---

## **Recommendations & Proposed Solutions**

### **Solution 1: Implement Time-Window Batching (High Priority)**

**Location:** Add to `NRTrackerExoPlayer.java`

Create a batching mechanism that accumulates dropped frame events within a configurable time window (e.g., 1 second):

```java
private final Handler droppedFrameHandler = new Handler(Looper.getMainLooper());
private final AtomicInteger accumulatedDroppedFrames = new AtomicInteger(0);
private final AtomicInteger accumulatedElapsedMs = new AtomicInteger(0);
private Runnable flushDroppedFramesRunnable;

private static final int DROPPED_FRAME_BATCH_WINDOW_MS = 1000; // 1 second window

@Override
public void onDroppedVideoFrames(@NonNull AnalyticsListener.EventTime eventTime, int droppedFrames, long elapsedMs) {
    if (player.isPlayingAd()) return;

    // Accumulate dropped frames
    accumulatedDroppedFrames.addAndGet(droppedFrames);
    accumulatedElapsedMs.addAndGet((int) elapsedMs);

    // Remove any pending flush and schedule new one
    if (flushDroppedFramesRunnable != null) {
        droppedFrameHandler.removeCallbacks(flushDroppedFramesRunnable);
    }

    flushDroppedFramesRunnable = () -> {
        int totalFrames = accumulatedDroppedFrames.getAndSet(0);
        int totalElapsed = accumulatedElapsedMs.getAndSet(0);

        if (totalFrames > 0) {
            sendDroppedFrame(totalFrames, totalElapsed);
        }
    };

    droppedFrameHandler.postDelayed(flushDroppedFramesRunnable, DROPPED_FRAME_BATCH_WINDOW_MS);
}
```

**Benefits:**
- Aggregates multiple dropped frame events into a single event
- Reduces event volume by up to 99% during continuous frame drops
- Maintains accurate total counts

---

### **Solution 2: Move Event Processing to Background Thread (Critical for ANR)**

**Location:** Modify `NRVideoTracker.java:60`

Replace the main thread Handler with a background thread Handler:

```java
private final HandlerThread eventThread = new HandlerThread("NRVideoEventThread", Process.THREAD_PRIORITY_BACKGROUND);
private final Handler eventHandler;

public NRVideoTracker() {
    // ... existing code ...

    // Create background thread for event processing
    eventThread.start();
    eventHandler = new Handler(eventThread.getLooper());

    // ... rest of initialization ...
}

// Modify sendDroppedFrame to post to background thread
public void sendDroppedFrame(int count, int elapsed) {
    eventHandler.post(() -> {
        Map<String, Object> attr = new HashMap<>();
        attr.put("lostFrames", count);
        attr.put("lostFramesDuration", elapsed);

        if (getState().isAd) {
            sendVideoAdEvent("AD_DROPPED_FRAMES", attr);
        } else {
            sendVideoEvent("CONTENT_DROPPED_FRAMES", attr);
        }
    });
}
```

**Benefits:**
- Removes blocking operations from main thread
- Prevents ANRs even during heavy frame dropping
- Maintains UI responsiveness

**Important:** Ensure proper thread synchronization when accessing shared state.

---

### **Solution 3: Implement Adaptive Throttling**

Add intelligent throttling that adapts based on event frequency:

```java
private static final int MAX_DROPPED_FRAME_EVENTS_PER_SECOND = 10;
private final AtomicLong lastDroppedFrameEventTime = new AtomicLong(0);
private final AtomicInteger droppedFrameEventCount = new AtomicInteger(0);

private boolean shouldThrottleDroppedFrameEvent() {
    long currentTime = System.currentTimeMillis();
    long lastTime = lastDroppedFrameEventTime.get();

    // Reset counter if more than 1 second has passed
    if (currentTime - lastTime > 1000) {
        droppedFrameEventCount.set(0);
        lastDroppedFrameEventTime.set(currentTime);
        return false;
    }

    // Check if we've exceeded the rate limit
    return droppedFrameEventCount.incrementAndGet() > MAX_DROPPED_FRAME_EVENTS_PER_SECOND;
}
```

---

### **Solution 4: Configure ExoPlayer's Analytics Reporting**

ExoPlayer's AnalyticsCollector may have configuration options for how frequently it reports dropped frames. Review if there's a way to configure the reporting interval at the ExoPlayer level.

---

### **Solution 5: Add Circuit Breaker Pattern**

Implement a circuit breaker that temporarily stops sending dropped frame events if the rate becomes excessive:

```java
private static final int CIRCUIT_BREAKER_THRESHOLD = 100; // events per second
private static final long CIRCUIT_BREAKER_RESET_TIME_MS = 5000; // 5 seconds

private enum CircuitState { CLOSED, OPEN }
private volatile CircuitState circuitState = CircuitState.CLOSED;
private final AtomicLong circuitOpenedTime = new AtomicLong(0);
```

---

## **Recommended Implementation Priority**

1. **CRITICAL (Immediate):** Solution 2 - Move to background thread (fixes ANR)
2. **HIGH:** Solution 1 - Time-window batching (fixes event spam)
3. **MEDIUM:** Solution 3 - Adaptive throttling (additional safety)
4. **LOW:** Solution 5 - Circuit breaker (edge case protection)

---

## **Testing Recommendations**

1. **Stress Test:** Simulate continuous frame dropping with `adb shell dumpsys gfxinfo`
2. **Network Throttling:** Test with poor network conditions to induce buffering/frame drops
3. **Low-End Devices:** Test on older Android devices with limited CPU/GPU
4. **Monitor Metrics:**
   - Event count per minute
   - Main thread blocking time
   - ANR occurrences
   - Memory usage

---

## **Additional Observations**

- The `PriorityEventBuffer` uses `ConcurrentLinkedQueue` which is thread-safe, but the operations are still synchronous
- The `HarvestManager` already has background threading for the harvest scheduler (`MultiTaskHarvestScheduler:78`), but the event recording itself is synchronous
- The heartbeat handler at `NRVideoTracker.java:60` also uses the main thread, which could compound the issue

---

## **Key File References**

- **Dropped Frame Handler:** `NRExoPlayerTracker/src/main/java/com/newrelic/videoagent/exoplayer/tracker/NRTrackerExoPlayer.java:535-540`
- **Main Thread Handler:** `NewRelicVideoCore/src/main/java/com/newrelic/videoagent/core/tracker/NRVideoTracker.java:60`
- **Event Recording:** `NewRelicVideoCore/src/main/java/com/newrelic/videoagent/core/harvest/HarvestManager.java:46-54`
- **Event Flow:** `NewRelicVideoCore/src/main/java/com/newrelic/videoagent/core/tracker/NRTracker.java:141-163`
- **Event Buffer:** `NewRelicVideoCore/src/main/java/com/newrelic/videoagent/core/harvest/PriorityEventBuffer.java`
