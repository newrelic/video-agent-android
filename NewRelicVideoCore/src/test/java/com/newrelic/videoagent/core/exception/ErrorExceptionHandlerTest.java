package com.newrelic.videoagent.core.exception;

import androidx.media3.common.PlaybackException;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for ErrorExceptionHandler that exercise actual production code.
 */
public class ErrorExceptionHandlerTest {

    @Test
    public void testHandleGenericException() {
        Exception genericException = new Exception("Generic error message");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(genericException);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals("Generic error message", handler.getErrorMessage());
    }

    @Test
    public void testHandleNullMessageException() {
        Exception exception = new Exception((String) null);

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        assertEquals(-9999, handler.getErrorCode());
        assertNull(handler.getErrorMessage());
    }

    @Test
    public void testHandleRuntimeException() {
        RuntimeException runtimeException = new RuntimeException("Runtime error");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(runtimeException);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals("Runtime error", handler.getErrorMessage());
    }

    @Test
    public void testHandleIOException() {
        Exception ioException = new java.io.IOException("IO error occurred");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(ioException);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals("IO error occurred", handler.getErrorMessage());
    }

    @Test
    public void testHandleExceptionWithEmptyMessage() {
        Exception exception = new Exception("");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals("", handler.getErrorMessage());
    }

    @Test
    public void testHandleExceptionWithLongMessage() {
        String longMessage = "This is a very long error message that contains a lot of details " +
                "about what went wrong during the video playback process. It includes " +
                "technical information that might be useful for debugging purposes.";
        Exception exception = new Exception(longMessage);

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals(longMessage, handler.getErrorMessage());
    }

    @Test
    public void testHandleExceptionWithSpecialCharacters() {
        String messageWithSpecialChars = "Error: \"Cannot load video\" - check network & permissions!";
        Exception exception = new Exception(messageWithSpecialChars);

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals(messageWithSpecialChars, handler.getErrorMessage());
    }

    @Test
    public void testGetErrorCodeReturnsConsistentValue() {
        Exception exception = new Exception("Test error");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        int errorCode1 = handler.getErrorCode();
        int errorCode2 = handler.getErrorCode();

        assertEquals(errorCode1, errorCode2);
        assertEquals(-9999, errorCode1);
    }

    @Test
    public void testGetErrorMessageReturnsConsistentValue() {
        Exception exception = new Exception("Test message");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        String message1 = handler.getErrorMessage();
        String message2 = handler.getErrorMessage();

        assertEquals(message1, message2);
        assertEquals("Test message", message1);
    }

    @Test
    public void testMultipleHandlersWithDifferentExceptions() {
        Exception exception1 = new Exception("Error 1");
        Exception exception2 = new Exception("Error 2");

        ErrorExceptionHandler handler1 = new ErrorExceptionHandler(exception1);
        ErrorExceptionHandler handler2 = new ErrorExceptionHandler(exception2);

        assertEquals("Error 1", handler1.getErrorMessage());
        assertEquals("Error 2", handler2.getErrorMessage());
        assertEquals(-9999, handler1.getErrorCode());
        assertEquals(-9999, handler2.getErrorCode());
    }

    @Test
    public void testHandleIllegalArgumentException() {
        IllegalArgumentException illegalArgException = new IllegalArgumentException("Invalid argument provided");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(illegalArgException);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals("Invalid argument provided", handler.getErrorMessage());
    }

    @Test
    public void testHandleNullPointerException() {
        NullPointerException npe = new NullPointerException("Null pointer encountered");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(npe);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals("Null pointer encountered", handler.getErrorMessage());
    }

    @Test
    public void testHandleSecurityException() {
        SecurityException secException = new SecurityException("Security violation detected");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(secException);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals("Security violation detected", handler.getErrorMessage());
    }

    @Test
    public void testHandleUnsupportedOperationException() {
        UnsupportedOperationException unsupportedException = new UnsupportedOperationException("Operation not supported");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(unsupportedException);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals("Operation not supported", handler.getErrorMessage());
    }

    @Test
    public void testDefaultErrorCodeConstant() {
        Exception exception = new Exception("Test");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        assertEquals(-9999, handler.getErrorCode());
    }

    @Test
    public void testHandleExceptionWithNumericMessage() {
        Exception exception = new Exception("12345");

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals("12345", handler.getErrorMessage());
    }

    @Test
    public void testHandleExceptionWithUnicodeCharacters() {
        String unicodeMessage = "Error: 视频加载失败 🎥";
        Exception exception = new Exception(unicodeMessage);

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        assertEquals(-9999, handler.getErrorCode());
        assertEquals(unicodeMessage, handler.getErrorMessage());
    }

    @Test
    public void testHandleNestedExceptionMessage() {
        Exception cause = new Exception("Root cause error");
        Exception exception = new Exception("Wrapper error: " + cause.getMessage(), cause);

        ErrorExceptionHandler handler = new ErrorExceptionHandler(exception);

        assertEquals(-9999, handler.getErrorCode());
        assertTrue(handler.getErrorMessage().contains("Wrapper error"));
        assertTrue(handler.getErrorMessage().contains("Root cause error"));
    }
}
