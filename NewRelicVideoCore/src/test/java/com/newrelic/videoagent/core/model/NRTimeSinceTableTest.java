package com.newrelic.videoagent.core.model;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for NRTimeSinceTable.
 */
public class NRTimeSinceTableTest {

    private NRTimeSinceTable timeSinceTable;

    @Before
    public void setUp() {
        timeSinceTable = new NRTimeSinceTable();
    }

    @Test
    public void testAddEntryWith() {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceVideoStart", "VIDEO_.*");

        Map<String, Object> attributes = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_START", attributes);

        assertNotNull(attributes);
    }

    @Test
    public void testAddEntryWithModel() {
        NRTimeSince ts = new NRTimeSince("VIDEO_START", "timeSinceVideoStart", "VIDEO_.*");
        timeSinceTable.addEntry(ts);

        Map<String, Object> attributes = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_START", attributes);

        assertNotNull(attributes);
    }

    @Test
    public void testApplyAttributesWithMatchingAction() throws InterruptedException {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceVideoStart", "VIDEO_END");

        Map<String, Object> startAttributes = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_START", startAttributes);

        Thread.sleep(10);

        Map<String, Object> endAttributes = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_END", endAttributes);

        assertTrue(endAttributes.containsKey("timeSinceVideoStart"));
        assertNotNull(endAttributes.get("timeSinceVideoStart"));
    }

    @Test
    public void testApplyAttributesWithRegexFilter() throws InterruptedException {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceVideoStart", "VIDEO_(END|PAUSE)");

        Map<String, Object> startAttributes = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_START", startAttributes);

        Thread.sleep(10);

        Map<String, Object> endAttributes = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_END", endAttributes);

        assertTrue(endAttributes.containsKey("timeSinceVideoStart"));

        Map<String, Object> pauseAttributes = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_PAUSE", pauseAttributes);

        assertTrue(pauseAttributes.containsKey("timeSinceVideoStart"));
    }

    @Test
    public void testApplyAttributesWithNoMatch() {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceVideoStart", "VIDEO_END");

        Map<String, Object> attributes = new HashMap<>();
        timeSinceTable.applyAttributes("AUDIO_START", attributes);

        assertFalse(attributes.containsKey("timeSinceVideoStart"));
    }

    @Test
    public void testMultipleEntriesInTable() throws InterruptedException {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceVideoStart", "VIDEO_END");
        timeSinceTable.addEntryWith("CONTENT_START", "timeSinceContentStart", "CONTENT_END");

        Map<String, Object> videoStartAttrs = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_START", videoStartAttrs);

        Map<String, Object> contentStartAttrs = new HashMap<>();
        timeSinceTable.applyAttributes("CONTENT_START", contentStartAttrs);

        Thread.sleep(10);

        Map<String, Object> videoEndAttrs = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_END", videoEndAttrs);

        assertTrue(videoEndAttrs.containsKey("timeSinceVideoStart"));
        assertFalse(videoEndAttrs.containsKey("timeSinceContentStart"));

        Map<String, Object> contentEndAttrs = new HashMap<>();
        timeSinceTable.applyAttributes("CONTENT_END", contentEndAttrs);

        assertTrue(contentEndAttrs.containsKey("timeSinceContentStart"));
    }

    @Test
    public void testTimeSinceValueIsNumeric() throws InterruptedException {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceVideoStart", "VIDEO_END");

        timeSinceTable.applyAttributes("VIDEO_START", new HashMap<>());

        Thread.sleep(10);

        Map<String, Object> endAttributes = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_END", endAttributes);

        Object timeSinceValue = endAttributes.get("timeSinceVideoStart");
        assertNotNull(timeSinceValue);
        assertTrue(timeSinceValue instanceof Number);
        assertTrue(((Number) timeSinceValue).longValue() >= 0);
    }

    @Test
    public void testApplyAttributesUpdatesExistingAttributes() throws InterruptedException {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceVideoStart", "VIDEO_END");

        timeSinceTable.applyAttributes("VIDEO_START", new HashMap<>());

        Thread.sleep(10);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("existingKey", "existingValue");
        timeSinceTable.applyAttributes("VIDEO_END", attributes);

        assertEquals("existingValue", attributes.get("existingKey"));
        assertTrue(attributes.containsKey("timeSinceVideoStart"));
        assertEquals(2, attributes.size());
    }

    @Test
    public void testApplyAttributesWithEmptyTable() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("key", "value");

        timeSinceTable.applyAttributes("VIDEO_START", attributes);

        assertEquals(1, attributes.size());
        assertEquals("value", attributes.get("key"));
    }

    @Test
    public void testMultipleApplyAttributesCallsForSameAction() throws InterruptedException {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceVideoStart", "VIDEO_END");

        timeSinceTable.applyAttributes("VIDEO_START", new HashMap<>());

        Thread.sleep(10);

        Map<String, Object> firstCall = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_END", firstCall);
        long firstValue = ((Number) firstCall.get("timeSinceVideoStart")).longValue();

        Thread.sleep(10);

        Map<String, Object> secondCall = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_END", secondCall);
        long secondValue = ((Number) secondCall.get("timeSinceVideoStart")).longValue();

        assertTrue(secondValue >= firstValue);
    }

    @Test
    public void testActionTriggersTimestampUpdate() throws InterruptedException {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceStart", "VIDEO_PAUSE");

        timeSinceTable.applyAttributes("VIDEO_START", new HashMap<>());
        Thread.sleep(10);

        Map<String, Object> firstPause = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_PAUSE", firstPause);
        long firstPauseTime = ((Number) firstPause.get("timeSinceStart")).longValue();

        timeSinceTable.applyAttributes("VIDEO_START", new HashMap<>());
        Thread.sleep(10);

        Map<String, Object> secondPause = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_PAUSE", secondPause);
        long secondPauseTime = ((Number) secondPause.get("timeSinceStart")).longValue();

        assertTrue(secondPauseTime < firstPauseTime + 20);
    }

    @Test
    public void testWildcardFilterMatchesMultipleActions() throws InterruptedException {
        timeSinceTable.addEntryWith("VIDEO_START", "timeSinceVideoStart", ".*");

        timeSinceTable.applyAttributes("VIDEO_START", new HashMap<>());
        Thread.sleep(10);

        Map<String, Object> endAttrs = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_END", endAttrs);
        assertTrue(endAttrs.containsKey("timeSinceVideoStart"));

        Map<String, Object> pauseAttrs = new HashMap<>();
        timeSinceTable.applyAttributes("VIDEO_PAUSE", pauseAttrs);
        assertTrue(pauseAttrs.containsKey("timeSinceVideoStart"));

        Map<String, Object> audioAttrs = new HashMap<>();
        timeSinceTable.applyAttributes("AUDIO_START", audioAttrs);
        assertTrue(audioAttrs.containsKey("timeSinceVideoStart"));
    }
}
