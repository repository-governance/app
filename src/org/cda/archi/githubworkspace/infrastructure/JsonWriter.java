package org.cda.archi.githubworkspace.infrastructure;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

/** Small JSON writer paired with JsonParser to keep the bundle dependency-free. */
public final class JsonWriter {
    private JsonWriter() { }

    public static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof BigDecimal) return value.toString();
        if (value instanceof Number number) return new BigDecimal(number.toString()).toPlainString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(write(entry.getValue()));
                if (iterator.hasNext()) out.append(',');
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            Iterator<?> iterator = values.iterator();
            while (iterator.hasNext()) {
                out.append(write(iterator.next()));
                if (iterator.hasNext()) out.append(',');
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value.");
    }

    public static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
