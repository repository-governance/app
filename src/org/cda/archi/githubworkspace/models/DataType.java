package org.cda.archi.githubworkspace.models;

/** Runtime field types supported by Data Manager. */
public enum DataType {
    STRING, TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, EMAIL, URL, UUID;

    public String jsonType() {
        return switch (this) {
            case INTEGER -> "integer";
            case DECIMAL -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }

    public boolean allowedAsPrimaryKey() {
        return this == STRING || this == INTEGER || this == UUID;
    }

    public static DataType parse(String value) {
        try {
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Unsupported data type: " + value);
        }
    }
}
