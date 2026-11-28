package org.cda.archi.githubworkspace.models;

import java.util.LinkedHashMap;
import java.util.Map;

/** A typed JSON object and its Contents API blob SHA. */
public record DataRecord(Map<String, Object> values, String sha) {
    public DataRecord {
        values = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public Object value(String column) { return values.get(column); }

    public String primaryKey(TableSchema schema) {
        Object value = values.get(schema.primaryKey());
        return value == null ? "" : String.valueOf(value);
    }
}
