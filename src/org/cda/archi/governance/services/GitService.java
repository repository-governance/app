package org.cda.archi.governance.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.cda.archi.governance.infrastructure.CancellationToken;
import org.cda.archi.governance.infrastructure.CommandRunner;
import org.cda.archi.governance.infrastructure.ProcessExecutionException;
import org.cda.archi.governance.infrastructure.ProcessExecutor;
import org.cda.archi.governance.infrastructure.ProcessResult;
import org.cda.archi.governance.models.GitConfiguration;

/** Reads the user's local Git installation and configured identity; performs no writes. */
public final class GitService {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(8);
    private final CommandRunner runner;

    public GitService() {
        this(new ProcessExecutor());
    }

    public GitService(CommandRunner runner) {
        this.runner = runner;
    }

    public GitConfiguration inspect(CancellationToken cancellation) {
        String version;
        try {
            version = command(List.of("git", "--version"), cancellation, true);
        } catch (IOException ex) {
            return new GitConfiguration(false, "", "", "", false,
                    "Git was not found in PATH or could not be started.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new GitConfiguration(false, "", "", "", false, "Git detection was cancelled.");
        }
        try {
            String userName = command(List.of("git", "config", "--get", "user.name"), cancellation, false);
            String userEmail = command(List.of("git", "config", "--get", "user.email"), cancellation, false);
            String helpers = command(List.of("git", "config", "--get-all", "credential.helper"),
                    cancellation, false);
            String githubHelpers = command(List.of("git", "config", "--get-all",
                    "credential.https://github.com.helper"), cancellation, false);
            boolean configuredHelper = !effectiveHelpers(helpers).isEmpty()
                    || !effectiveHelpers(githubHelpers).isEmpty();
            return new GitConfiguration(true, version, userName, userEmail, configuredHelper,
                    configuredHelper
                    ? "Git and its credential helper are available."
                    : "Git is available, but no credential helper is configured.");
        } catch (IOException ex) {
            return new GitConfiguration(true, version, "", "", false,
                    "Git is available, but its configuration could not be read.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new GitConfiguration(true, version, "", "", false, "Git inspection was cancelled.");
        }
    }

    private String command(List<String> arguments, CancellationToken cancellation, boolean required)
            throws IOException, InterruptedException {
        try (ProcessResult result = runner.run(arguments, new byte[0], COMMAND_TIMEOUT, cancellation)) {
            if (result.exitCode() != 0) {
                if (required) throw new ProcessExecutionException("Git command failed.");
                return "";
            }
            return new String(result.standardOutput(), StandardCharsets.UTF_8).trim();
        }
    }

    private static List<String> effectiveHelpers(String value) {
        List<String> result = new ArrayList<>();
        for (String line : value.split("\\R")) {
            String helper = line.trim();
            // Git treats an empty helper value as a reset of previously configured helpers.
            if (helper.isEmpty()) result.clear();
            else result.add(helper);
        }
        return List.copyOf(result);
    }
}
