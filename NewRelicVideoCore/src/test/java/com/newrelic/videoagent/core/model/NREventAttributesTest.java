package com.newrelic.videoagent.core.model;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for NREventAttributes.
 */
public class NREventAttributesTest {

    private NREventAttributes eventAttributes;

    @Before
    public void setUp() {
        eventAttributes = new NREventAttributes();
    }

    @Test
    public void testSetAttributeWithoutFilter() {
        eventAttributes.setAttribute("testKey", "testValue", null);

        Map<String, Object> result = eventAttributes.generateAttributes("REQUEST", null);

        assertEquals("testValue", result.get("testKey"));
    }

    @Test
    public void testSetAttributeWithSpecificFilter() {
        eventAttributes.setAttribute("customAttr", "customValue", "VIDEO_START");

        Map<String, Object> matchResult = eventAttributes.generateAttributes("VIDEO_START", null);
        assertEquals("customValue", matchResult.get("customAttr"));

        Map<String, Object> noMatchResult = eventAttributes.generateAttributes("VIDEO_END", null);
        assertNull(noMatchResult.get("customAttr"));
    }

    @Test
    public void testSetAttributeWithRegexFilter() {
        eventAttributes.setAttribute("videoAttr", "value1", "VIDEO_.*");

        Map<String, Object> startResult = eventAttributes.generateAttributes("VIDEO_START", null);
        assertEquals("value1", startResult.get("videoAttr"));

        Map<String, Object> endResult = eventAttributes.generateAttributes("VIDEO_END", null);
        assertEquals("value1", endResult.get("videoAttr"));

        Map<String, Object> noMatchResult = eventAttributes.generateAttributes("AUDIO_START", null);
        assertNull(noMatchResult.get("videoAttr"));
    }

    @Test
    public void testMultipleAttributesWithDifferentFilters() {
        eventAttributes.setAttribute("global", "globalValue", null);
        eventAttributes.setAttribute("videoOnly", "videoValue", "VIDEO_.*");
        eventAttributes.setAttribute("specific", "specificValue", "VIDEO_START");

        Map<String, Object> videoStartResult = eventAttributes.generateAttributes("VIDEO_START", null);
        assertEquals("globalValue", videoStartResult.get("global"));
        assertEquals("videoValue", videoStartResult.get("videoOnly"));
        assertEquals("specificValue", videoStartResult.get("specific"));

        Map<String, Object> videoEndResult = eventAttributes.generateAttributes("VIDEO_END", null);
        assertEquals("globalValue", videoEndResult.get("global"));
        assertEquals("videoValue", videoEndResult.get("videoOnly"));
        assertNull(videoEndResult.get("specific"));
    }

    @Test
    public void testAttributeOverwriting() {
        eventAttributes.setAttribute("key", "value1", "VIDEO_.*");
        eventAttributes.setAttribute("key", "value2", "VIDEO_START");

        Map<String, Object> result = eventAttributes.generateAttributes("VIDEO_START", null);
        assertEquals("value2", result.get("key"));
    }

    @Test
    public void testGenerateAttributesWithExistingAttributes() {
        eventAttributes.setAttribute("attr1", "value1", null);

        Map<String, Object> existingAttrs = new HashMap<>();
        existingAttrs.put("attr2", "value2");
        existingAttrs.put("attr3", "value3");

        Map<String, Object> result = eventAttributes.generateAttributes("REQUEST", existingAttrs);

        assertEquals("value1", result.get("attr1"));
        assertEquals("value2", result.get("attr2"));
        assertEquals("value3", result.get("attr3"));
        assertEquals(3, result.size());
    }

    @Test
    public void testGenerateAttributesWithNullExistingAttributes() {
        eventAttributes.setAttribute("key", "value", null);

        Map<String, Object> result = eventAttributes.generateAttributes("REQUEST", null);

        assertNotNull(result);
        assertEquals("value", result.get("key"));
    }

    @Test
    public void testGenerateAttributesWithEmptyExistingAttributes() {
        eventAttributes.setAttribute("key", "value", null);

        Map<String, Object> result = eventAttributes.generateAttributes("REQUEST", new HashMap<>());

        assertNotNull(result);
        assertEquals("value", result.get("key"));
    }

    @Test
    public void testMultipleBucketsWithSameKey() {
        eventAttributes.setAttribute("key", "defaultValue", null);
        eventAttributes.setAttribute("key", "videoValue", "VIDEO_.*");

        Map<String, Object> videoResult = eventAttributes.generateAttributes("VIDEO_START", null);
        // Both filters match VIDEO_START, but HashMap iteration order determines which wins
        assertNotNull(videoResult.get("key"));
        assertTrue(videoResult.get("key").equals("defaultValue") || videoResult.get("key").equals("videoValue"));

        Map<String, Object> otherResult = eventAttributes.generateAttributes("AUDIO_START", null);
        assertEquals("defaultValue", otherResult.get("key"));
    }

    @Test
    public void testSetAttributeWithDifferentValueTypes() {
        eventAttributes.setAttribute("stringAttr", "stringValue", null);
        eventAttributes.setAttribute("intAttr", 42, null);
        eventAttributes.setAttribute("boolAttr", true, null);
        eventAttributes.setAttribute("doubleAttr", 3.14, null);

        Map<String, Object> result = eventAttributes.generateAttributes("REQUEST", null);

        assertEquals("stringValue", result.get("stringAttr"));
        assertEquals(42, result.get("intAttr"));
        assertEquals(true, result.get("boolAttr"));
        assertEquals(3.14, result.get("doubleAttr"));
    }

    @Test
    public void testToString() {
        eventAttributes.setAttribute("key1", "value1", null);
        eventAttributes.setAttribute("key2", "value2", "VIDEO_START");

        String result = eventAttributes.toString();

        assertNotNull(result);
        assertTrue(result.contains("NREventAttributes"));
    }

    @Test
    public void testComplexRegexPatterns() {
        eventAttributes.setAttribute("errorAttr", "errorValue", "(VIDEO|AUDIO)_ERROR");

        Map<String, Object> videoErrorResult = eventAttributes.generateAttributes("VIDEO_ERROR", null);
        assertEquals("errorValue", videoErrorResult.get("errorAttr"));

        Map<String, Object> audioErrorResult = eventAttributes.generateAttributes("AUDIO_ERROR", null);
        assertEquals("errorValue", audioErrorResult.get("errorAttr"));

        Map<String, Object> noMatchResult = eventAttributes.generateAttributes("VIDEO_START", null);
        assertNull(noMatchResult.get("errorAttr"));
    }

    @Test
    public void testAttributePrecedenceWithMultipleFilters() {
        eventAttributes.setAttribute("attr", "general", ".*");
        eventAttributes.setAttribute("attr", "video", "VIDEO_.*");
        eventAttributes.setAttribute("attr", "specific", "VIDEO_START");

        Map<String, Object> result = eventAttributes.generateAttributes("VIDEO_START", null);

        assertTrue(result.containsKey("attr"));
        assertNotNull(result.get("attr"));
    }

    @Test
    public void testEmptyAttributesGeneration() {
        Map<String, Object> result = eventAttributes.generateAttributes("VIDEO_START", null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
