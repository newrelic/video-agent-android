package com.newrelic.videoagent.core.harvest;

import java.util.List;
import java.util.Map;

public class DefaultSizeEstimator implements SizeEstimator {
    @Override
    public int estimate(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof String) {
            // Use length() for ASCII, multiply by 2 for a rough upper bound for UTF-16 (mobile safe)
            return ((String) obj).length() * 2;
        }
        if (obj instanceof Number) {
            // Use more accurate primitive sizes
            if (obj instanceof Byte || obj instanceof Short) return 2;
            if (obj instanceof Integer || obj instanceof Float) return 4;
            if (obj instanceof Long || obj instanceof Double) return 8;
            return 8; // Default for other numbers
        }
        if (obj instanceof Boolean) return 1;
        if (obj instanceof Map) {
            int size = 0;
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                size += estimate(entry.getKey());
                size += estimate(entry.getValue());
            }
            return size;
        }
        if (obj instanceof List) {
            int size = 0;
            for (Object item : (List<?>) obj) {
                size += estimate(item);
            }
            return size;
        }
        return 16; // Default for other types
    }
}
