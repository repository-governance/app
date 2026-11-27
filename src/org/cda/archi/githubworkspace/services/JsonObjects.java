package org.cda.archi.githubworkspace.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class JsonObjects {
    private JsonObjects() { }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String description) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Invalid " + description + ".");
        return (Map<String, Object>) value;
    }

    static String string(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing " + key + ".");
        }
        return text;
    }

    static String stringOrEmpty(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value instanceof String text ? text : "";
    }

    static long integer(Map<String, Object> object, String key, long fallback) {
        Object value = object.get(key);
        if (value instanceof BigDecimal number) return number.longValueExact();
        if (value instanceof Number number) return number.longValue();
        if (value == null) return fallback;
        throw new IllegalArgumentException("Invalid " + key + ".");
    }

    static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        Object value = object.get(key);
        if (value instanceof Boolean result) return result;
        if (value == null) return fallback;
        throw new IllegalArgumentException("Invalid " + key + ".");
    }

    static List<String> strings(Object value) {
        if (!(value instanceof List<?> items)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof String text) result.add(text);
        }
        return List.copyOf(result);
    }
}
