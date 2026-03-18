package com.newrelic.videoagent.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for NRDef that verify constant values.
 */
public class NRDefTest {

    @Test
    public void testVideoEventConstants() {
        assertEquals("VideoAction", NRDef.NR_VIDEO_EVENT);
        assertEquals("VideoAdAction", NRDef.NR_VIDEO_AD_EVENT);
        assertEquals("VideoErrorAction", NRDef.NR_VIDEO_ERROR_EVENT);
        assertEquals("VideoCustomAction", NRDef.NR_VIDEO_CUSTOM_EVENT);
    }

    @Test
    public void testSourceConstant() {
        assertEquals("ANDROID", NRDef.SRC);
    }

    @Test
    public void testTrackerReadyConstants() {
        assertEquals("TRACKER_READY", NRDef.TRACKER_READY);
        assertEquals("PLAYER_READY", NRDef.PLAYER_READY);
    }

    @Test
    public void testContentEventConstants() {
        assertEquals("CONTENT_REQUEST", NRDef.CONTENT_REQUEST);
        assertEquals("CONTENT_START", NRDef.CONTENT_START);
        assertEquals("CONTENT_PAUSE", NRDef.CONTENT_PAUSE);
        assertEquals("CONTENT_RESUME", NRDef.CONTENT_RESUME);
        assertEquals("CONTENT_END", NRDef.CONTENT_END);
    }

    @Test
    public void testContentSeekConstants() {
        assertEquals("CONTENT_SEEK_START", NRDef.CONTENT_SEEK_START);
        assertEquals("CONTENT_SEEK_END", NRDef.CONTENT_SEEK_END);
    }

    @Test
    public void testContentBufferConstants() {
        assertEquals("CONTENT_BUFFER_START", NRDef.CONTENT_BUFFER_START);
        assertEquals("CONTENT_BUFFER_END", NRDef.CONTENT_BUFFER_END);
    }

    @Test
    public void testContentMiscConstants() {
        assertEquals("CONTENT_HEARTBEAT", NRDef.CONTENT_HEARTBEAT);
        assertEquals("CONTENT_RENDITION_CHANGE", NRDef.CONTENT_RENDITION_CHANGE);
        assertEquals("CONTENT_ERROR", NRDef.CONTENT_ERROR);
    }

    @Test
    public void testAdEventConstants() {
        assertEquals("AD_REQUEST", NRDef.AD_REQUEST);
        assertEquals("AD_START", NRDef.AD_START);
        assertEquals("AD_PAUSE", NRDef.AD_PAUSE);
        assertEquals("AD_RESUME", NRDef.AD_RESUME);
        assertEquals("AD_END", NRDef.AD_END);
    }

    @Test
    public void testAdSeekConstants() {
        assertEquals("AD_SEEK_START", NRDef.AD_SEEK_START);
        assertEquals("AD_SEEK_END", NRDef.AD_SEEK_END);
    }

    @Test
    public void testAdBufferConstants() {
        assertEquals("AD_BUFFER_START", NRDef.AD_BUFFER_START);
        assertEquals("AD_BUFFER_END", NRDef.AD_BUFFER_END);
    }

    @Test
    public void testAdMiscConstants() {
        assertEquals("AD_HEARTBEAT", NRDef.AD_HEARTBEAT);
        assertEquals("AD_RENDITION_CHANGE", NRDef.AD_RENDITION_CHANGE);
        assertEquals("AD_ERROR", NRDef.AD_ERROR);
    }

    @Test
    public void testAdBreakConstants() {
        assertEquals("AD_BREAK_START", NRDef.AD_BREAK_START);
        assertEquals("AD_BREAK_END", NRDef.AD_BREAK_END);
    }

    @Test
    public void testAdInteractionConstants() {
        assertEquals("AD_QUARTILE", NRDef.AD_QUARTILE);
        assertEquals("AD_CLICK", NRDef.AD_CLICK);
    }

    @Test
    public void testVersionConstant() {
        assertNotNull(NRDef.NRVIDEO_CORE_VERSION);
        assertFalse(NRDef.NRVIDEO_CORE_VERSION.isEmpty());
    }

    @Test
    public void testAllContentConstantsAreUnique() {
        String[] contentConstants = {
            NRDef.CONTENT_REQUEST,
            NRDef.CONTENT_START,
            NRDef.CONTENT_PAUSE,
            NRDef.CONTENT_RESUME,
            NRDef.CONTENT_END,
            NRDef.CONTENT_SEEK_START,
            NRDef.CONTENT_SEEK_END,
            NRDef.CONTENT_BUFFER_START,
            NRDef.CONTENT_BUFFER_END,
            NRDef.CONTENT_HEARTBEAT,
            NRDef.CONTENT_RENDITION_CHANGE,
            NRDef.CONTENT_ERROR
        };

        for (int i = 0; i < contentConstants.length; i++) {
            for (int j = i + 1; j < contentConstants.length; j++) {
                assertNotEquals(contentConstants[i], contentConstants[j]);
            }
        }
    }

