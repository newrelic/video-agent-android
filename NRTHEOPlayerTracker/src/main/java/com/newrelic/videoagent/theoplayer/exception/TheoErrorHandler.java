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

    private final Integer errorCode;   // null when no SDK-provided code is available
    private final String errorMessage;

    public TheoErrorHandler(Exception error) {
        Integer code   = null;
        String message = (error != null) ? error.getMessage() : "<Unknown error>";

        // THEOplayer wraps errors in THEOplayerException — extract code if available.
        // ErrorCode.getId() returns a stable SDK-assigned integer (not ordinal/position-dependent),
        // safe across upgrades that add or reorder enum constants. THEOplayer's paired
        // fromId(int) reverse-lookup confirms getId() is the intended serialisable identifier.
        if (error instanceof THEOplayerException) {
            THEOplayerException theoError = (THEOplayerException) error;
            if (theoError.getCode() != null) {
                code    = theoError.getCode().getId();
                message = theoError.getCode().name() + ": " + theoError.getMessage();
            } else {
                message = theoError.getMessage();
            }
        }

        this.errorCode    = code;
        this.errorMessage = (message != null) ? message : "<Unknown error>";
    }

    @Override
    public Integer getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}
