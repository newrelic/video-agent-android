package com.newrelic.videoagent.core.exception;

/**
 * Generic error handler — player-agnostic.
 *
 * Extracts error code and message from a plain Java Exception.
 * Player-specific exception handling (PlaybackException, AdError, etc.)
 * is done in the respective tracker modules before calling sendError(int, String).
 */
public class ErrorExceptionHandler {

    private static final int DEFAULT_ERROR_CODE = -1;

    private final int errorCode;
    private final String errorMessage;

    public ErrorExceptionHandler(Exception error) {
        this.errorCode    = DEFAULT_ERROR_CODE;
        this.errorMessage = (error != null && error.getMessage() != null)
                ? error.getMessage()
                : "<Unknown error>";
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
