package com.newrelic.videoagent.core.mediatailor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.exoplayer.ExoPlayer;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorAdBreak;
import com.newrelic.videoagent.core.mediatailor.model.MediaTailorLinearAd;
import com.newrelic.videoagent.core.mediatailor.model.PlayingAdBreak;

import java.util.List;

/**
 * Tracks ad playback by monitoring ExoPlayer's current position.
 * Detects when the player enters/exits ad breaks and individual ads.
 * Runs periodic checks (every 100ms) to update playback state.
 */
public class MediaTailorAdPlaybackTracker {
    private static final String TAG = "MediaTailor.Tracker";
    private static final int TRACKING_INTERVAL_MS = 100; // Poll every 100ms

    private final ExoPlayer player;
    private final List<MediaTailorAdBreak> adBreaks;
    private final Handler handler;
    private final Runnable trackingRunnable;

    // Tracking state
    private int currentAdBreakIndex = 0;
    private int currentAdIndex = 0;
    private MediaTailorAdBreak nextAdBreak = null;
    private PlayingAdBreak playingAdBreak = null;
    private boolean isTracking = false;

    /**
     * Create a new ad playback tracker.
     *
     * @param player ExoPlayer instance to monitor
     * @param adBreaks List of ad breaks from MediaTailor tracking data
     */
    public MediaTailorAdPlaybackTracker(ExoPlayer player, List<MediaTailorAdBreak> adBreaks) {
        this.player = player;
        this.adBreaks = adBreaks;
        this.handler = new Handler(Looper.getMainLooper());

        // Create tracking runnable
        this.trackingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTracking) {
                    trackAdBreaks();
                    handler.postDelayed(this, TRACKING_INTERVAL_MS);
                }
            }
        };

        Log.d(TAG, "MediaTailorAdPlaybackTracker initialized with " + adBreaks.size() + " ad breaks");
    }

    /**
     * Start tracking ad playback.
     * Begins periodic polling of player position.
     */
    public void start() {
        if (isTracking) {
            Log.d(TAG, "Tracker already started");
            return;
        }

        Log.d(TAG, "Starting ad playback tracking (interval: " + TRACKING_INTERVAL_MS + "ms)");
        isTracking = true;
        handler.post(trackingRunnable);
    }

    /**
     * Stop tracking ad playback.
     * Stops periodic polling and cleans up.
     */
    public void stop() {
        if (!isTracking) {
            Log.d(TAG, "Tracker already stopped");
            return;
        }

        Log.d(TAG, "Stopping ad playback tracking");
        isTracking = false;
        handler.removeCallbacks(trackingRunnable);
    }

    /**
     * Reset tracking state to beginning.
     * Useful for replaying content.
     */
    public void reset() {
        Log.d(TAG, "Resetting tracker state");
        currentAdBreakIndex = 0;
        currentAdIndex = 0;
        nextAdBreak = null;
        playingAdBreak = null;
    }

    /**
     * Core tracking logic - called periodically.
     * Detects which ad break and ad is currently playing based on player position.
     */
    private void trackAdBreaks() {
        // Get current player time in seconds
        double playerTime = player.getCurrentPosition() / 1000.0;

        // Check if we have ad breaks to track
        if (adBreaks == null || adBreaks.isEmpty()) {
            return;
        }

        // Advance currentAdBreakIndex if player time has moved past current ad break
        while (currentAdBreakIndex < adBreaks.size() - 1 &&
               playerTime >= adBreaks.get(currentAdBreakIndex).getEndTime()) {
            currentAdBreakIndex++;
            currentAdIndex = 0; // Reset ad index for new break
            Log.d(TAG, "Advanced to ad break index: " + currentAdBreakIndex);
        }

        // Get current ad break
        MediaTailorAdBreak currentAdBreak = adBreaks.get(currentAdBreakIndex);

        // Update nextAdBreak state
        MediaTailorAdBreak previousNextAdBreak = nextAdBreak;
        if (playerTime < currentAdBreak.getScheduleTime()) {
            // Player is before current ad break - it's the next one
            nextAdBreak = currentAdBreak;
        } else if (currentAdBreakIndex < adBreaks.size() - 1) {
            // Player is in or past current break - next break is the following one
            nextAdBreak = adBreaks.get(currentAdBreakIndex + 1);
        } else {
            // No more ad breaks
            nextAdBreak = null;
        }

        // Log nextAdBreak changes
        if (previousNextAdBreak != nextAdBreak) {
            if (nextAdBreak != null) {
                Log.d(TAG, String.format("Next ad break: %s at %.1fs (player: %.1fs)",
                        nextAdBreak.getId(), nextAdBreak.getScheduleTime(), playerTime));
            } else {
                Log.d(TAG, "No more upcoming ad breaks");
            }
        }

        // Check if player is within current ad break time range
        if (!currentAdBreak.isInRange(playerTime)) {
            // Player is not in ad break
            if (playingAdBreak != null) {
                Log.d(TAG, String.format("Exited ad break: %s (player: %.1fs)",
                        currentAdBreak.getId(), playerTime));
                playingAdBreak = null;
            }
            return;
        }

        // Player is within ad break - find which ad
        List<MediaTailorLinearAd> ads = currentAdBreak.getAds();
        if (ads == null || ads.isEmpty()) {
            Log.w(TAG, "Ad break has no ads: " + currentAdBreak.getId());
            return;
        }

        // Advance currentAdIndex if player time has moved past current ad
        while (currentAdIndex < ads.size() - 1 &&
               playerTime >= ads.get(currentAdIndex).getEndTime()) {
            currentAdIndex++;
            Log.d(TAG, "Advanced to ad index: " + currentAdIndex);
        }

        // Get current ad
        MediaTailorLinearAd currentAd = ads.get(currentAdIndex);

        // Check if player is within current ad's time range
        if (currentAd.isInRange(playerTime)) {
            PlayingAdBreak newPlayingAdBreak = new PlayingAdBreak(currentAdBreak, currentAdIndex);

            // Check if playback state changed
            if (playingAdBreak == null || !playingAdBreak.equals(newPlayingAdBreak)) {
                Log.d(TAG, String.format("Now playing: Ad #%d (%s) in break %s (player: %.1fs, ad: %.1f-%.1fs)",
                        currentAdIndex + 1,
                        currentAd.getId(),
                        currentAdBreak.getId(),
                        playerTime,
                        currentAd.getScheduleTime(),
                        currentAd.getEndTime()));
                playingAdBreak = newPlayingAdBreak;
            }
        } else {
            // Player is in ad break time range but not in any specific ad
            // This can happen in gaps between ads
            if (playingAdBreak != null) {
                Log.d(TAG, String.format("In ad break gap (player: %.1fs)", playerTime));
                playingAdBreak = null;
            }
        }
    }

    /**
     * Get the next upcoming ad break.
     *
     * @return Next ad break or null if none
     */
    public MediaTailorAdBreak getNextAdBreak() {
        return nextAdBreak;
    }

    /**
     * Get the currently playing ad break and ad.
     *
     * @return Playing ad break or null if not in ad
     */
    public PlayingAdBreak getPlayingAdBreak() {
        return playingAdBreak;
    }

    /**
     * Check if tracker is currently running.
     *
     * @return true if tracking, false otherwise
     */
    public boolean isTracking() {
        return isTracking;
    }

    /**
     * Get current player time in seconds.
     * Useful for debugging.
     *
     * @return Current position in seconds
     */
    public double getCurrentPlayerTime() {
        return player.getCurrentPosition() / 1000.0;
    }

    /**
     * Get tracking state summary for debugging.
     *
     * @return State summary string
     */
    public String getStateDebugInfo() {
        double playerTime = getCurrentPlayerTime();
        return String.format("Tracker State: playerTime=%.1fs, adBreakIndex=%d/%d, adIndex=%d, " +
                        "nextAdBreak=%s, playingAdBreak=%s",
                playerTime,
                currentAdBreakIndex,
                adBreaks.size(),
                currentAdIndex,
                nextAdBreak != null ? nextAdBreak.getId() : "null",
                playingAdBreak != null ? playingAdBreak.toString() : "null");
    }
}
