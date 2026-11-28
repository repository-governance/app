package org.cda.archi.githubworkspace.tests;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.cda.archi.githubworkspace.clients.GitHubApiClient;
import org.cda.archi.githubworkspace.clients.RestClient;
import org.cda.archi.githubworkspace.clients.RestResponse;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.CommandRunner;
import org.cda.archi.githubworkspace.infrastructure.GitCredentialAdapter;
import org.cda.archi.githubworkspace.infrastructure.JsonParser;
import org.cda.archi.githubworkspace.infrastructure.ProcessExecutionException;
import org.cda.archi.githubworkspace.infrastructure.ProcessExecutor;
import org.cda.archi.githubworkspace.infrastructure.ProcessResult;
import org.cda.archi.githubworkspace.infrastructure.PluginLog;
import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.models.GitCredential;
import org.cda.archi.githubworkspace.models.GitHubOrganization;
import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.RepositorySearchResult;
import org.cda.archi.githubworkspace.services.GitCredentialService;
import org.cda.archi.githubworkspace.services.ArchiModelService;
import org.cda.archi.githubworkspace.services.GitHubApiException;
import org.cda.archi.githubworkspace.services.GitHubAuthenticationService;
import org.cda.archi.githubworkspace.services.GitHubRepositoryService;
import org.cda.archi.githubworkspace.services.GitHubSession;
import org.cda.archi.githubworkspace.services.GitService;
import org.cda.archi.githubworkspace.services.IncompleteSearchException;
import org.cda.archi.githubworkspace.services.PreferencesService;
import org.cda.archi.githubworkspace.services.WorkspaceService;

