package com.newrelic.videoagent.core.exception;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;
import com.google.ads.interactivemedia.v3.api.AdError;
import androidx.media3.exoplayer.source.ads.AdsMediaSource.AdLoadException;

public class ErrorExceptionHandler {

    private int errorCode;
    private String errorMessage;


    public ErrorExceptionHandler(Exception error) {
        this.errorCode = -1; // Default error code
        this.errorMessage = error.getMessage();

        if (error instanceof InvalidResponseCodeException) {
            InvalidResponseCodeException dataSourceError = (InvalidResponseCodeException) error;
            this.errorCode = dataSourceError.responseCode;
            this.errorMessage = dataSourceError.responseMessage;
        } else if (error instanceof PlaybackException) {
            PlaybackException playbackError = (PlaybackException) error;
            this.errorCode = playbackError.errorCode;
            this.errorMessage = playbackError.getMessage();
        } else if (error instanceof AdError || error instanceof AdLoadException) {
            AdError adError = (error instanceof AdLoadException)
                    ? (error.getCause() instanceof AdError ? (AdError) error.getCause() : null)
                    : (AdError) error;

            if (adError != null) {
                this.errorCode = adError.getErrorCodeNumber();
                this.errorMessage = adError.getMessage();
            }
        }
    }


    public int getErrorCode() {
        return this.errorCode;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }
}

