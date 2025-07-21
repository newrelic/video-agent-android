package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;

public class DefaultSizeEstimator implements SizeEstimator {
    // Cache for repeated string size calculations (mobile optimization)
    private static final int MAX_CACHE_SIZE = 100;
    private final java.util.LinkedHashMap<String, Integer> sizeCache = new java.util.LinkedHashMap<String, Integer>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    // Prevent deep recursion that can cause stack overflow on mobile
    private static final int MAX_RECURSION_DEPTH = 5;

    @Override
    public int estimate(Object obj) {
        return estimateWithDepth(obj, 0);
    }

    private int estimateWithDepth(Object obj, int depth) {
        if (obj == null || depth >= MAX_RECURSION_DEPTH) return 0;

        if (obj instanceof String) {
            String str = (String) obj;
            // Use cache for strings to avoid repeated length calculations - API 16+ compatible
            Integer cachedSize = sizeCache.get(str);
            if (cachedSize != null) {
                return cachedSize;
            }
            int size = str.length() * 2; // UTF-16 mobile safe
            sizeCache.put(str, size);
            return size;
        }

        if (obj instanceof Number) {
            // Optimized primitive size detection
            Class<?> clazz = obj.getClass();
            if (clazz == Integer.class || clazz == Float.class) return 4;
            if (clazz == Long.class || clazz == Double.class) return 8;
            if (clazz == Short.class || clazz == Byte.class) return 2;
            return 8; // Default for BigInteger, etc.
        }

        if (obj instanceof Boolean) return 1;

        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            int size = 0;
            int count = 0;
            // Limit map processing for mobile performance
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (++count > 20) break; // Limit to prevent performance issues
                size += estimateWithDepth(entry.getKey(), depth + 1);
                size += estimateWithDepth(entry.getValue(), depth + 1);
            }
            return size;
        }

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            int size = 0;
            int maxItems = Math.min(list.size(), 20); // Limit for mobile performance
            for (int i = 0; i < maxItems; i++) {
                size += estimateWithDepth(list.get(i), depth + 1);
            }
            return size;
        }

        return 16; // Default for other types
    }
}