/** Mock-based tests: no real Git credential store, GitHub account, or user token. */
public final class GitHubWorkspaceTests {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testGitInspectionAndIdentity();
        testProcessExecutorCapturesBothStreams();
        testCredentialProtocolAndSecretClearing();
        testCredentialTimeoutAndCancellation();
        testGitHubIdentityValidation();
        testIncompatibleCredentialIsExplicit();
        testRepositoryPaginationAndFields();
        testIncompleteAndOverLimitResultsAreRejected();
        testEmptySearchAndAuthorizationErrors();
        testQueryValidationAndAuthorizationHostRestriction();
        testWorkspacePathAndCloneUrlValidation();
        testPersistentPreferenceValuesAndWorkspaceValidation();
        testPersistentDiagnosticLog();
        testNativeModelOpenDeduplication();
        testGitCloneReuseAndLocalChanges();
        testPlanCloneBranchAndReuse();
        testThumbnailSelectionAndCache();
        assertions += CatalogRegressionTests.run();
        assertions += org.cda.archi.githubworkspace.views.CloneRepositoryDialogTests.run();
        assertions += org.cda.archi.githubworkspace.services.RepositoryManagementServiceTests.run();
        testJsonValidation();
        System.out.println("PASS: " + assertions + " GitHub Workspace assertions");
    }

    private static void testWorkspacePathAndCloneUrlValidation() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path home = Files.createTempDirectory("github-workspace-workspace-home-");
        try {
            System.setProperty("user.home", home.toString());
            WorkspaceService workspace = new WorkspaceService(home.resolve(".github-workspace/workspace"));
            GitHubRepository repository = repository("SBB-009999",
                    "https://github.com/repository-governance/SBB-009999.git");
            Path repositoryDirectory = home.resolve(".github-workspace/workspace/github.com/repository-governance/SBB-009999");
            equal(repositoryDirectory.toAbsolutePath().normalize(),
                    workspace.repositoryPath(repository), "Repository workspace is independent of Plan ID");
            Path path = workspace.preparePlanDirectory(repository, "D-09999");
            equal(repositoryDirectory.resolve("D-09999").toAbsolutePath().normalize(), path,
                    "Plan ID is appended after repository identity");
            check(Files.isDirectory(repositoryDirectory), "Workspace and repository directories are prepared");
            check(!Files.exists(path), "Preparing a plan path does not create or overwrite a checkout");
            WorkspaceService.validateCloneUrl(repository);
            equal("Operational-Gateway-Innovation-Center",
                    WorkspaceService.normalizeRepositoryName("  Operational Gateway   Innovation Center  "),
                    "Human-readable repository names are converted to hyphenated GitHub names");
            equal("SBB-010001", WorkspaceService.normalizeRepositoryName("SBB-010001"),
                    "Already-valid repository names are preserved");
            WorkspaceService.validateOrganization("repository-governance");
            expectThrows(IllegalArgumentException.class,
                    () -> WorkspaceService.validateOrganization("../outside"), "Organization traversal is rejected");
            expectThrows(IOException.class,
                    () -> workspace.repositoryPath(
                            repository("../outside", "https://github.com/repository-governance/SBB-009999.git")),
                    "Repository path traversal is rejected");
            expectThrows(IllegalArgumentException.class,
                    () -> WorkspaceService.validatePlanIdSyntax("../D-09999"), "Plan ID traversal is rejected");
            expectThrows(IllegalArgumentException.class,
                    () -> WorkspaceService.validatePlanIdSyntax("main"), "Only D-number plan IDs are accepted");
            expectThrows(IOException.class,
                    () -> WorkspaceService.validateCloneUrl(repository("SBB-009999",
                            "https://evil.example/repository-governance/SBB-009999.git")),
                    "Non-GitHub clone URL is rejected");
            expectThrows(IOException.class,
                    () -> WorkspaceService.validateCloneUrl(repository("SBB-009999",
                            "https://github.com/other/SBB-009999.git")),
                    "Clone URL identity mismatch is rejected");

            Path model = path.resolve("source/design.archimate");
            Files.createDirectories(model.getParent());
            Files.writeString(model, "model fixture");
            equal(model.toRealPath(), workspace.modelPath(path), "Only source/design.archimate is selected");
            Files.delete(model);
            expectThrows(IOException.class, () -> workspace.modelPath(path),
                    "Missing ArchiMate source file is reported");
        } finally {
            if (originalHome == null) System.clearProperty("user.home");
            else System.setProperty("user.home", originalHome);
            deleteTree(home);
        }
    }

    private static void testPersistentDiagnosticLog() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path home = Files.createTempDirectory("github-workspace-log-home-");
        try {
            System.setProperty("user.home", home.toString());
            PluginLog.info("Test", "persistent log entry");
            PluginLog.error("Test", "stack trace fixture", new IllegalStateException("trace fixture"));
            Path expected = home.resolve(".github-workspace/logs/github-workspace.log");
            equal(expected.toAbsolutePath().normalize(), PluginLog.logFile(),
                    "Diagnostic log is stored under the current user's profile");
            String contents = PluginLog.readAll();
            check(contents.contains("INFO [Test]") && contents.contains("persistent log entry"),
                    "Diagnostic log appends timestamped info entries");
            check(contents.contains("ERROR [Test]") && contents.contains("stack trace fixture")
                    && contents.contains("java.lang.IllegalStateException: trace fixture"),
                    "Diagnostic log persists the complete exception trace line by line");
        } finally {
            if (originalHome == null) System.clearProperty("user.home");
            else System.setProperty("user.home", originalHome);
            deleteTree(home);
        }
    }

    private static void testPersistentPreferenceValuesAndWorkspaceValidation() throws Exception {
        Path home = Files.createTempDirectory("github-workspace-preferences-home-");
        try {
            equal(home.resolve(".github-workspace/workspace").toAbsolutePath().normalize(),
                    PreferencesService.defaultWorkspace(home),
                    "Default workspace is resolved from user.home");
            Map<String, Object> values = new HashMap<>();
            org.eclipse.core.runtime.preferences.IEclipsePreferences node =
                    (org.eclipse.core.runtime.preferences.IEclipsePreferences) java.lang.reflect.Proxy.newProxyInstance(
                            GitHubWorkspaceTests.class.getClassLoader(),
                            new Class<?>[] {org.eclipse.core.runtime.preferences.IEclipsePreferences.class},
                            (proxy, method, args) -> switch (method.getName()) {
                                case "get" -> values.getOrDefault(args[0], args[1]);
                                case "put" -> { values.put((String) args[0], args[1]); yield null; }
                                case "getBoolean" -> values.containsKey(args[0])
                                        ? values.get(args[0]) : args[1];
                                case "putBoolean" -> { values.put((String) args[0], args[1]); yield null; }
                                case "flush", "sync" -> null;
                                case "toString" -> "test preference node";
                                default -> throw new UnsupportedOperationException(method.getName());
                            });
            PreferencesService preferences = new PreferencesService(node);
            equal("repository-governance", preferences.getOrganization(), "Reference organization is default");
            equal("archimate", preferences.getTopic(), "Reference repository topic is default");
            equal("https://github.com/repository-governance/templates", preferences.getTemplateRepository(),
                    "The configured repository of root-level templates has a default");
            check(preferences.useDefaultWorkspace(), "Default workspace is enabled initially");
            preferences.saveSearch("example-org", "architecture");
            preferences.saveTemplateRepository("https://github.com/example-org/templates");
            equal("example-org", preferences.getOrganization(), "Organization preference persists");
            equal("architecture", preferences.getTopic(), "Topic preference persists");
            equal("https://github.com/example-org/templates", preferences.getTemplateRepository(),
                    "Template repository preference persists");

            Path custom = home.resolve("custom-workspace");
            preferences.saveWorkspace(false, custom.toString());
            equal(custom.toAbsolutePath().normalize(), preferences.getWorkspaceRoot(),
                    "Custom workspace root is resolved from preferences");
            PreferencesService.validateWorkspaceLocation(custom.toString());
            check(Files.isDirectory(custom), "Workspace validation creates a missing custom directory");
            Path notDirectory = home.resolve("not-a-directory");
            Files.writeString(notDirectory, "preserve");
            expectThrows(IOException.class,
                    () -> PreferencesService.validateWorkspaceLocation(notDirectory.toString()),
                    "A regular file cannot be selected as a workspace");
            preferences.restoreDefaults();
            check(preferences.useDefaultWorkspace(), "Restore Defaults returns to default workspace");
            equal("repository-governance", preferences.getOrganization(), "Restore Defaults resets organization");
            equal("archimate", preferences.getTopic(), "Restore Defaults resets topic");
            equal("https://github.com/repository-governance/templates", preferences.getTemplateRepository(),
                    "Restore Defaults resets the template repository");
            equal("", preferences.getLastAccount(),
                    "Authentication identity is not populated by discovery preferences");
        } finally {
            deleteTree(home);
        }
    }

    private static void testNativeModelOpenDeduplication() throws Exception {
        Path root = Files.createTempDirectory("github-workspace-native-model-");
        try {
            Path model = root.resolve("source/design.archimate");
            Files.createDirectories(model.getParent());
            Files.writeString(model, "native model test fixture");
            AtomicReference<Path> loaded = new AtomicReference<>();
            AtomicInteger opens = new AtomicInteger();
            AtomicInteger activations = new AtomicInteger();
            ArchiModelService service = new ArchiModelService(new ArchiModelService.NativeAccess() {
                @Override public Path findLoaded(Path normalizedPhysicalPath) {
                    return loaded.get();
                }
                @Override public Path openAndActivate(Path normalizedPhysicalPath) {
                    opens.incrementAndGet();
                    loaded.set(normalizedPhysicalPath);
                    return normalizedPhysicalPath;
                }
                @Override public void activate(Path normalizedPhysicalPath) {
                    activations.incrementAndGet();
                }
            });

            Path first = service.openOrActivate(model);
            Path second = service.openOrActivate(model.getParent().resolve("../source/design.archimate"));
            equal(first, second, "Equivalent physical paths identify one loaded model");
            equal(1, opens.get(), "An already-open model is not reloaded or duplicated");
            equal(1, activations.get(), "An already-open model is activated");
            expectThrows(IOException.class, () -> service.openOrActivate(root.resolve("missing.archimate")),
                    "Missing model is not reported as opened");

            Path planOne = root.resolve("D-09999/source/design.archimate");
            Path planTwo = root.resolve("D-10000/source/design.archimate");
            Files.createDirectories(planOne.getParent());
            Files.createDirectories(planTwo.getParent());
            Files.writeString(planOne, "same internal model IDs");
            Files.writeString(planTwo, "same internal model IDs");
            java.util.Set<Path> loadedPaths = new java.util.HashSet<>();
            AtomicInteger independentOpens = new AtomicInteger();
            AtomicInteger independentActivations = new AtomicInteger();
            ArchiModelService planService = new ArchiModelService(new ArchiModelService.NativeAccess() {
                @Override public Path findLoaded(Path normalizedPhysicalPath) {
                    return loadedPaths.stream().filter(normalizedPhysicalPath::equals).findFirst().orElse(null);
                }
                @Override public Path openAndActivate(Path normalizedPhysicalPath) {
                    independentOpens.incrementAndGet();
                    loadedPaths.add(normalizedPhysicalPath);
                    return normalizedPhysicalPath;
                }
                @Override public void activate(Path normalizedPhysicalPath) {
                    independentActivations.incrementAndGet();
                }
            });
            planService.openOrActivate(planOne);
            planService.openOrActivate(planTwo);
            planService.openOrActivate(planTwo);
            equal(2, independentOpens.get(),
                    "Different plan file paths may open independently despite identical model contents/IDs");
            equal(1, independentActivations.get(), "Reopening one plan activates only that physical file");
        } finally {
            deleteTree(root);
        }
    }

    private static void testGitCloneReuseAndLocalChanges() throws Exception {
        Path temp = Files.createTempDirectory("github-workspace-git-integration-");
        try {
            Path source = temp.resolve("source");
            Files.createDirectories(source.resolve("source"));
            runGit(List.of("git", "init", source.toString()), temp);
            runGit(List.of("git", "-C", source.toString(), "config", "user.name", "GitHub Workspace Test"), temp);
            runGit(List.of("git", "-C", source.toString(), "config", "user.email", "test@example.invalid"), temp);
            Files.writeString(source.resolve("README.md"), "fixture\n");
            Files.writeString(source.resolve("source/design.archimate"), "fixture model\n");
            runGit(List.of("git", "-C", source.toString(), "add", "--all"), temp);
            runGit(List.of("git", "-C", source.toString(), "commit", "-m", "fixture"), temp);

            Path bare = temp.resolve("origin.git");
            runGit(List.of("git", "clone", "--bare", source.toString(), bare.toString()), temp);
            Path destination = temp.resolve("workspace/github.com/repository-governance/SBB-009999");
            Files.createDirectories(destination.getParent());
            AtomicInteger cloneCommands = new AtomicInteger();
            List<String> requestedClone = new ArrayList<>();
            CommandRunner integrationRunner = new CommandRunner() {
                private final ProcessExecutor delegate = new ProcessExecutor();
                @Override public ProcessResult run(List<String> arguments, byte[] input, Duration timeout,
                        CancellationToken cancellation) throws IOException, InterruptedException {
                    return run(arguments, input, timeout, cancellation, ignored -> { });
                }
                @Override public ProcessResult run(List<String> arguments, byte[] input, Duration timeout,
                        CancellationToken cancellation, java.util.function.Consumer<String> progress)
                        throws IOException, InterruptedException {
                    if (arguments.size() >= 6 && arguments.get(0).equals("git")
                            && arguments.get(1).equals("clone")) {
                        cloneCommands.incrementAndGet();
                        requestedClone.addAll(arguments);
                        List<String> localClone = new ArrayList<>(arguments);
                        localClone.set(4, bare.toString());
                        try (ProcessResult result = delegate.run(localClone, input, timeout, cancellation, progress)) {
                            if (result.exitCode() != 0) return result;
                        }
                        runGit(List.of("git", "-C", arguments.get(5), "remote", "set-url", "origin",
                                "git@github.com:repository-governance/SBB-009999.git"), temp);
                        return new ProcessResult(0, new byte[0], new byte[0]);
                    }
                    return delegate.run(arguments, input, timeout, cancellation, progress);
                }
            };
            GitService git = new GitService(integrationRunner);
            String cloneUrl = "https://github.com/repository-governance/SBB-009999.git";
            Path cloned = git.cloneOrReuse(cloneUrl, "repository-governance", "SBB-009999", destination,
                    new CancellationToken(), ignored -> { });
            equal(destination.toRealPath(), cloned, "Successful local Git clone is validated");
            check(Files.isRegularFile(cloned.resolve("source/design.archimate")),
                    "Clone includes source/design.archimate");
            equal(List.of("git", "clone", "--progress", "--", cloneUrl), requestedClone.subList(0, 5),
                    "Clone uses Git argument vector and the GitHub clone_url without credentials");
            check(requestedClone.get(5).startsWith(destination.getParent().toString()),
                    "Transient clone staging is created inside the persistent workspace");
            check(!Path.of(requestedClone.get(5)).equals(destination),
                    "The persistent checkout is published only after a successful validated clone");
            check(GitService.sameGitHubRepository("https://github.com/repository-governance/SBB-009999.git",
                    "repository-governance", "SBB-009999"), "HTTPS origin is accepted");
            check(GitService.sameGitHubRepository("git@github.com:repository-governance/SBB-009999.git",
                    "repository-governance", "SBB-009999"), "Equivalent SSH origin is accepted");
            check(GitService.sameGitHubRepository("ssh://git@github.com/repository-governance/SBB-009999",
                    "repository-governance", "SBB-009999"), "ssh:// origin without .git is accepted");
            check(!GitService.sameGitHubRepository("https://evil.example/repository-governance/SBB-009999.git",
                    "repository-governance", "SBB-009999"), "Non-GitHub origin is rejected");

            Files.writeString(cloned.resolve("README.md"), "user's uncommitted change\n");
            Path reused = git.cloneOrReuse(cloneUrl, "repository-governance", "SBB-009999", destination,
                    new CancellationToken(), ignored -> { });
            equal(cloned, reused, "Existing valid checkout is reused");
            equal(1, cloneCommands.get(), "Second open does not execute git clone again");
            equal("user's uncommitted change\n", Files.readString(cloned.resolve("README.md")),
                    "Reusing a checkout preserves local edits");

            Path wrongRemote = temp.resolve("workspace/github.com/repository-governance/WRONG-REMOTE");
            Files.createDirectories(wrongRemote);
            runGit(List.of("git", "init", wrongRemote.toString()), temp);
            runGit(List.of("git", "-C", wrongRemote.toString(), "remote", "add", "origin",
                    "https://github.com/another-org/WRONG-REMOTE.git"), temp);
            expectThrows(IOException.class, () -> git.validateWorkingCopy("repository-governance",
                    "WRONG-REMOTE", wrongRemote, new CancellationToken()),
                    "Existing Git working copy with a mismatched origin is rejected");
            check(Files.isDirectory(wrongRemote.resolve(".git")),
                    "Mismatched Git working copy is preserved");

            Path unrelated = temp.resolve("workspace/github.com/repository-governance/OTHER");
            Files.createDirectories(unrelated);
            Path marker = unrelated.resolve("user-data.txt");
            Files.writeString(marker, "preserve");
            expectThrows(IOException.class, () -> git.cloneOrReuse(cloneUrl, "repository-governance", "SBB-009999",
                    unrelated, new CancellationToken(), ignored -> { }),
                    "Existing unrelated directory is rejected without replacement");
            equal("preserve", Files.readString(marker), "Existing unrelated files remain untouched");
            equal(1, cloneCommands.get(), "Invalid existing path is never cloned over");

            Path failedDestination = temp.resolve("workspace/github.com/repository-governance/FAILED");
            CommandRunner failedCloneRunner = new CommandRunner() {
                @Override public ProcessResult run(List<String> arguments, byte[] input, Duration timeout,
                        CancellationToken cancellation) {
                    return new ProcessResult(128, new byte[0],
                            "fatal: Authentication failed".getBytes(StandardCharsets.UTF_8));
                }
            };
            try {
                new GitService(failedCloneRunner).cloneOrReuse(
                        "https://github.com/repository-governance/FAILED.git", "repository-governance",
                        "FAILED", failedDestination, new CancellationToken(), ignored -> { });
                throw new AssertionError("Expected failed clone to be reported");
            } catch (IOException ex) {
                check(ex.getMessage().contains("authentication"), "Clone authentication failure is explained");
            }
            check(!Files.exists(failedDestination), "Failed clone is not published as the final checkout");
            try (var entries = Files.list(failedDestination.getParent())) {
                check(entries.anyMatch(path -> path.getFileName().toString().startsWith(".FAILED.clone-")),
                        "Incomplete staging data is preserved and is not mistaken for a checkout");
            }
        } finally {
            deleteTree(temp);
        }
    }

    private static void testPlanCloneBranchAndReuse() throws Exception {
        Path temp = Files.createTempDirectory("github-workspace-plan-integration-");
        try {
            Path source = temp.resolve("source");
            Files.createDirectories(source.resolve("source"));
            runGit(List.of("git", "init", "--initial-branch=trunk", source.toString()), temp);
            runGit(List.of("git", "-C", source.toString(), "config", "user.name", "GitHub Workspace Test"), temp);
            runGit(List.of("git", "-C", source.toString(), "config", "user.email", "test@example.invalid"), temp);
            Files.writeString(source.resolve("README.md"), "default branch\n");
            Files.writeString(source.resolve("source/design.archimate"), "fixture model\n");
            runGit(List.of("git", "-C", source.toString(), "add", "--all"), temp);
            runGit(List.of("git", "-C", source.toString(), "commit", "-m", "default fixture"), temp);
            runGit(List.of("git", "-C", source.toString(), "switch", "-c", "D-10000"), temp);
            Files.writeString(source.resolve("README.md"), "existing plan branch\n");
            runGit(List.of("git", "-C", source.toString(), "commit", "-am", "remote plan fixture"), temp);
            runGit(List.of("git", "-C", source.toString(), "switch", "trunk"), temp);
            Path bare = temp.resolve("origin.git");
            runGit(List.of("git", "clone", "--bare", source.toString(), bare.toString()), temp);

            String originUrl = "https://github.com/repository-governance/SBB-009999.git";
            GitHubRepository repository = repository("SBB-009999", originUrl, "trunk");
            WorkspaceService workspace = new WorkspaceService(temp.resolve("workspace"));
            GitService service = new GitService(new LocalPlanRunner(bare, originUrl, temp));

            Path newPlan = workspace.preparePlanDirectory(repository, "D-09999");
            Path created = service.clonePlanOrReuse(originUrl, "repository-governance", repository.getName(),
                    repository.getDefaultBranch(), "D-09999", newPlan, new CancellationToken(), ignored -> { });
            equal("D-09999", gitOutput(List.of("git", "-C", created.toString(), "branch", "--show-current"), temp),
                    "A new plan clones and creates the requested Plan ID branch");
            equal("default branch\n", Files.readString(created.resolve("README.md")),
                    "New plan starts from the repository's actual default branch");
            check(Files.isRegularFile(workspace.modelPath(created)),
                    "Plan checkout exposes source/design.archimate");
            expectGitFailure(List.of("git", "--git-dir", bare.toString(), "show-ref", "--verify", "--quiet",
                    "refs/heads/D-09999"), temp, "New Plan ID is not published to GitHub");

            Files.writeString(created.resolve("README.md"), "uncommitted local plan edit\n");
            Path reused = service.clonePlanOrReuse(originUrl, "repository-governance", repository.getName(),
                    "trunk", "D-09999", newPlan, new CancellationToken(), ignored -> { });
            equal(created.toRealPath(), reused, "Existing Plan ID working copy is reused");
            equal("uncommitted local plan edit\n", Files.readString(reused.resolve("README.md")),
                    "Reusing a plan preserves uncommitted local changes");

            Path remotePlan = workspace.preparePlanDirectory(repository, "D-10000");
            Path checkedOut = service.clonePlanOrReuse(originUrl, "repository-governance", repository.getName(),
                    "trunk", "D-10000", remotePlan, new CancellationToken(), ignored -> { });
            equal("D-10000", gitOutput(List.of("git", "-C", checkedOut.toString(),
                    "branch", "--show-current"), temp), "An existing remote Plan ID is checked out locally");
            equal("origin/D-10000", gitOutput(List.of("git", "-C", checkedOut.toString(),
                    "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}"), temp),
                    "Existing remote Plan ID tracks its origin branch");
            equal(List.of("D-09999", "D-10000"), service.findValidPlanCheckouts(repository, workspace,
                    new CancellationToken()).stream().map(path -> path.getFileName().toString()).sorted().toList(),
                    "Workspace scan discovers independent validated Plan ID directories");

            runGit(List.of("git", "-C", reused.toString(), "switch", "trunk"), temp);
            expectThrows(IOException.class, () -> service.validatePlanWorkingCopy("repository-governance",
                    repository.getName(), "D-09999", reused, new CancellationToken()),
                    "A local plan on a different active branch is rejected");
            equal("trunk", gitOutput(List.of("git", "-C", reused.toString(), "branch", "--show-current"), temp),
                    "Plan validation does not switch the user's existing branch");
        } finally {
            deleteTree(temp);
        }
    }

    private static void testThumbnailSelectionAndCache() throws Exception {
        assertions += org.cda.archi.githubworkspace.services.ThumbnailServiceTests.run();
    }

    private static GitHubRepository repository(String name, String cloneUrl) {
        return repository(name, cloneUrl, "main");
    }

    private static GitHubRepository repository(String name, String cloneUrl, String defaultBranch) {
        return new GitHubRepository(9001, name, "repository-governance/" + name, "", "private", true,
                List.of("archimate"), "https://github.com/repository-governance/" + name, cloneUrl,
                "git@github.com:repository-governance/" + name + ".git", defaultBranch, null);
    }

    private static void runGit(List<String> arguments, Path workingDirectory)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(arguments);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Test Git command timed out.");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Test Git command failed (output suppressed).");
        }
        Arrays.fill(output, (byte) 0);
    }

    private static String gitOutput(List<String> arguments, Path workingDirectory)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(arguments);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Test Git command timed out.");
        }
        if (process.exitValue() != 0) throw new IOException("Test Git command failed (output suppressed).");
        String result = new String(output, StandardCharsets.UTF_8).trim();
        Arrays.fill(output, (byte) 0);
        return result;
    }

    private static void expectGitFailure(List<String> arguments, Path workingDirectory, String message)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(arguments);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Test Git command timed out.");
        }
        boolean failed = process.exitValue() != 0;
        Arrays.fill(output, (byte) 0);
        check(failed, message);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void testProcessExecutorCapturesBothStreams() throws Exception {
        String javaExecutable = System.getProperty("java.home") + java.io.File.separator + "bin"
                + java.io.File.separator + "java";
        try (ProcessResult result = new ProcessExecutor().run(List.of(javaExecutable, "-version"),
                new byte[0], Duration.ofSeconds(10), new CancellationToken())) {
            equal(0, result.exitCode(), "ProcessBuilder executes an argument-vector process");
            String combined = new String(result.standardOutput(), StandardCharsets.UTF_8)
                    + new String(result.standardError(), StandardCharsets.UTF_8);
            check(combined.toLowerCase().contains("version"), "ProcessExecutor drains stdout and stderr");
        }
    }

    private static void testGitInspectionAndIdentity() {
        FakeRunner runner = new FakeRunner();
        GitConfiguration git = new GitService(runner).inspect(new CancellationToken());
        check(git.isAvailable(), "Git installation is detected");
        equal("git version 2.54.0", git.getVersion(), "Git version is read");
        equal("Configured Local Name", git.getUserName(), "Git user.name is read");
        equal("local@example.test", git.getUserEmail(), "Git user.email is read");
        check(git.hasCredentialHelper(), "Credential helper configuration is read");
        check(runner.commands.contains(List.of("git", "--version")), "Git is invoked as argument vector");
        check(runner.commands.contains(List.of("git", "config", "--get", "user.name")),
                "Git username config command is executed");
        check(runner.commands.contains(List.of("git", "config", "--get", "user.email")),
                "Git email config command is executed");
        check(runner.commands.contains(List.of("git", "config", "--get-all", "credential.helper")),
                "Git credential helper command is executed");
        check(runner.commands.contains(List.of("git", "config", "--get-all",
                "credential.https://github.com.helper")), "GitHub-scoped helper is detected");
        check(!git.getUserName().equals("github-user"), "Git identity is not conflated with GitHub identity");
    }

    private static void testCredentialProtocolAndSecretClearing() throws Exception {
        FakeRunner runner = new FakeRunner();
        byte[] helperOutput = "protocol=https\nhost=github.com\nusername=x-access-token\npassword=mock-token\n\n"
                .getBytes(StandardCharsets.UTF_8);
        runner.credentialOutput = helperOutput;
        GitCredentialService credentials = new GitCredentialService(new GitCredentialAdapter(runner));
        GitCredential credential = credentials.retrieve(gitConfiguration(), new CancellationToken());
        equal(List.of("git", "credential", "fill"), runner.lastCommand, "Uses git credential fill");
        equal("protocol=https\nhost=github.com\n\n",
                new String(runner.lastInput, StandardCharsets.UTF_8), "Sends exact Git credential protocol input");
        equal("x-access-token", credential.getUsername(), "Credential username is parsed");
        char[] password = credential.copyPassword();
        equal("mock-token", new String(password), "Credential password stays in memory for validation");
        check(credential.toString().contains("redacted") && !credential.toString().contains("mock-token"),
                "Credential string representation is redacted");
        Arrays.fill(password, '\0');
        credential.close();
        check(allZero(helperOutput), "Credential process output is cleared after parsing");
        check(!runner.lastInput.toString().contains("mock-token"), "Credential is not sent in process arguments/input");
    }

    private static void testCredentialTimeoutAndCancellation() {
        CommandRunner timeoutRunner = (args, input, timeout, cancellation) -> {
            throw new ProcessExecutionException("The process timed out.");
        };
        try {
            new GitCredentialService(new GitCredentialAdapter(timeoutRunner))
                    .retrieve(gitConfiguration(), new CancellationToken());
            throw new AssertionError("Expected timeout");
        } catch (IOException ex) {
            check(ex.getMessage().contains("timed out"), "Credential process timeout is surfaced safely");
        }

        CancellationToken token = new CancellationToken();
        token.cancel();
        CommandRunner cancelledRunner = (args, input, timeout, cancellation) -> {
            cancellation.throwIfCancelled();
            return new ProcessResult(1, new byte[0], new byte[0]);
        };
        try {
            new GitCredentialService(new GitCredentialAdapter(cancelledRunner))
                    .retrieve(gitConfiguration(), token);
            throw new AssertionError("Expected cancellation");
        } catch (IOException ex) {
            check(ex.getMessage().contains("cancelled"), "Credential cancellation is reported");
        } catch (CancellationException ex) {
            check(true, "Pre-cancelled operation remains cancellable");
        }
    }

    private static void testGitHubIdentityValidation() throws Exception {
        FakeRunner runner = credentialRunner("validated-token");
        FakeApi api = new FakeApi((uri, token, call) -> {
            equal("https://api.github.com/user", uri.toString(), "Validation uses GET /user endpoint");
            equal("validated-token", token, "Candidate credential is passed only to the GitHub API client");
            return response(200, "{\"id\":123,\"login\":\"github-workspace-user\",\"name\":\"EA User\","
                    + "\"html_url\":\"https://github.com/github-workspace-user\"}");
        });
        GitHubSession session = authenticate(runner, api);
        equal("github-workspace-user", session.getUser().getLogin(), "GitHub account comes from /user");
        equal("EA User", session.getUser().getName(), "GitHub display name comes from /user");
        check(!session.toString().contains("validated-token"), "Session does not expose its token in toString");
        session.close();
    }

    private static void testIncompatibleCredentialIsExplicit() throws Exception {
        FakeApi api = new FakeApi((uri, token, call) -> response(401, "{\"message\":\"Bad credentials\"}"));
        try {
            authenticate(credentialRunner("git-only-password"), api);
            throw new AssertionError("Expected Git REST credential incompatibility");
        } catch (GitHubApiException ex) {
            check(ex.getMessage().contains("GitHub rejected it as a REST API bearer token"),
                    "Git-compatible but REST-incompatible credential is reported clearly");
            check(!ex.getMessage().contains("git-only-password"), "Credential is absent from error message");
        }
    }

    private static void testRepositoryPaginationAndFields() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FakeApi api = new FakeApi((uri, token, call) -> {
            equal("search-token", token, "Search request uses the validated in-memory GitHub session");
            equal("api.github.com", uri.getHost(), "Authorization stays on GitHub API host");
            Map<String, String> parameters = query(uri);
            equal("org:repository-governance topic:archimate", parameters.get("q"), "Search qualifiers are correct");
            equal("100", parameters.get("per_page"), "Search requests maximum page size");
            int page = Integer.parseInt(parameters.get("page"));
            equal(call + 1, page, "Pagination advances sequentially");
            calls.incrementAndGet();
            if (page == 1) return response(200, pageBody(101, 100, 1));
            return response(200, pageBody(101, 1, 101));
        });
        GitHubSession session = authenticate(credentialRunner("search-token"),
                new FakeApi((uri, token, call) -> response(200,
                        "{\"id\":11,\"login\":\"account\",\"name\":\"\",\"html_url\":\"https://github.com/account\"}")));
        RepositorySearchResult result = new GitHubRepositoryService(api).search(session,
                new GitHubOrganization("repository-governance"), "archimate", new CancellationToken());
        equal(101, result.getRepositories().size(), "All pages are returned");
        equal(101L, result.getTotalCount(), "Search total is retained");
        equal(2, calls.get(), "Search paginates to second page");
        GitHubRepository first = result.getRepositories().get(0);
        equal(1L, first.getId(), "Repository id is deserialized");
        equal("repo-1", first.getName(), "Repository name is deserialized");
        equal("repository-governance/repo-1", first.getFullName(), "Full name is deserialized");
        equal("private", first.getVisibility(), "Visibility is deserialized");
        check(first.isPrivate(), "Private boolean is deserialized");
        equal(List.of("archimate"), first.getTopics(), "Topics are deserialized");
        equal("main", first.getDefaultBranch(), "Default branch is deserialized");
        equal("https://github.com/repository-governance/repo-1", first.getHtmlUrl(), "Browser URL is deserialized");
        equal("https://github.com/repository-governance/repo-1.git", first.getCloneUrl(), "Clone URL is deserialized");
        equal("git@github.com:repository-governance/repo-1.git", first.getSshUrl(), "SSH URL is deserialized");
        check(first.getUpdatedAt() != null, "Updated timestamp is deserialized");
        session.close();
    }

    private static void testIncompleteAndOverLimitResultsAreRejected() throws Exception {
        GitHubSession session = authenticate(credentialRunner("search-token"),
                new FakeApi((uri, token, call) -> response(200,
                        "{\"id\":11,\"login\":\"account\",\"name\":\"\",\"html_url\":\"https://github.com/account\"}")));
        GitHubRepositoryService incompleteService = new GitHubRepositoryService(new FakeApi(
                (uri, token, call) -> response(200,
                        "{\"total_count\":1,\"incomplete_results\":true,\"items\":[]}")));
        expectThrows(IncompleteSearchException.class,
                () -> incompleteService.search(session, new GitHubOrganization("org"), "archimate",
                        new CancellationToken()), "Incomplete results are rejected");

        GitHubRepositoryService overLimitService = new GitHubRepositoryService(new FakeApi(
                (uri, token, call) -> response(200,
                        "{\"total_count\":1001,\"incomplete_results\":false,\"items\":[]}")));
        try {
            overLimitService.search(session, new GitHubOrganization("org"), "archimate", new CancellationToken());
            throw new AssertionError("Expected 1000-result limit");
        } catch (IncompleteSearchException ex) {
            check(ex.getMessage().contains("1,000"), "Search over 1,000 is explicitly incomplete");
        }
        session.close();
    }

    private static void testEmptySearchAndAuthorizationErrors() throws Exception {
        GitHubSession session = authenticate(credentialRunner("search-token"),
                new FakeApi((uri, token, call) -> response(200,
                        "{\"id\":11,\"login\":\"account\",\"name\":\"\",\"html_url\":\"https://github.com/account\"}")));
        RepositorySearchResult empty = new GitHubRepositoryService(new FakeApi(
                (uri, token, call) -> response(200, "{\"total_count\":0,\"incomplete_results\":false,\"items\":[]}")))
                .search(session, new GitHubOrganization("org"), "archimate", new CancellationToken());
        check(empty.isEmpty(), "Successful no-match search remains an empty success");

        for (int status : new int[] {403, 404, 422, 429}) {
            GitHubRepositoryService service = new GitHubRepositoryService(new FakeApi(
                    (uri, token, call) -> response(status,
                            "{\"message\":\"API error\"}", status == 429 ? Map.of("Retry-After", "30") : Map.of())));
            try {
                service.search(session, new GitHubOrganization("org"), "archimate", new CancellationToken());
                throw new AssertionError("Expected HTTP " + status);
            } catch (GitHubApiException ex) {
                equal(status, ex.getStatusCode(), "HTTP " + status + " is distinguished");
                check(!ex.getMessage().isBlank(), "HTTP " + status + " has user-facing explanation");
            }
        }
        session.close();
    }

    private static void testQueryValidationAndAuthorizationHostRestriction() throws Exception {
        expectThrows(IllegalArgumentException.class, () -> new GitHubOrganization("org query"),
                "Organization qualifier injection is rejected");
        expectThrows(IllegalArgumentException.class, () -> GitHubRepositoryService.validateTopic("archimate topic"),
                "Topic qualifier injection is rejected");
        RestClient noNetworkClient = new RestClient(null);
        expectThrows(IllegalArgumentException.class,
                () -> noNetworkClient.get(URI.create("https://evil.example/data"), "secret",
                        new CancellationToken()), "Authorization to non-GitHub host is rejected");
    }

    private static void testJsonValidation() {
        Map<?, ?> parsed = (Map<?, ?>) JsonParser.parse("{\"key\":[true,null,3],\"text\":\"a\\nb\"}");
        equal("a\nb", parsed.get("text"), "JSON escapes are parsed");
        expectThrows(IllegalArgumentException.class, () -> JsonParser.parse("{\"x\":undefined}"),
                "Invalid JSON token is rejected");
        expectThrows(IllegalArgumentException.class, () -> JsonParser.parse("{\"x\":1,}"),
                "Trailing comma is rejected");
    }

    private static GitHubSession authenticate(FakeRunner runner, FakeApi api) throws Exception {
        return new GitHubAuthenticationService(new GitCredentialService(new GitCredentialAdapter(runner)), api)
                .authenticate(gitConfiguration(), new CancellationToken());
    }

    private static FakeRunner credentialRunner(String token) {
        FakeRunner runner = new FakeRunner();
        runner.credentialOutput = ("username=x-access-token\npassword=" + token + "\n")
                .getBytes(StandardCharsets.UTF_8);
        return runner;
    }

    private static GitConfiguration gitConfiguration() {
        return new GitConfiguration(true, "git version test", "local user", "local@example.test",
                true, "Ready");
    }

    private static RestResponse response(int status, String body) {
        return response(status, body, Map.of());
    }

    private static RestResponse response(int status, String body, Map<String, String> headers) {
        java.util.Map<String, List<String>> values = new java.util.LinkedHashMap<>();
        headers.forEach((name, value) -> values.put(name, List.of(value)));
        return new RestResponse(status, body, java.net.http.HttpHeaders.of(values, (name, value) -> true));
    }

    private static String pageBody(int total, int count, int firstId) {
        StringBuilder json = new StringBuilder("{\"total_count\":").append(total)
                .append(",\"incomplete_results\":false,\"items\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) json.append(',');
            int id = firstId + index;
            json.append("{\"id\":").append(id)
                    .append(",\"name\":\"repo-").append(id)
                    .append("\",\"full_name\":\"repository-governance/repo-").append(id)
                    .append("\",\"description\":null,\"visibility\":\"private\",\"private\":true")
                    .append(",\"topics\":[\"archimate\"],\"html_url\":\"https://github.com/repository-governance/repo-")
                    .append(id).append("\",\"clone_url\":\"https://github.com/repository-governance/repo-")
                    .append(id).append(".git\",\"ssh_url\":\"git@github.com:repository-governance/repo-")
                    .append(id).append(".git\",\"default_branch\":\"main\",\"updated_at\":\"2026-11-20T10:15:30Z\"}");
        }
        return json.append("]}").toString();
    }

    private static Map<String, String> query(URI uri) {
        java.util.LinkedHashMap<String, String> parameters = new java.util.LinkedHashMap<>();
        for (String part : uri.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            parameters.put(java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
        }
        return parameters;
    }

    private static boolean allZero(byte[] value) {
        for (byte element : value) if (element != 0) return false;
        return true;
    }

    private static void expectThrows(Class<? extends Throwable> type, ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (Throwable ex) {
            check(type.isInstance(ex), message + " (got " + ex.getClass().getSimpleName() + ")");
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        check(java.util.Objects.equals(expected, actual),
                message + " (expected " + expected + ", got " + actual + ")");
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }

    private static final class LocalPlanRunner implements CommandRunner {
        private final ProcessExecutor delegate = new ProcessExecutor();
        private final Path bare;
        private final String githubOrigin;
        private final Path workingDirectory;

        private LocalPlanRunner(Path bare, String githubOrigin, Path workingDirectory) {
            this.bare = bare;
            this.githubOrigin = githubOrigin;
            this.workingDirectory = workingDirectory;
        }

        @Override
        public ProcessResult run(List<String> arguments, byte[] input, Duration timeout,
                CancellationToken cancellation) throws IOException, InterruptedException {
            return run(arguments, input, timeout, cancellation, ignored -> { });
        }

        @Override
        public ProcessResult run(List<String> arguments, byte[] input, Duration timeout,
                CancellationToken cancellation, java.util.function.Consumer<String> progress)
                throws IOException, InterruptedException {
            if (arguments.size() >= 6 && arguments.get(0).equals("git") && arguments.get(1).equals("clone")) {
                List<String> local = new ArrayList<>(arguments);
                local.set(4, bare.toString());
                ProcessResult result = delegate.run(local, input, timeout, cancellation, progress);
                if (result.exitCode() == 0) {
                    runGit(List.of("git", "-C", arguments.get(5), "remote", "set-url", "origin", githubOrigin),
                            workingDirectory);
                }
                return result;
            }
            if (arguments.size() >= 5 && arguments.get(0).equals("git") && arguments.get(1).equals("-C")
                    && (arguments.get(3).equals("ls-remote") || arguments.get(3).equals("fetch"))) {
                Path checkout = Path.of(arguments.get(2));
                runGit(List.of("git", "-C", checkout.toString(), "remote", "set-url", "origin", bare.toString()),
                        workingDirectory);
                try {
                    return delegate.run(arguments, input, timeout, cancellation, progress);
                } finally {
                    runGit(List.of("git", "-C", checkout.toString(), "remote", "set-url", "origin", githubOrigin),
                            workingDirectory);
                }
            }
            return delegate.run(arguments, input, timeout, cancellation, progress);
        }
    }

    private static final class FakeRunner implements CommandRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private List<String> lastCommand = List.of();
        private byte[] lastInput = new byte[0];
        private byte[] credentialOutput = new byte[0];

        @Override
        public ProcessResult run(List<String> arguments, byte[] input, Duration timeout,
                CancellationToken cancellation) throws IOException {
            cancellation.throwIfCancelled();
            lastCommand = List.copyOf(arguments);
            lastInput = input.clone();
            commands.add(lastCommand);
            String output = switch (String.join(" ", arguments)) {
                case "git --version" -> "git version 2.54.0\n";
                case "git config --get user.name" -> "Configured Local Name\n";
                case "git config --get user.email" -> "local@example.test\n";
                case "git config --get-all credential.helper" -> "manager\n";
                case "git config --get-all credential.https://github.com.helper" -> "";
                case "git credential fill" -> null;
                default -> "";
            };
            byte[] stdout = output == null ? credentialOutput : output.getBytes(StandardCharsets.UTF_8);
            return new ProcessResult(0, stdout, new byte[0]);
        }
    }

    @FunctionalInterface private interface ApiResponse {
        RestResponse respond(URI uri, String token, int call);
    }

    private static final class FakeApi implements GitHubApiClient {
        private final ApiResponse response;
        private int calls;

        private FakeApi(ApiResponse response) { this.response = response; }

        @Override
        public RestResponse get(URI uri, String token, CancellationToken cancellation) {
            cancellation.throwIfCancelled();
            return response.respond(uri, token, calls++);
        }
    }
}
