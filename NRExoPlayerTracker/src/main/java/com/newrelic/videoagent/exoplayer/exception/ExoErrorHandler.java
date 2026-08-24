package com.newrelic.videoagent.exoplayer.exception;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;
import com.newrelic.videoagent.core.exception.PlayerErrorHandler;

import java.lang.reflect.Method;

/**
 * ExoPlayer + IMA error handler — implements {@link PlayerErrorHandler}.
 *
 * Extracts structured error codes and messages from ExoPlayer and IMA exception types.
 * Lives in NRExoPlayerTracker — Core has zero dependency on ExoPlayer or IMA.
 *
 * IMA classes (AdError, AdLoadException) are referenced via reflection so this class
 * loads safely on apps that do not include the IMA SDK.
 */
public class ExoErrorHandler implements PlayerErrorHandler {

    private static final int DEFAULT_ERROR_CODE = -9999;

    private static final String AD_LOAD_EXCEPTION =
            "androidx.media3.exoplayer.source.ads.AdsMediaSource$AdLoadException";
    private static final String AD_ERROR =
            "com.google.ads.interactivemedia.v3.api.AdError";

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
        } else {
            // IMA error handling via reflection — safe if IMA SDK is not on classpath
            int[] imaResult = extractIMAErrorCode(error);
            if (imaResult[0] != DEFAULT_ERROR_CODE) {
                code    = imaResult[0];
                message = error.getMessage();
            }
        }

        this.errorCode    = code;
        this.errorMessage = (message != null) ? message : "<Unknown error>";
    }

    /**
     * Extracts error code from IMA AdError or AdLoadException without importing
     * IMA classes directly. Returns {DEFAULT_ERROR_CODE} if IMA is not on classpath
     * or the error is not an IMA error.
     */
    private static int[] extractIMAErrorCode(Exception error) {
        try {
            Class<?> adLoadExClass = Class.forName(AD_LOAD_EXCEPTION);
            Class<?> adErrorClass  = Class.forName(AD_ERROR);
            Method getErrorCode    = adErrorClass.getMethod("getErrorCodeNumber");

            if (adLoadExClass.isInstance(error)) {
                Throwable cause = error.getCause();
                if (adErrorClass.isInstance(cause)) {
                    return new int[]{ (int) getErrorCode.invoke(cause) };
                }
            } else if (adErrorClass.isInstance(error)) {
                return new int[]{ (int) getErrorCode.invoke(error) };
            }
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // IMA SDK not on classpath — safe to skip
        } catch (Exception ignored) {
            // Reflection failure — fall back to default
        }
        return new int[]{ DEFAULT_ERROR_CODE };
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
