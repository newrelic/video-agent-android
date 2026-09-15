package com.newrelic.videoagent.core.exception;

/**
 * Generic error handler — player-agnostic.
 *
 * Extracts error message from a plain Java Exception.
 * Returns null for errorCode because a generic Exception carries no SDK-provided
 * error code — callers should omit errorCode from events when it is null.
 *
 * Player-specific exception handling (PlaybackException, AdError, etc.)
 * is done in the respective tracker modules before calling sendError(int, String).
 */
public class ErrorExceptionHandler {

    private final String errorMessage;

    public ErrorExceptionHandler(Exception error) {
        this.errorMessage = (error != null && error.getMessage() != null)
                ? error.getMessage()
                : "<Unknown error>";
    }

    /** Returns null — generic exceptions carry no SDK-provided error code. */
    public Integer getErrorCode() {
        return null;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
