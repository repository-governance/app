package org.cda.archi.githubworkspace.models;

/** One ordered column in a table schema. */
public record ColumnDefinition(String name, DataType type, boolean primaryKey, boolean required,
        String pattern, String validationMessage) {
    private static final int MAX_PATTERN_LENGTH = 256;
    private static final int MAX_VALIDATION_MESSAGE_LENGTH = 300;

    public ColumnDefinition(String name, DataType type, boolean primaryKey, boolean required) {
        this(name, type, primaryKey, required, "", "");
    }

    public ColumnDefinition {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Column names must be safe identifiers of at most 64 characters.");
        }
        if (type == null) throw new IllegalArgumentException("Choose a column data type.");
        if (primaryKey && !type.allowedAsPrimaryKey()) {
            throw new IllegalArgumentException("Primary keys must use String, Integer, or UUID.");
        }
        if (primaryKey) required = true;
        pattern = pattern == null ? "" : pattern;
        validationMessage = validationMessage == null ? "" : validationMessage.strip();
        if (type != DataType.STRING && (!pattern.isEmpty() || !validationMessage.isEmpty())) {
            throw new IllegalArgumentException("Regex validation is only available for String columns.");
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("Regex pattern is limited to 256 characters.");
        }
        if (validationMessage.length() > MAX_VALIDATION_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Validation message is limited to 300 characters.");
        }
        if (!pattern.isEmpty()) {
            try {
                java.util.regex.Pattern.compile(pattern);
            } catch (java.util.regex.PatternSyntaxException ex) {
                throw new IllegalArgumentException("Invalid regular expression: " + ex.getDescription());
            }
        } else {
            validationMessage = "";
        }
    }

    public boolean matchesPattern(String value) {
        return pattern.isEmpty() || java.util.regex.Pattern.matches(pattern, value);
    }

    public String patternFailureMessage() {
        return validationMessage.isEmpty() ? "Value does not match the required pattern." : validationMessage;
    }
}
