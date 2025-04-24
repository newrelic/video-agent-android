package com.newrelic.videoagent.core.exception;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;
import com.google.ads.interactivemedia.v3.api.AdError;

public class ErrorExceptionHandler {

    private int errorCode;
    private String errorMessage;


    public ErrorExceptionHandler(Exception error) {
        if (error instanceof InvalidResponseCodeException) {
            InvalidResponseCodeException dataSourceError = (InvalidResponseCodeException) error;
            this.errorCode = dataSourceError.responseCode;
            this.errorMessage = dataSourceError.responseMessage;
        } else if (error instanceof PlaybackException) {
            PlaybackException exoError = (PlaybackException) error;
            this.errorCode = exoError.errorCode;
            this.errorMessage = exoError.getMessage();
        } else if (error instanceof AdError) {
            AdError adError = (AdError) error;
            this.errorCode = adError.getErrorCodeNumber();
            this.errorMessage = adError.getMessage();
        } else {
            this.errorCode = -1; // Default error code
            this.errorMessage = error.getMessage();
        }
    }


    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

