package org.cda.archi.githubworkspace.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.cda.archi.githubworkspace.models.ColumnDefinition;
import org.cda.archi.githubworkspace.models.DataType;
import org.cda.archi.githubworkspace.models.TableSchema;

/** Validates and converts dynamic form values to JSON-native types. */
public final class DataValidationService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_DOCUMENT_CHARS = 256 * 1024;

    public Map<String, Object> validate(TableSchema schema, Map<String, String> raw, boolean creating)
            throws IllegalArgumentException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ColumnDefinition column : schema.columns()) {
            String text = raw.get(column.name());
            if (text == null) text = "";
            if (column.primaryKey() && creating && column.type() == DataType.UUID && text.isBlank()) {
                text = UUID.randomUUID().toString();
            }
            if (text.isBlank()) {
                if (column.required()) throw new IllegalArgumentException(column.name() + " is required.");
                values.put(column.name(), null);
                continue;
            }
            try {
                values.put(column.name(), convert(column, text));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Invalid value for " + column.name() + ": " + ex.getMessage());
            }
        }
        String json = org.cda.archi.githubworkspace.infrastructure.JsonWriter.write(values);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_DOCUMENT_CHARS) {
            throw new IllegalArgumentException("Record exceeds 256 KiB.");
        }
        return values;
    }

    public Map<String, Object> validateStored(TableSchema schema, Map<String, Object> stored) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ColumnDefinition column : schema.columns()) {
            Object value = stored.get(column.name());
            if (value == null) {
                if (column.required()) throw new IllegalArgumentException(column.name() + " is required.");
                values.put(column.name(), null);
                continue;
            }
            boolean validType = switch (column.type()) {
                case INTEGER -> value instanceof BigDecimal decimal && decimal.stripTrailingZeros().scale() <= 0;
                case DECIMAL -> value instanceof BigDecimal;
                case BOOLEAN -> value instanceof Boolean;
                default -> value instanceof String;
            };
            if (!validType) throw new IllegalArgumentException("Stored JSON type does not match " + column.name() + ".");
            try {
                String text = String.valueOf(value);
                values.put(column.name(), convert(column, text));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Invalid stored value for " + column.name() + ": "
                        + ex.getMessage());
            }
        }
        if (stored.size() > schema.columns().size() || stored.keySet().stream()
                .anyMatch(key -> schema.columns().stream().noneMatch(c -> c.name().equals(key)))) {
            throw new IllegalArgumentException("Record contains fields that are not defined in the schema.");
        }
        return values;
    }

    private Object convert(ColumnDefinition column, String text) {
        return switch (column.type()) {
            case INTEGER -> Integer.valueOf(text.trim());
            case DECIMAL -> new BigDecimal(text.trim());
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
                    throw new IllegalArgumentException("enter true or false");
                }
                yield Boolean.valueOf(text);
            }
            case DATE -> LocalDate.parse(text.trim()).toString();
            case DATETIME -> OffsetDateTime.parse(text.trim()).toString();
            case EMAIL -> {
                if (!EMAIL.matcher(text.trim()).matches()) throw new IllegalArgumentException("enter a valid email");
                yield text.trim();
            }
            case URL -> {
                java.net.URI uri = java.net.URI.create(text.trim());
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) throw new IllegalArgumentException("enter an HTTP(S) URL");
                yield uri.toASCIIString();
            }
            case UUID -> java.util.UUID.fromString(text.trim()).toString();
            case STRING -> {
                if (!column.matchesPattern(text)) throw new IllegalArgumentException(column.patternFailureMessage());
                yield text;
            }
            case TEXT -> text;
        };
    }
}
