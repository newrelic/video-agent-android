package com.newrelic.videoagent.exoplayer.exception;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;

import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;

public class ExoErrorHandlerTest {

    // ── Generic exception ─────────────────────────────────────────────────────

    @Test
    public void genericException_returnsDefaultCode() {
        ExoErrorHandler h = new ExoErrorHandler(new Exception("boom"));
        assertNull(h.getErrorCode());
        assertEquals("boom", h.getErrorMessage());
    }

    @Test
    public void nullException_returnsDefaultCodeAndUnknownMessage() {
        ExoErrorHandler h = new ExoErrorHandler(null);
        assertNull(h.getErrorCode());
        assertEquals("<Unknown error>", h.getErrorMessage());
    }

    @Test
    public void exceptionWithNullMessage_returnsUnknownMessage() {
        ExoErrorHandler h = new ExoErrorHandler(new IOException((String) null));
        assertNull(h.getErrorCode());
        assertEquals("<Unknown error>", h.getErrorMessage());
    }

    // ── InvalidResponseCodeException ─────────────────────────────────────────

    @Test
    public void invalidResponseCode404_returnsHttpCode() {
        InvalidResponseCodeException e = new InvalidResponseCodeException(
                404, "Not Found", null, Collections.emptyMap(), null, new byte[0]);
        ExoErrorHandler h = new ExoErrorHandler(e);
        assertEquals((Integer) 404, h.getErrorCode());
        assertEquals("Not Found", h.getErrorMessage());
    }

    @Test
    public void invalidResponseCode500_returnsHttpCode() {
        InvalidResponseCodeException e = new InvalidResponseCodeException(
                500, "Internal Server Error", null, Collections.emptyMap(), null, new byte[0]);
        ExoErrorHandler h = new ExoErrorHandler(e);
        assertEquals((Integer) 500, h.getErrorCode());
    }

    // ── PlaybackException ─────────────────────────────────────────────────────
    // Use protected (message, cause, errorCode, timestampMs) constructor to avoid
    // Clock.DEFAULT.elapsedRealtime() which throws under Robolectric.

    private static class TestPlaybackException extends PlaybackException {
        TestPlaybackException(String message, int code) {
            super(message, null, code, 0L);
        }
    }

    @Test
    public void playbackException_returnsExoErrorCode() {
        PlaybackException e = new TestPlaybackException(
                "playback failed", PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED);
        ExoErrorHandler h = new ExoErrorHandler(e);
        assertEquals((Integer) PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, h.getErrorCode());
        assertEquals("playback failed", h.getErrorMessage());
    }

    @Test
    public void playbackException_nullMessage_returnsUnknownMessage() {
        PlaybackException e = new TestPlaybackException(null, PlaybackException.ERROR_CODE_UNSPECIFIED);
        ExoErrorHandler h = new ExoErrorHandler(e);
        assertEquals((Integer) PlaybackException.ERROR_CODE_UNSPECIFIED, h.getErrorCode());
        assertEquals("<Unknown error>", h.getErrorMessage());
    }

    // ── IMA absent (default classpath in unit tests) ──────────────────────────

    @Test
    public void imaAbsent_nonImaException_returnsNullCode() {
        // IMA SDK is not on the test classpath — IMA_AVAILABLE should be false,
        // so any non-IMA exception falls through to null (no SDK-provided error code).
        ExoErrorHandler h = new ExoErrorHandler(new RuntimeException("network stall"));
        assertNull(h.getErrorCode());
    }
}
