package org.cda.archi.githubworkspace.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cda.archi.githubworkspace.infrastructure.JsonParser;
import org.cda.archi.githubworkspace.infrastructure.JsonWriter;
import org.cda.archi.githubworkspace.models.ColumnDefinition;
import org.cda.archi.githubworkspace.models.DataType;
import org.cda.archi.githubworkspace.models.TableSchema;

/** Schema serialization, parsing, and remote-safe validation. */
public final class TableSchemaService {
    public String serialize(TableSchema schema) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("schemaVersion", schema.schemaVersion());
        object.put("name", schema.name());
        object.put("description", schema.description());
        object.put("primaryKey", schema.primaryKey());
        List<Map<String, Object>> columns = new ArrayList<>();
        for (ColumnDefinition column : schema.columns()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", column.name());
            value.put("type", displayType(column.type()));
            value.put("required", column.required());
            columns.add(value);
        }
        object.put("columns", columns);
        return JsonWriter.write(object) + "\n";
    }

    public TableSchema parse(String json) {
        Object parsed = JsonParser.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) throw new IllegalArgumentException("Schema must be a JSON object.");
        String version = string(map.get("schemaVersion"));
        String name = string(map.get("name"));
        String description = string(map.get("description"));
        String primaryKey = string(map.get("primaryKey"));
        if (version.isBlank() || name.isBlank() || primaryKey.isBlank()
                || !(map.get("description") instanceof String)) {
            throw new IllegalArgumentException("Schema is missing required metadata.");
        }
        List<ColumnDefinition> columns = new ArrayList<>();
        Object rawColumns = map.get("columns");
        if (!(rawColumns instanceof List<?> list) || list.size() > TableSchema.MAX_COLUMNS) {
            throw new IllegalArgumentException("Schema columns are missing or exceed the limit.");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> column)) throw new IllegalArgumentException("Invalid column definition.");
            String columnName = string(column.get("name"));
            DataType type = DataType.parse(string(column.get("type")));
            boolean required = bool(column.get("required"));
            Object marker = column.get("primaryKey");
            boolean primary = marker instanceof Boolean flag
                    ? flag : primaryKey.equals(columnName);
            columns.add(new ColumnDefinition(columnName, type, primary, required));
        }
        return new TableSchema(version, name, description, primaryKey, columns);
    }

    public static String displayType(DataType type) {
        return switch (type) {
            case STRING -> "String";
            case TEXT -> "Text";
            case INTEGER -> "Integer";
            case DECIMAL -> "Decimal";
            case BOOLEAN -> "Boolean";
            case DATE -> "Date";
            case DATETIME -> "DateTime";
            case EMAIL -> "Email";
            case URL -> "URL";
            case UUID -> "UUID";
        };
    }

    private static String string(Object value) {
        if (value == null) return "";
        if (!(value instanceof String text)) throw new IllegalArgumentException("Invalid schema string value.");
        return text;
    }
    private static boolean bool(Object value) {
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException("Invalid schema boolean value.");
        return flag;
    }
}
