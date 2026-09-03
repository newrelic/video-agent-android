package com.newrelic.videoagent.theoplayer.exception;

import com.newrelic.videoagent.core.exception.PlayerErrorHandler;
import com.theoplayer.android.api.error.THEOplayerException;

/**
 * THEOplayer error handler — implements {@link PlayerErrorHandler}.
 *
 * Extracts error information from THEOplayer exception types.
 * Lives in NRTHEOPlayerTracker — Core has zero dependency on THEOplayer SDK.
 */
public class TheoErrorHandler implements PlayerErrorHandler {

    private static final int DEFAULT_ERROR_CODE = -9999;

    private final int errorCode;
    private final String errorMessage;

    public TheoErrorHandler(Exception error) {
        int code      = DEFAULT_ERROR_CODE;
        String message = (error != null) ? error.getMessage() : "<Unknown error>";

        // THEOplayer wraps errors in THEOplayerException — extract code if available
        if (error instanceof THEOplayerException) {
            THEOplayerException theoError = (THEOplayerException) error;
            code    = theoError.getCode() != null ? theoError.getCode().ordinal() : DEFAULT_ERROR_CODE;
            message = theoError.getMessage();
        }

        this.errorCode    = code;
        this.errorMessage = (message != null) ? message : "<Unknown error>";
    }

    /** Construct directly from a message string (e.g. from ErrorEvent). */
    public TheoErrorHandler(int code, String message) {
        this.errorCode    = code;
        this.errorMessage = (message != null) ? message : "<Unknown error>";
    }

    @Override
    public int getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}
