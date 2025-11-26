package com.newrelic.videoagent.core.mediatailor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.newrelic.videoagent.core.mediatailor.model.MediaTailorEvent;
import com.newrelic.videoagent.core.mediatailor.model.PlayingAdBreak;

/**
 * Emits MediaTailor ad lifecycle events based on playback state changes.
 * Monitors the MediaTailorAdPlaybackTracker and emits events when ad breaks
 * and ads start/finish.
 */
public class MediaTailorEventEmitter {
    private static final String TAG = "MediaTailor.Events";
    private static final int EVENT_CHECK_INTERVAL_MS = 50; // Check every 50ms for responsive events

    private final MediaTailorAdPlaybackTracker tracker;
    private final Handler handler;
    private final Runnable eventCheckRunnable;

    private PlayingAdBreak previousPlayingAdBreak = null;
    private boolean isEmitting = false;

    /**
     * Create a new event emitter.
     *
     * @param tracker Ad playback tracker to monitor
     */
    public MediaTailorEventEmitter(MediaTailorAdPlaybackTracker tracker) {
        this.tracker = tracker;
        this.handler = new Handler(Looper.getMainLooper());

        // Create event checking runnable
        this.eventCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (isEmitting) {
                    checkAndEmitEvents();
                    handler.postDelayed(this, EVENT_CHECK_INTERVAL_MS);
                }
            }
        };

        Log.d(TAG, "MediaTailorEventEmitter initialized");
    }

    /**
     * Start emitting events.
     * Begins periodic monitoring of tracker state.
     */
    public void start() {
        if (isEmitting) {
            Log.d(TAG, "Event emitter already started");
            return;
        }

        Log.d(TAG, "Starting event emission (interval: " + EVENT_CHECK_INTERVAL_MS + "ms)");
        isEmitting = true;
        handler.post(eventCheckRunnable);
    }

    /**
     * Stop emitting events.
     * Stops periodic monitoring and cleans up.
     */
    public void stop() {
        if (!isEmitting) {
            Log.d(TAG, "Event emitter already stopped");
            return;
        }

        Log.d(TAG, "Stopping event emission");
        isEmitting = false;
        handler.removeCallbacks(eventCheckRunnable);
    }

    /**
     * Reset emitter state.
     * Clears previous playback state.
     */
    public void reset() {
        Log.d(TAG, "Resetting event emitter state");
        previousPlayingAdBreak = null;
    }

    /**
     * Check for state changes and emit appropriate events.
     * This is the core event detection logic.
     */
    private void checkAndEmitEvents() {
        PlayingAdBreak playingAdBreak = tracker.getPlayingAdBreak();

        // Case 1: Transition from no ad to ad (Ad break started, first ad started)
        if (previousPlayingAdBreak == null && playingAdBreak != null) {
            Log.d(TAG, "Event Logic: Case 1 triggered - Transition from no ad to an ad break.");
            emitEvent(new MediaTailorEvent.AdBreakStarted(playingAdBreak.getAdBreak()));
            emitEvent(new MediaTailorEvent.AdStarted(playingAdBreak.getAd(), playingAdBreak.getAdIndex()));
        }

        // Case 2: Transition between different ad breaks
        else if (previousPlayingAdBreak != null && playingAdBreak != null &&
                !previousPlayingAdBreak.getAdBreak().getId().equals(playingAdBreak.getAdBreak().getId())) {
            Log.d(TAG, "Event Logic: Case 2 triggered - Transitioning to a new ad break.");
            // Finish previous ad and ad break
            emitEvent(new MediaTailorEvent.AdFinished(previousPlayingAdBreak.getAd()));
            emitEvent(new MediaTailorEvent.AdBreakFinished(previousPlayingAdBreak.getAdBreak()));

            // Start new ad break and first ad
            emitEvent(new MediaTailorEvent.AdBreakStarted(playingAdBreak.getAdBreak()));
            emitEvent(new MediaTailorEvent.AdStarted(playingAdBreak.getAd(), playingAdBreak.getAdIndex()));
        }

        // Case 3: Transition between ads in same ad break
        else if (playingAdBreak != null && previousPlayingAdBreak != null &&
                playingAdBreak.getAdBreak().getId().equals(previousPlayingAdBreak.getAdBreak().getId()) &&
                !playingAdBreak.getAd().getId().equals(previousPlayingAdBreak.getAd().getId())) {
            Log.d(TAG, "Event Logic: Case 3 triggered - Transitioning to the next ad in the same break.");
            // Finish previous ad
            emitEvent(new MediaTailorEvent.AdFinished(previousPlayingAdBreak.getAd()));

            // Start new ad
            emitEvent(new MediaTailorEvent.AdStarted(playingAdBreak.getAd(), playingAdBreak.getAdIndex()));
        }

        // Case 4: Transition from ad to no ad (Last ad finished, ad break finished)
        else if (previousPlayingAdBreak != null && playingAdBreak == null) {
            Log.d(TAG, "Event Logic: Case 4 triggered - Transition from an ad break back to content.");
            emitEvent(new MediaTailorEvent.AdFinished(previousPlayingAdBreak.getAd()));
            emitEvent(new MediaTailorEvent.AdBreakFinished(previousPlayingAdBreak.getAdBreak()));
        }

        // Update previous state
        previousPlayingAdBreak = playingAdBreak;
    }

    /**
     * Emit a single event.
     * For POC, this logs to Android Logcat.
     *
     * @param event Event to emit
     */
    private void emitEvent(MediaTailorEvent event) {
        // Log with structured format
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.i(TAG, "EVENT: " + event.getEventType());
        Log.i(TAG, "INFO:  " + event.getEventInfo());
        Log.i(TAG, "TIME:  " + String.format("%.1fs", tracker.getCurrentPlayerTime()));
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Also log full event string for easy filtering
        Log.d(TAG, event.toString());
    }

    /**
     * Check if emitter is currently running.
     *
     * @return true if emitting, false otherwise
     */
    public boolean isEmitting() {
        return isEmitting;
    }

    /**
     * Get current playback state for debugging.
     *
     * @return State summary string
     */
    public String getStateDebugInfo() {
        return String.format("Emitter State: isEmitting=%b, previousPlayingAdBreak=%s, currentPlayingAdBreak=%s",
                isEmitting,
                previousPlayingAdBreak != null ? previousPlayingAdBreak.toString() : "null",
                tracker.getPlayingAdBreak() != null ? tracker.getPlayingAdBreak().toString() : "null");
    }
}
