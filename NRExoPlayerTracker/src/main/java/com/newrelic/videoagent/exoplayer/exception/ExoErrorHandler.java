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

    // Resolved once at class load — zero cost on every call when IMA is absent.
    private static final Class<?> AD_LOAD_EX_CLASS;
    private static final Class<?> AD_ERROR_CLASS;
    private static final Method   GET_ERROR_CODE;
    private static final boolean  IMA_AVAILABLE;

    static {
        Class<?> adLoadEx = null;
        Class<?> adError  = null;
        Method   method   = null;
        boolean  ok       = false;
        try {
            adLoadEx = Class.forName(
                    "androidx.media3.exoplayer.source.ads.AdsMediaSource$AdLoadException");
            adError  = Class.forName("com.google.ads.interactivemedia.v3.api.AdError");
            method   = adError.getMethod("getErrorCodeNumber");
            ok       = true;
        } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException ignored) {
            // IMA SDK not on classpath — all calls will fast-path to DEFAULT_ERROR_CODE
        }
        AD_LOAD_EX_CLASS = adLoadEx;
        AD_ERROR_CLASS   = adError;
        GET_ERROR_CODE   = method;
        IMA_AVAILABLE    = ok;
    }

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
            int imaCode = extractIMAErrorCode(error);
            if (imaCode != DEFAULT_ERROR_CODE) {
                code    = imaCode;
                message = error.getMessage();
            }
        }

        this.errorCode    = code;
        this.errorMessage = (message != null) ? message : "<Unknown error>";
    }

    /**
     * Extracts error code from IMA AdError or AdLoadException using pre-resolved
     * static fields. Returns DEFAULT_ERROR_CODE if IMA is absent or the error is
     * not an IMA type.
     */
    private static int extractIMAErrorCode(Exception error) {
        if (!IMA_AVAILABLE) return DEFAULT_ERROR_CODE;
        try {
            if (AD_LOAD_EX_CLASS.isInstance(error)) {
                Throwable cause = error.getCause();
                if (AD_ERROR_CLASS.isInstance(cause)) {
                    return (int) GET_ERROR_CODE.invoke(cause);
                }
            } else if (AD_ERROR_CLASS.isInstance(error)) {
                return (int) GET_ERROR_CODE.invoke(error);
            }
        } catch (Exception ignored) {
            // Reflection failure — fall back to default
        }
        return DEFAULT_ERROR_CODE;
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
