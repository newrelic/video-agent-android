package com.newrelic.videoagent.core.util;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for JsonStreamUtil.
 */
public class JsonStreamUtilTest {

    @Test
    public void testStreamJsonToOutputStreamWithSimpleList() throws IOException {
        List<Object> list = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("key", "value");
        list.add(item);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonStreamUtil.streamJsonToOutputStream(list, outputStream);

        String result = outputStream.toString();
        assertNotNull(result);
        assertTrue(result.contains("key"));
        assertTrue(result.contains("value"));
    }

    @Test
    public void testStreamJsonToOutputStreamWithEmptyList() throws IOException {
        List<Object> list = new ArrayList<>();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonStreamUtil.streamJsonToOutputStream(list, outputStream);

        String result = outputStream.toString();
        assertNotNull(result);
        assertEquals("[]", result);
    }

    @Test
    public void testStreamJsonToOutputStreamWithMultipleItems() throws IOException {
        List<Object> list = new ArrayList<>();

        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1);
        item1.put("name", "Item1");
        list.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 2);
        item2.put("name", "Item2");
        list.add(item2);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonStreamUtil.streamJsonToOutputStream(list, outputStream);

        String result = outputStream.toString();
        assertNotNull(result);
        assertTrue(result.contains("Item1"));
        assertTrue(result.contains("Item2"));
        assertTrue(result.startsWith("["));
        assertTrue(result.endsWith("]"));
    }

    @Test
    public void testStreamMapToOutputStreamWithSimpleMap() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", 42);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonStreamUtil.streamMapToOutputStream(map, outputStream);

        String result = outputStream.toString();
        assertNotNull(result);
        assertTrue(result.contains("key1"));
        assertTrue(result.contains("value1"));
        assertTrue(result.contains("key2"));
        assertTrue(result.contains("42"));
    }

    @Test
    public void testStreamMapToOutputStreamWithEmptyMap() throws IOException {
        Map<String, Object> map = new HashMap<>();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonStreamUtil.streamMapToOutputStream(map, outputStream);

        String result = outputStream.toString();
        assertNotNull(result);
        assertEquals("{}", result);
    }

    @Test
    public void testStreamJsonToStringWithSimpleList() throws IOException {
        List<Object> list = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("key", "value");
        list.add(item);

        String result = JsonStreamUtil.streamJsonToString(list);

        assertNotNull(result);
        assertTrue(result.contains("key"));
        assertTrue(result.contains("value"));
    }

    @Test
    public void testStreamJsonToStringWithEmptyList() throws IOException {
        List<Object> list = new ArrayList<>();

        String result = JsonStreamUtil.streamJsonToString(list);

        assertNotNull(result);
        assertEquals("[]", result);
    }

    @Test
    public void testStreamMapWithNestedMap() throws IOException {
        Map<String, Object> outerMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("innerKey", "innerValue");
        outerMap.put("nested", innerMap);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonStreamUtil.streamMapToOutputStream(outerMap, outputStream);

        String result = outputStream.toString();
        assertNotNull(result);
        assertTrue(result.contains("nested"));
        assertTrue(result.contains("innerKey"));
        assertTrue(result.contains("innerValue"));
    }

    @Test
    public void testStreamListWithMixedTypes() throws IOException {
        List<Object> list = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("stringValue", "text");
        item.put("numberValue", 42);
        item.put("booleanValue", true);
        item.put("nullValue", null);
        list.add(item);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonStreamUtil.streamJsonToOutputStream(list, outputStream);

        String result = outputStream.toString();
        assertNotNull(result);
        assertTrue(result.contains("stringValue"));
        assertTrue(result.contains("text"));
        assertTrue(result.contains("42"));
        assertTrue(result.contains("true"));
        assertTrue(result.contains("null"));
    }

    @Test
    public void testStreamJsonWithLargeList() throws IOException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", i);
            item.put("name", "Item" + i);
            list.add(item);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonStreamUtil.streamJsonToOutputStream(list, outputStream);

        String result = outputStream.toString();
        assertNotNull(result);
        assertTrue(result.contains("Item0"));
        assertTrue(result.contains("Item99"));
    }
}
