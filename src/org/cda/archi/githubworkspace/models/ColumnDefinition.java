package org.cda.archi.githubworkspace.models;

/** One ordered column in a table schema. */
public record ColumnDefinition(String name, DataType type, boolean primaryKey, boolean required) {
    public ColumnDefinition {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Column names must be safe identifiers of at most 64 characters.");
        }
        if (type == null) throw new IllegalArgumentException("Choose a column data type.");
        if (primaryKey && !type.allowedAsPrimaryKey()) {
            throw new IllegalArgumentException("Primary keys must use String, Integer, or UUID.");
        }
        if (primaryKey) required = true;
    }
}
