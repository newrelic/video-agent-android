package com.newrelic.videoagent.core.model;

/**
 * Holds the state of a tracker.
 */
public class NRTrackerState {
    /**
     * Player Ready happened.
     */
    public boolean isPlayerReady;
    /**
     * Request happened.
     */
    public boolean isRequested;
    /**
     * Start happened.
     */
    public boolean isStarted;
    /**
     * Video is playing.
     */
    public boolean isPlaying;
    /**
     * Video is paused.
     */
    public boolean isPaused;
    /**
     * Video is seeking.
     */
    public boolean isSeeking;
    /**
     * Video is buffering.
     */
    public boolean isBuffering;
    /**
     * Is playing an ad.
     */
    public boolean isAd;
    /**
     * In an ad break.
     */
    public boolean isAdBreak;

    public NRChrono chrono;
    public Long accumulatedVideoWatchTime;

    /**
     * Create a new tracker state instance.
     */
    public NRTrackerState() {
        reset();
    }

    /**
     * Reset all states.
     */
    public void reset() {
        isPlayerReady = false;
        isRequested = false;
        isStarted = false;
        isPlaying = false;
        isPaused = false;
        isSeeking = false;
        isBuffering = false;
        isAd = false;
        isAdBreak = false;
        chrono = new NRChrono();
        accumulatedVideoWatchTime = 0L;
    }

    /**
     * Check to send event PLAYER_READY.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goPlayerReady() {
        if (!isPlayerReady) {
            isPlayerReady = true;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Check to send event REQUEST.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goRequest() {
        if (!isRequested) {
            isRequested = true;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Check to send event START.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goStart() {
        if (isRequested && !isStarted) {
            isStarted = true;
            isPlaying = true;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Check to send event END.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goEnd() {
        if (isRequested) {
            isRequested = false;
            isStarted = false;
            isPlaying = false;
            isPaused = false;
            isSeeking = false;
            isBuffering = false;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Check to send event PAUSE.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goPause() {
        if (isStarted && !isPaused) {
            isPaused = true;
            isPlaying = false;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Check to send event RESUME.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goResume() {
        if (isStarted && isPaused) {
            isPaused = false;
            isPlaying = true;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Check to send event BUFFER_START.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goBufferStart() {
        if (isRequested && !isBuffering) {
            isBuffering = true;
            isPlaying = false;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Check to send event BUFFER_END.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goBufferEnd() {
        if (isRequested && isBuffering) {
            isBuffering = false;
            isPlaying = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check to send event SEEK_START.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goSeekStart() {
        if (isStarted && !isSeeking) {
            isSeeking = true;
            isPlaying = false;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check to send event SEEK_END.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goSeekEnd() {
        if (isStarted && isSeeking) {
            isSeeking = false;
            isPlaying = true;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Check to send event AD_BREAK_START.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goAdBreakStart() {
        if (!isAdBreak) {
            isAdBreak = true;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Check to send event AD_BREAK_END.
     *
     * @return True if state changed. False otherwise.
     */
    public boolean goAdBreakEnd() {
        if (isAdBreak) {
            isRequested = false;
            isAdBreak = false;
            return true;
        } else {
            return false;
        }
    }
}