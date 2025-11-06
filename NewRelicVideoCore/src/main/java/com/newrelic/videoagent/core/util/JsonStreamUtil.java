package com.newrelic.videoagent.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

/**
 * Lightweight JSON streaming utility optimized for mobile/TV performance
 * Avoids JSONObject/JSONArray overhead for better resource usage
 * Provides static methods for streaming JSON directly to OutputStream
 */
public class JsonStreamUtil {

    private static final String UTF_8 = "UTF-8"; // Compatibility with API level 16

    /**
     * Stream a List of objects to OutputStream as JSON array
     * Optimized for mobile/TV performance with direct streaming
     */
    public static void streamJsonToOutputStream(List<Object> data, OutputStream outputStream) throws IOException {
        try {
            byte[] openBracket = "[".getBytes(UTF_8);
            byte[] closeBracket = "]".getBytes(UTF_8);
            byte[] comma = ",".getBytes(UTF_8);

            outputStream.write(openBracket);

            for (int i = 0; i < data.size(); i++) {
                if (i > 0) {
                    outputStream.write(comma);
                }
                streamObjectToOutputStream(data.get(i), outputStream);
            }

            outputStream.write(closeBracket);
        } catch (UnsupportedEncodingException e) {
            throw new IOException("UTF-8 encoding not supported", e);
        }
    }

    /**
     * Stream a Map to OutputStream as JSON object
     * Optimized for mobile/TV performance with direct streaming
     */
    public static void streamMapToOutputStream(Map<String, Object> map, OutputStream outputStream) throws IOException {
        try {
            byte[] openBrace = "{".getBytes(UTF_8);
            byte[] closeBrace = "}".getBytes(UTF_8);
            byte[] comma = ",".getBytes(UTF_8);
            byte[] colon = ":".getBytes(UTF_8);
            byte[] quote = "\"".getBytes(UTF_8);

            outputStream.write(openBrace);

            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    outputStream.write(comma);
                }

                // Write key
                outputStream.write(quote);
                outputStream.write(escapeJsonString(entry.getKey()).getBytes(UTF_8));
                outputStream.write(quote);
                outputStream.write(colon);

                // Write value
                streamObjectToOutputStream(entry.getValue(), outputStream);
                first = false;
            }

            outputStream.write(closeBrace);
        } catch (UnsupportedEncodingException e) {
            throw new IOException("UTF-8 encoding not supported", e);
        }
    }

    /**
     * Stream any object to OutputStream with appropriate JSON formatting
     */
    private static void streamObjectToOutputStream(Object value, OutputStream outputStream) throws IOException {
        try {
            byte[] quote = "\"".getBytes(UTF_8);

            if (value == null) {
                outputStream.write("null".getBytes(UTF_8));
            } else if (value instanceof String) {
                outputStream.write(quote);
                outputStream.write(escapeJsonString((String) value).getBytes(UTF_8));
                outputStream.write(quote);
            } else if (value instanceof Number || value instanceof Boolean) {
                outputStream.write(value.toString().getBytes(UTF_8));
            } else if (value instanceof List) {
                streamJsonToOutputStream((List<Object>) value, outputStream);
            } else if (value instanceof Map) {
                streamMapToOutputStream((Map<String, Object>) value, outputStream);
            } else {
                // Fallback: convert to string and quote it
                outputStream.write(quote);
                outputStream.write(escapeJsonString(value.toString()).getBytes(UTF_8));
                outputStream.write(quote);
            }
        } catch (UnsupportedEncodingException e) {
            throw new IOException("UTF-8 encoding not supported", e);
        }
    }

    /**
     * Escape JSON string values with StringBuilder for better performance
     */
    private static String escapeJsonString(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        // Pre-allocate StringBuilder with estimated capacity
        StringBuilder result = new StringBuilder(str.length() + 16);

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\':
                    result.append("\\\\");
                    break;
                case '"':
                    result.append("\\\"");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    result.append(c);
                    break;
            }
        }

        return result.toString();
    }

    /**
     * Convenience method to stream JSON to OutputStream and return as String
     * Useful for generating JSON payloads
     */
    public static String streamJsonToString(List<Object> data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        streamJsonToOutputStream(data, baos);
        return baos.toString(UTF_8);
    }
}
