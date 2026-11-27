package org.cda.archi.githubworkspace.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.CommandRunner;
import org.cda.archi.githubworkspace.infrastructure.ProcessExecutionException;
import org.cda.archi.githubworkspace.infrastructure.ProcessExecutor;
import org.cda.archi.githubworkspace.infrastructure.ProcessResult;
import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.models.GitHubRepository;

/** Reads local Git configuration and performs the explicitly requested repository clone only. */
public final class GitService {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(30);
    private static final ConcurrentHashMap<Path, ReentrantLock> REPOSITORY_LOCKS = new ConcurrentHashMap<>();
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

    /**
     * Reuses a verified checkout, or clones into a unique staging directory before
     * publishing it at the persistent destination. Failed staging contents are retained.
     */
    public Path cloneOrReuse(String cloneUrl, String organization, String repositoryName, Path destination,
            CancellationToken cancellation, Consumer<String> progress) throws IOException, InterruptedException {
        Path expected = destination.toAbsolutePath().normalize();
        WorkspaceService.validateOrganization(organization);
        WorkspaceService.validateRepositoryName(repositoryName);
        WorkspaceService.validateCloneUrl(cloneUrl, repositoryName, organization);
        if (!expected.getFileName().toString().equalsIgnoreCase(repositoryName)) {
            throw new IOException("The local repository path does not match the selected repository.");
        }
        ReentrantLock lock = REPOSITORY_LOCKS.computeIfAbsent(expected, ignored -> new ReentrantLock());
        if (!lock.tryLock()) throw new IOException("An operation for this local repository is already in progress.");
        try {
            cancellation.throwIfCancelled();
            if (Files.exists(expected, LinkOption.NOFOLLOW_LINKS)) {
                progress.accept("Using existing local repository...");
                return validateWorkingCopy(organization, repositoryName, expected, cancellation);
            }

            Files.createDirectories(expected.getParent());
            Path staging = Files.createTempDirectory(expected.getParent(), "." + repositoryName + ".clone-");
            progress.accept("Cloning repository...");
            List<String> arguments = List.of("git", "clone", "--progress", "--", cloneUrl, staging.toString());
            try (ProcessResult result = runner.run(arguments, new byte[0], CLONE_TIMEOUT, cancellation, progress)) {
                if (result.exitCode() != 0) {
                    throw new IOException(cloneFailureMessage(result.standardError()));
                }
            } catch (java.util.concurrent.CancellationException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (IOException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new IOException("Git could not complete the repository clone.");
            }
            cancellation.throwIfCancelled();
            validateWorkingCopy(organization, repositoryName, staging, cancellation);
            cancellation.throwIfCancelled();
            try {
                Files.move(staging, expected);
            } catch (java.nio.file.FileAlreadyExistsException race) {
                progress.accept("Using existing local repository...");
                return validateWorkingCopy(organization, repositoryName, expected, cancellation);
            }
            return validateWorkingCopy(organization, repositoryName, expected, cancellation);
        } finally {
            lock.unlock();
        }
    }

    /** Creates or reuses one independent checkout per repository and Plan ID. */
    public Path clonePlanOrReuse(String cloneUrl, String organization, String repositoryName,
            String defaultBranch, String planId, Path destination, CancellationToken cancellation,
            Consumer<String> progress) throws IOException, InterruptedException {
        WorkspaceService.validatePlanIdSyntax(planId);
        WorkspaceService.validateCloneUrl(cloneUrl, repositoryName, organization);
        if (defaultBranch == null || defaultBranch.isBlank()) {
            throw new IOException("GitHub did not provide a default branch for this repository.");
        }
        Path expected = destination.toAbsolutePath().normalize();
        if (!expected.getFileName().toString().equals(planId)
                || !expected.getParent().getFileName().toString().equals(repositoryName)) {
            throw new IOException("The plan destination does not match the selected repository and Plan ID.");
        }
        ReentrantLock lock = REPOSITORY_LOCKS.computeIfAbsent(expected, ignored -> new ReentrantLock());
        if (!lock.tryLock()) throw new IOException("An operation for this plan is already in progress.");
        try {
            cancellation.throwIfCancelled();
            if (Files.exists(expected, LinkOption.NOFOLLOW_LINKS)) {
                progress.accept("Validating existing local plan...");
                return validatePlanWorkingCopy(organization, repositoryName, planId, expected, cancellation);
            }
            Files.createDirectories(expected.getParent());
            Path staging = Files.createTempDirectory(expected.getParent(), "." + planId + ".clone-");
            progress.accept("Cloning repository into a staging directory...");
            List<String> clone = List.of("git", "clone", "--progress", "--", cloneUrl, staging.toString());
            try (ProcessResult result = runner.run(clone, new byte[0], CLONE_TIMEOUT, cancellation, progress)) {
                if (result.exitCode() != 0) throw new IOException(cloneFailureMessage(result.standardError()));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (IOException | java.util.concurrent.CancellationException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new IOException("Git could not complete the repository clone.");
            }

            cancellation.throwIfCancelled();
            validateWorkingCopy(organization, repositoryName, staging, cancellation);
            validateBranchName(defaultBranch, cancellation);
            String defaultRef = "refs/remotes/origin/" + defaultBranch;
            command(List.of("git", "-C", staging.toString(), "rev-parse", "--verify", defaultRef),
                    cancellation, true);
            String remotePlan = command(List.of("git", "-C", staging.toString(), "ls-remote",
                    "--heads", "origin", "refs/heads/" + planId), cancellation, true);
            cancellation.throwIfCancelled();
            if (remotePlan.isBlank()) {
                progress.accept("Creating local plan branch from " + defaultBranch + "...");
                command(List.of("git", "-C", staging.toString(), "switch", "--no-track", "-c",
                        planId, defaultRef), cancellation, true);
            } else {
                progress.accept("Checking out the existing remote plan branch...");
                command(List.of("git", "-C", staging.toString(), "fetch", "origin",
                        "refs/heads/" + planId + ":refs/remotes/origin/" + planId), cancellation, true);
                command(List.of("git", "-C", staging.toString(), "switch", "--track", "-c",
                        planId, "origin/" + planId), cancellation, true);
            }
            cancellation.throwIfCancelled();
            validatePlanWorkingCopy(organization, repositoryName, planId, staging, cancellation);
            WorkspaceService.modelPathWithin(staging);
            cancellation.throwIfCancelled();
            try {
                Files.move(staging, expected);
            } catch (java.nio.file.FileAlreadyExistsException race) {
                progress.accept("Using the local plan created by the concurrent operation...");
                return validatePlanWorkingCopy(organization, repositoryName, planId, expected, cancellation);
            }
            return expected.toRealPath();
        } finally {
            lock.unlock();
        }
    }

    /** Validates a plan checkout without changing its branch or worktree. */
    public Path validatePlanWorkingCopy(String organization, String repositoryName, String planId,
            Path destination, CancellationToken cancellation) throws IOException, InterruptedException {
        WorkspaceService.validatePlanIdSyntax(planId);
        Path root = validateWorkingCopy(organization, repositoryName, destination, cancellation);
        String branch = command(List.of("git", "-C", root.toString(), "branch", "--show-current"),
                cancellation, true);
        if (!branch.equals(planId)) {
            throw new IOException("This local checkout is on branch " + (branch.isBlank() ? "(detached HEAD)" : branch)
                    + ", not the requested plan " + planId + ". It was left unchanged.");
        }
        return root;
    }

    public List<Path> findValidPlanCheckouts(GitHubRepository repository, WorkspaceService workspace,
            CancellationToken cancellation) throws IOException, InterruptedException {
        List<Path> result = new ArrayList<>();
        for (Path path : workspace.planDirectories(repository)) {
            cancellation.throwIfCancelled();
            String planId = path.getFileName().toString();
            try {
                validatePlanWorkingCopy(WorkspaceService.repositoryOrganization(repository),
                        repository.getName(), planId, path, cancellation);
                workspace.modelPath(path);
                result.add(path);
            } catch (IOException invalidCheckout) {
                // Invalid, unrelated or branch-mismatched directories are never offered as openable plans.
            }
        }
        return List.copyOf(result);
    }

    public void validateBranchName(String branch, CancellationToken cancellation)
            throws IOException, InterruptedException {
        if (branch == null || branch.isBlank()) throw new IOException("The repository default branch is invalid.");
        command(List.of("git", "check-ref-format", "--branch", branch), cancellation, true);
    }

    public Path validateWorkingCopy(String organization, String repositoryName, Path destination,
            CancellationToken cancellation) throws IOException, InterruptedException {
        Path expected = destination.toAbsolutePath().normalize();
        if (!Files.isDirectory(expected, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(expected)) {
            throw new IOException("The local repository path exists but is not a safe directory.");
        }
        Path realExpected = expected.toRealPath();
        String topLevel = command(List.of("git", "-C", realExpected.toString(), "rev-parse", "--show-toplevel"),
                cancellation, true);
        Path actualRoot;
        try {
            actualRoot = Path.of(topLevel).toRealPath();
        } catch (RuntimeException ex) {
            throw new IOException("The existing directory is not a valid Git working copy.");
        }
        if (!actualRoot.equals(realExpected)) {
            throw new IOException("The existing Git working copy has a different root directory.");
        }
        String remote = command(List.of("git", "-C", realExpected.toString(), "remote", "get-url", "origin"),
                cancellation, true);
        if (!sameGitHubRepository(remote, organization, repositoryName)) {
            throw new IOException("The existing working copy's origin does not match the selected GitHub repository.");
        }
        return realExpected;
    }

    public static boolean sameGitHubRepository(String remote, String organization, String repositoryName) {
        String expected = organization.toLowerCase(java.util.Locale.ROOT) + "/"
                + repositoryName.toLowerCase(java.util.Locale.ROOT);
        String identity = parseRemoteIdentity(remote);
        return identity != null && identity.equals(expected);
    }

    private static String parseRemoteIdentity(String remote) {
        if (remote == null || remote.isBlank()) return null;
        String path;
        try {
            if (remote.startsWith("git@")) {
                int colon = remote.indexOf(':');
                if (colon < 0 || !remote.substring(0, colon).equalsIgnoreCase("git@github.com")) return null;
                path = remote.substring(colon + 1);
            } else {
                java.net.URI uri = java.net.URI.create(remote);
                String scheme = uri.getScheme();
                if (!("https".equalsIgnoreCase(scheme) || "ssh".equalsIgnoreCase(scheme))
                        || !"github.com".equalsIgnoreCase(uri.getHost()) || uri.getPort() != -1
                        || uri.getQuery() != null || uri.getFragment() != null
                        || (uri.getUserInfo() != null && !uri.getUserInfo().equalsIgnoreCase("git"))) return null;
                path = uri.getPath();
            }
        } catch (RuntimeException ex) {
            return null;
        }
        if (path.startsWith("/")) path = path.substring(1);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.toLowerCase(java.util.Locale.ROOT).endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        String[] parts = path.split("/", -1);
        if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
                || !parts[1].matches("[A-Za-z0-9_.-]{1,100}")) return null;
        return path.toLowerCase(java.util.Locale.ROOT);
    }

    private String command(List<String> arguments, CancellationToken cancellation, boolean required)
            throws IOException, InterruptedException {
        try (ProcessResult result = runner.run(arguments, new byte[0], COMMAND_TIMEOUT, cancellation)) {
            if (result.exitCode() != 0) {
                if (required) throw new IOException("Git could not validate the local working copy.");
                return "";
            }
            return new String(result.standardOutput(), StandardCharsets.UTF_8).trim();
        }
    }

    private static String cloneFailureMessage(byte[] stderr) {
        String output = new String(stderr, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
        if (output.contains("authentication failed") || output.contains("could not read username")) {
            return "GitHub requires authentication or the configured Git credential helper cancelled sign-in.";
        }
        if (output.contains("repository not found") || output.contains("not found")) {
            return "The repository is unavailable to this account or no longer exists.";
        }
        if (output.contains("no space left") || output.contains("not enough space")) {
            return "There is not enough disk space to clone this repository.";
        }
        if (output.contains("permission denied") || output.contains("access is denied")) {
            return "The workspace directory is not writable.";
        }
        return "Git could not clone the repository. Check network access and local Git Credential Manager.";
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
