package com.newrelic.videoagent.core.model;

public class NRTrackerState {
    public boolean isPlayerReady;
    public boolean isRequested;
    public boolean isStarted;
    public boolean isPlaying;
    public boolean isPaused;
    public boolean isSeeking;
    public boolean isBuffering;
    public boolean isAd;
    public boolean isAdBreak;

    public NRTrackerState() {
        reset();
    }

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
    }


    public boolean goPlayerReady() {
        if (!isPlayerReady) {
            isPlayerReady = true;
            return true;
        }
        else {
            return false;
        }
    }

    public boolean goRequest() {
        if (!isRequested) {
            isRequested = true;
            return true;
        }
        else {
            return false;
        }
    }

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

    public boolean goBufferEnd() {
        if (isRequested && isBuffering) {
            isBuffering = false;
            isPlaying = true;
            return true;
        } else {
            return false;
        }
    }

    public boolean goSeekStart() {
        if (isStarted && !isSeeking) {
            isSeeking = true;
            isPlaying = false;
            return true;
        } else {
            return false;
        }
    }

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

    public boolean goAdBreakStart() {
        if (!isAdBreak) {
            isAdBreak = true;
            return true;
        }
        else {
            return false;
        }
    }

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
