package org.cda.archi.githubworkspace.models;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Schema is the sole definition source for a dynamically managed table. */
public record TableSchema(String schemaVersion, String name, String description,
        String primaryKey, List<ColumnDefinition> columns) {
    public static final int MAX_COLUMNS = 64;

    public TableSchema {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        if (!"1.0".equals(schemaVersion)) throw new IllegalArgumentException("Unsupported schema version.");
        if (!isSafeTableName(name)) throw new IllegalArgumentException(
                "Table names must use lowercase letters, digits, and hyphens (1–48 characters).");
        description = description == null ? "" : description.strip();
        columns = List.copyOf(columns == null ? List.of() : columns);
        if (columns.isEmpty() || columns.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("A table must have between 1 and " + MAX_COLUMNS + " columns.");
        }
        Set<String> names = new HashSet<>();
        int primaryCount = 0;
        String actualPrimary = null;
        for (ColumnDefinition column : columns) {
            if (!names.add(column.name().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Column names must be unique.");
            }
            if (column.primaryKey()) {
                primaryCount++;
                actualPrimary = column.name();
            }
        }
        if (primaryCount != 1) throw new IllegalArgumentException("Define exactly one primary key.");
        if (primaryKey != null && !primaryKey.equals(actualPrimary)) {
            throw new IllegalArgumentException("Primary key metadata does not match the columns.");
        }
        primaryKey = actualPrimary;
    }

    public static boolean isSafeTableName(String name) {
        return name != null && name.matches("[a-z][a-z0-9-]{0,47}") && !name.contains("..");
    }

    public ColumnDefinition primaryKeyColumn() {
        return columns.stream().filter(ColumnDefinition::primaryKey).findFirst().orElseThrow();
    }
}