    @Test
    public void testAllAdConstantsAreUnique() {
        String[] adConstants = {
            NRDef.AD_REQUEST,
            NRDef.AD_START,
            NRDef.AD_PAUSE,
            NRDef.AD_RESUME,
            NRDef.AD_END,
            NRDef.AD_SEEK_START,
            NRDef.AD_SEEK_END,
            NRDef.AD_BUFFER_START,
            NRDef.AD_BUFFER_END,
            NRDef.AD_HEARTBEAT,
            NRDef.AD_RENDITION_CHANGE,
            NRDef.AD_ERROR,
            NRDef.AD_BREAK_START,
            NRDef.AD_BREAK_END,
            NRDef.AD_QUARTILE,
            NRDef.AD_CLICK
        };

        for (int i = 0; i < adConstants.length; i++) {
            for (int j = i + 1; j < adConstants.length; j++) {
                assertNotEquals(adConstants[i], adConstants[j]);
            }
        }
    }

    @Test
    public void testContentConstantsStartWithCONTENT() {
        assertTrue(NRDef.CONTENT_REQUEST.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_START.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_PAUSE.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_RESUME.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_END.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_SEEK_START.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_SEEK_END.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_BUFFER_START.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_BUFFER_END.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_HEARTBEAT.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_RENDITION_CHANGE.startsWith("CONTENT_"));
        assertTrue(NRDef.CONTENT_ERROR.startsWith("CONTENT_"));
    }

    @Test
    public void testAdConstantsStartWithAD() {
        assertTrue(NRDef.AD_REQUEST.startsWith("AD_"));
        assertTrue(NRDef.AD_START.startsWith("AD_"));
        assertTrue(NRDef.AD_PAUSE.startsWith("AD_"));
        assertTrue(NRDef.AD_RESUME.startsWith("AD_"));
        assertTrue(NRDef.AD_END.startsWith("AD_"));
        assertTrue(NRDef.AD_SEEK_START.startsWith("AD_"));
        assertTrue(NRDef.AD_SEEK_END.startsWith("AD_"));
        assertTrue(NRDef.AD_BUFFER_START.startsWith("AD_"));
        assertTrue(NRDef.AD_BUFFER_END.startsWith("AD_"));
        assertTrue(NRDef.AD_HEARTBEAT.startsWith("AD_"));
        assertTrue(NRDef.AD_RENDITION_CHANGE.startsWith("AD_"));
        assertTrue(NRDef.AD_ERROR.startsWith("AD_"));
        assertTrue(NRDef.AD_BREAK_START.startsWith("AD_"));
        assertTrue(NRDef.AD_BREAK_END.startsWith("AD_"));
        assertTrue(NRDef.AD_QUARTILE.startsWith("AD_"));
        assertTrue(NRDef.AD_CLICK.startsWith("AD_"));
    }

    @Test
    public void testConstantsAreNotNull() {
        assertNotNull(NRDef.NRVIDEO_CORE_VERSION);
        assertNotNull(NRDef.NR_VIDEO_EVENT);
        assertNotNull(NRDef.NR_VIDEO_AD_EVENT);
        assertNotNull(NRDef.NR_VIDEO_ERROR_EVENT);
        assertNotNull(NRDef.NR_VIDEO_CUSTOM_EVENT);
        assertNotNull(NRDef.SRC);
        assertNotNull(NRDef.TRACKER_READY);
        assertNotNull(NRDef.PLAYER_READY);
    }

    @Test
    public void testConstantsAreNotEmpty() {
        assertFalse(NRDef.NRVIDEO_CORE_VERSION.isEmpty());
        assertFalse(NRDef.NR_VIDEO_EVENT.isEmpty());
        assertFalse(NRDef.NR_VIDEO_AD_EVENT.isEmpty());
        assertFalse(NRDef.NR_VIDEO_ERROR_EVENT.isEmpty());
        assertFalse(NRDef.NR_VIDEO_CUSTOM_EVENT.isEmpty());
        assertFalse(NRDef.SRC.isEmpty());
        assertFalse(NRDef.TRACKER_READY.isEmpty());
        assertFalse(NRDef.PLAYER_READY.isEmpty());
    }
}
