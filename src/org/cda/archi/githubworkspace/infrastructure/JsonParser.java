package org.cda.archi.githubworkspace.infrastructure;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON parser to avoid adding a runtime library to the Archi bundle. */
public final class JsonParser {
    private final String source;
    private int cursor;

    private JsonParser(String source) {
        this.source = source;
    }

    public static Object parse(String source) {
        JsonParser parser = new JsonParser(source);
        Object result = parser.value();
        parser.space();
        if (parser.cursor != source.length()) throw parser.invalid();
        return result;
    }

    private Object value() {
        space();
        if (cursor >= source.length()) throw invalid();
        return switch (source.charAt(cursor)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        cursor++;
        space();
        Map<String, Object> result = new LinkedHashMap<>();
        if (take('}')) return result;
        while (true) {
            space();
            if (peek() != '"') throw invalid();
            String key = string();
            space();
            require(':');
            Object value = value();
            if (result.containsKey(key)) throw invalid();
            result.put(key, value);
            space();
            if (take('}')) return result;
            require(',');
        }
    }

    private List<Object> array() {
        cursor++;
        space();
        List<Object> result = new ArrayList<>();
        if (take(']')) return result;
        while (true) {
            result.add(value());
            space();
            if (take(']')) return result;
            require(',');
        }
    }

    private String string() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (cursor < source.length()) {
            char c = source.charAt(cursor++);
            if (c == '"') return result.toString();
            if (c < 0x20) throw invalid();
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (cursor >= source.length()) throw invalid();
            char escaped = source.charAt(cursor++);
            switch (escaped) {
                case '"': case '\\': case '/': result.append(escaped); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'u':
                    if (cursor + 4 > source.length()) throw invalid();
                    try {
                        result.append((char) Integer.parseInt(source.substring(cursor, cursor + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw invalid();
                    }
                    cursor += 4;
                    break;
                default: throw invalid();
            }
        }
        throw invalid();
    }

    private BigDecimal number() {
        int start = cursor;
        if (take('-') && cursor >= source.length()) throw invalid();
        if (take('0')) {
            if (digit(peek())) throw invalid();
        } else {
            if (!digitOneToNine(peek())) throw invalid();
            while (digit(peek())) cursor++;
        }
        if (take('.')) {
            if (!digit(peek())) throw invalid();
            while (digit(peek())) cursor++;
        }
        if (take('e') || take('E')) {
            if (!take('+')) take('-');
            if (!digit(peek())) throw invalid();
            while (digit(peek())) cursor++;
        }
        try {
            return new BigDecimal(source.substring(start, cursor));
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    private Object literal(String token, Object value) {
        if (!source.startsWith(token, cursor)) throw invalid();
        cursor += token.length();
        return value;
    }

    private void space() {
        while (cursor < source.length() && " \t\r\n".indexOf(source.charAt(cursor)) >= 0) cursor++;
    }
    private char peek() { return cursor < source.length() ? source.charAt(cursor) : '\0'; }
    private boolean take(char c) {
        if (peek() != c) return false;
        cursor++;
        return true;
    }
    private void require(char c) {
        if (!take(c)) throw invalid();
    }
    private boolean digit(char c) { return c >= '0' && c <= '9'; }
    private boolean digitOneToNine(char c) { return c >= '1' && c <= '9'; }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid JSON."); }
}
