package com.newrelic.videoagent.exoplayer.exception;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;
import androidx.media3.exoplayer.source.ads.AdsMediaSource.AdLoadException;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.newrelic.videoagent.core.exception.PlayerErrorHandler;

/**
 * ExoPlayer + IMA error handler — implements {@link PlayerErrorHandler}.
 *
 * Extracts structured error codes and messages from ExoPlayer and IMA exception types.
 * Lives in NRExoPlayerTracker — Core has zero dependency on ExoPlayer or IMA.
 *
 * Pattern: every player module creates its own implementation of PlayerErrorHandler
 *   NRExoPlayerTracker  → ExoErrorHandler     (PlaybackException, AdError, ...)
 *   NRTHEOPlayerTracker → TheoErrorHandler    (see NRTrackerTHEOPlayer)
 *   NRBitmovinTracker   → BitmovinErrorHandler (future)
 */
public class ExoErrorHandler implements PlayerErrorHandler {

    private static final int DEFAULT_ERROR_CODE = -1;

    private final int errorCode;
    private final String errorMessage;

    public ExoErrorHandler(Exception error) {
        int code = DEFAULT_ERROR_CODE;
        String message = (error != null) ? error.getMessage() : "<Unknown error>";

        if (error instanceof InvalidResponseCodeException) {
            InvalidResponseCodeException e = (InvalidResponseCodeException) error;
            code    = e.responseCode;
            message = e.responseMessage;
        } else if (error instanceof PlaybackException) {
            PlaybackException e = (PlaybackException) error;
            code    = e.errorCode;
            message = e.getMessage();
        } else if (error instanceof AdLoadException) {
            if (error.getCause() instanceof AdError) {
                AdError adError = (AdError) error.getCause();
                code    = adError.getErrorCodeNumber();
                message = adError.getMessage();
            }
        } else if (error instanceof AdError) {
            AdError adError = (AdError) error;
            code    = adError.getErrorCodeNumber();
            message = adError.getMessage();
        }

        this.errorCode    = code;
        this.errorMessage = (message != null) ? message : "<Unknown error>";
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
