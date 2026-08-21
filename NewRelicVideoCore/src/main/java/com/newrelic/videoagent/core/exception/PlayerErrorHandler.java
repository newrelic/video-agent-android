package com.newrelic.videoagent.core.exception;

/**
 * Player-agnostic error contract.
 *
 * Each player module implements this interface with its own exception types:
 *   NRExoPlayerTracker  → ExoErrorHandler    (PlaybackException, AdError, ...)
 *   NRTHEOPlayerTracker → TheoErrorHandler   (THEOplayer ErrorEvent)
 *   NRBitmovinTracker   → BitmovinErrorHandler (future)
 *
 * The tracker calls sendError(handler.getErrorCode(), handler.getErrorMessage())
 * on NRVideoTracker — Core never needs to know which player SDK produced the error.
 */
public interface PlayerErrorHandler {
    int getErrorCode();
    String getErrorMessage();
}
