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

        // THEOplayer wraps errors in THEOplayerException — extract code if available.
        // ErrorCode is a plain enum with no numeric values; ordinal() gives declaration
        // position (0-based). We include the enum name in the message so NR events
        // remain readable even if THEOplayer reorders the enum in a future SDK release.
        if (error instanceof THEOplayerException) {
            THEOplayerException theoError = (THEOplayerException) error;
            if (theoError.getCode() != null) {
                code    = theoError.getCode().ordinal();
                message = theoError.getCode().name() + ": " + theoError.getMessage();
            } else {
                message = theoError.getMessage();
            }
        }

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
