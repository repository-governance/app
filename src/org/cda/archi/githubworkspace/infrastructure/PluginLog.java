package org.cda.archi.githubworkspace.infrastructure;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Appends safe diagnostic traces to the current user's GitHub Workspace profile directory. */
public final class PluginLog {
    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private PluginLog() { }

    public static Path logFile() throws IOException {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) throw new IOException("The user profile directory is unavailable.");
        try {
            return Path.of(home).resolve(".github-workspace").resolve("logs")
                    .resolve("github-workspace.log").toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            throw new IOException("The user profile directory is invalid.");
        }
    }

    public static void info(String component, String message) {
        append("INFO", component, message, null);
    }

    public static void warn(String component, String message, Throwable error) {
        append("WARN", component, message, error);
    }

    public static void error(String component, String message, Throwable error) {
        append("ERROR", component, message, error);
    }

    public static String readAll() throws IOException {
        Path file = logFile();
        if (!Files.exists(file)) return "";
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static void append(String level, String component, String message, Throwable error) {
        synchronized (LOCK) {
            try {
                Path file = logFile();
                Files.createDirectories(file.getParent());
                StringBuilder entry = new StringBuilder();
                String prefix = TIMESTAMP.format(Instant.now()) + " " + level
                        + " [" + safe(component) + "] [" + Thread.currentThread().getName() + "] ";
                entry.append(prefix).append(singleLine(message)).append(System.lineSeparator());
                if (error != null) {
                    StringWriter stack = new StringWriter();
                    error.printStackTrace(new PrintWriter(stack));
                    for (String line : stack.toString().split("\\R", -1)) {
                        if (!line.isEmpty()) entry.append(prefix).append(line).append(System.lineSeparator());
                    }
                }
                Files.writeString(file, entry, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException ignored) {
                // Logging must never break a repository operation or SWT event.
            }
        }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "GitHub Workspace";
        return singleLine(value);
    }

    private static String singleLine(String value) {
        if (value == null || value.isBlank()) return "(no message)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
