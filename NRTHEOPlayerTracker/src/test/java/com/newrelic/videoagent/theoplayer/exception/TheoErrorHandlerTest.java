package com.newrelic.videoagent.theoplayer.exception;

import com.theoplayer.android.api.error.ErrorCode;
import com.theoplayer.android.api.error.THEOplayerException;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TheoErrorHandlerTest {

    

    // ── null / plain Exception ────────────────────────────────────────────────

    @Test
    public void nullException_returnsDefaultCodeAndUnknownMessage() {
        TheoErrorHandler h = new TheoErrorHandler(null);
        assertNull(h.getErrorCode());
        assertEquals("<Unknown error>", h.getErrorMessage());
    }

    @Test
    public void genericException_returnsDefaultCodeAndOriginalMessage() {
        TheoErrorHandler h = new TheoErrorHandler(new Exception("boom"));
        assertNull(h.getErrorCode());
        assertEquals("boom", h.getErrorMessage());
    }

    @Test
    public void exceptionWithNullMessage_returnsUnknownMessage() {
        // IOException(String) with null maps to Throwable.getMessage() == null
        TheoErrorHandler h = new TheoErrorHandler(new Exception((String) null));
        assertNull(h.getErrorCode());
        assertEquals("<Unknown error>", h.getErrorMessage());
    }

    // ── THEOplayerException — real SDK instances ──────────────────────────────

    @Test
    public void theoException_networkError_usesStableIdAndPrefixesCodeName() {
        TheoErrorHandler h = new TheoErrorHandler(
                new THEOplayerException(ErrorCode.NETWORK_ERROR, "connection failed"));

        assertEquals((Integer) ErrorCode.NETWORK_ERROR.getId(), h.getErrorCode());
        assertEquals("NETWORK_ERROR: connection failed", h.getErrorMessage());
    }

    @Test
    public void theoException_manifestLoadError_returnsStableIdAndMessage() {
        TheoErrorHandler h = new TheoErrorHandler(
                new THEOplayerException(ErrorCode.MANIFEST_LOAD_ERROR, "404 on manifest"));

        assertEquals((Integer) ErrorCode.MANIFEST_LOAD_ERROR.getId(), h.getErrorCode());
        assertEquals("MANIFEST_LOAD_ERROR: 404 on manifest", h.getErrorMessage());
    }

    @Test
    public void theoException_nullCode_fallsBackToDefaultCodeAndBareMessage() {
        // The real SDK constructor rejects null ErrorCode, so mock to exercise the
        // defensive null-check in TheoErrorHandler (guards against future subclasses).
        THEOplayerException ex = mock(THEOplayerException.class);
        when(ex.getCode()).thenReturn(null);
        when(ex.getMessage()).thenReturn("mystery error");

        TheoErrorHandler h = new TheoErrorHandler(ex);

        assertNull(h.getErrorCode());
        assertEquals("mystery error", h.getErrorMessage());
    }
}
