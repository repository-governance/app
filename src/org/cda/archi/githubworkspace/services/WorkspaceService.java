package org.cda.archi.githubworkspace.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;

import org.cda.archi.githubworkspace.models.GitHubRepository;

/** Resolves safe repository/plan workspaces without moving or deleting existing files. */
public final class WorkspaceService {
    public static final String GITHUB_HOST = "github.com";
    private final PreferencesService preferences;
    private final Path fixedRoot;

    public WorkspaceService() {
        this(new PreferencesService());
    }

    public WorkspaceService(PreferencesService preferences) {
        this.preferences = preferences;
        this.fixedRoot = null;
    }

    public WorkspaceService(Path workspaceRoot) {
        this.preferences = null;
        this.fixedRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    public Path workspaceRoot() {
        return (fixedRoot == null ? preferences.getWorkspaceRoot() : fixedRoot).toAbsolutePath().normalize();
    }

    public Path repositoryPath(GitHubRepository repository) throws IOException {
        validateRepositoryIdentity(repository);
        Path root = workspaceRoot();
        Path path = root.resolve(GITHUB_HOST).resolve(repositoryOrganization(repository))
                .resolve(repository.getName()).normalize();
        if (!path.startsWith(root)) throw new IOException("The repository path escapes the workspace.");
        return path;
    }

    public Path planPath(GitHubRepository repository, String planId) throws IOException {
        validatePlanIdSyntax(planId);
        Path repositoryPath = repositoryPath(repository);
        Path plan = repositoryPath.resolve(planId).normalize();
        if (!plan.startsWith(repositoryPath)) throw new IOException("The plan path escapes its repository workspace.");
        return plan;
    }

    public Path preparePlanDirectory(GitHubRepository repository, String planId) throws IOException {
        Path root = workspaceRoot();
        ensureSafeDirectory(root);
        Path repositoryDirectory = repositoryPath(repository);
        ensureSafeDirectory(repositoryDirectory.getParent().getParent().getParent());
        ensureSafeDirectory(repositoryDirectory.getParent().getParent());
        ensureSafeDirectory(repositoryDirectory.getParent());
        ensureSafeDirectory(repositoryDirectory);
        validatePlanIdSyntax(planId);
        Path plan = repositoryDirectory.resolve(planId).normalize();
        if (!plan.startsWith(repositoryDirectory)) throw new IOException("The plan path escapes its repository workspace.");
        if (Files.exists(plan, LinkOption.NOFOLLOW_LINKS)) {
            rejectLink(plan);
            ensureContained(root, plan);
        }
        return plan;
    }

    /**
     * Scans only immediate plan folders. Callers validate each folder's Git root/origin/branch
     * asynchronously before presenting it as an openable local plan.
     */
    public List<Path> planDirectories(GitHubRepository repository) throws IOException {
        Path repositoryPath = repositoryPath(repository);
        if (!Files.exists(repositoryPath, LinkOption.NOFOLLOW_LINKS)) return List.of();
        rejectLink(repositoryPath);
        ensureContained(workspaceRoot(), repositoryPath);
        if (!Files.isDirectory(repositoryPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The repository workspace is not a directory.");
        }
        try (var children = Files.list(repositoryPath)) {
            List<Path> plans = new java.util.ArrayList<>();
            for (Path child : children.toList()) {
                if (!child.getFileName().toString().matches("D-[0-9]+")) continue;
                rejectLink(child);
                if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("A local Plan ID path is not a directory.");
                }
                ensureContained(workspaceRoot(), child);
                plans.add(child);
            }
            plans.sort(Comparator.comparing(path -> path.getFileName().toString()));
            return List.copyOf(plans);
        }
    }

    public Path modelPath(Path planDirectory) throws IOException {
        return modelPathWithin(planDirectory);
    }

    public static Path modelPathWithin(Path planDirectory) throws IOException {
        Path root = planDirectory.toRealPath();
        Path model = planDirectory.resolve("source").resolve("design.archimate").normalize();
        if (!model.startsWith(planDirectory.toAbsolutePath().normalize())) {
            throw new IOException("The ArchiMate model path escapes the plan workspace.");
        }
        Path source = planDirectory.resolve("source");
        rejectLink(source);
        rejectLink(model);
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(model, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The repository does not contain source/design.archimate.");
        }
        Path realModel = model.toRealPath();
        if (!realModel.startsWith(root)) throw new IOException("The ArchiMate model resolves outside the plan workspace.");
        return realModel;
    }

    public void validateDestination(Path planDirectory) throws IOException {
        Path root = workspaceRoot();
        Path normalized = planDirectory.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new IOException("The plan path is outside the configured workspace.");
        ensureSafeDirectory(root);
        Path existing = normalized;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing == null) throw new IOException("The plan destination has no existing parent.");
        rejectLink(existing);
        ensureContained(root, existing);
    }

    public boolean hasLegacyGovernanceWorkspace() {
        Path legacy = Path.of(System.getProperty("user.home"), ".governance", "workspace");
        return Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS);
    }

    public static String repositoryOrganization(GitHubRepository repository) throws IOException {
        validateRepositoryIdentity(repository);
        return repository.getFullName().substring(0, repository.getFullName().indexOf('/'));
    }

    public static void validateRepositoryIdentity(GitHubRepository repository) throws IOException {
        if (repository == null) throw new IOException("Select a GitHub repository.");
        String[] identity = repository.getFullName().split("/", -1);
        if (identity.length != 2 || !identity[1].equalsIgnoreCase(repository.getName())) {
            throw new IOException("The selected repository has an invalid GitHub identity.");
        }
        validateOrganization(identity[0]);
        validateRepositoryName(repository.getName());
    }

    public static void validateOrganization(String organization) {
        if (organization == null || !organization.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")) {
            throw new IllegalArgumentException("Enter a valid GitHub organization name.");
        }
    }

    public static void validateRepositoryName(String name) {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..")
                || name.endsWith(".") || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")
                || !name.matches("[A-Za-z0-9_.-]{1,100}")) {
            throw new IllegalArgumentException(
                    "Enter a valid repository name using letters, numbers, dots, underscores, or hyphens.");
        }
    }

    /** Converts a human-entered repository title into a URL-safe GitHub repository name. */
    public static String normalizeRepositoryName(String value) {
        if (value == null) return "";
        return value.strip().replaceAll("\\s+", "-");
    }

    public static String buildingBlockRepositoryName(String code, String type, String title) {
        validateBuildingBlockCode(code);
        validateBuildingBlockType(type);
        validateBuildingBlockTitle(title);
        String titleSlug = Normalizer.normalize(title.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", "-");
        String repositoryName = code + "-" + type + "-" + titleSlug;
        validateRepositoryName(repositoryName);
        return repositoryName;
    }

    public static void validateBuildingBlockCode(String code) {
        if (code == null || !code.matches("SBB-[0-9]{7}")) {
            throw new IllegalArgumentException("Code must use the format SBB-0099999.");
        }
    }

    public static void validateBuildingBlockType(String type) {
        if (!"AM".equals(type) && !"SD".equals(type)) {
            throw new IllegalArgumentException("Choose AM - Application Module or SD - Service Domain.");
        }
    }

    public static void validateBuildingBlockTitle(String title) {
        if (!isValidBuildingBlockTitle(title)) {
            throw new IllegalArgumentException("Title can contain letters and spaces only.");
        }
    }

    public static boolean isValidBuildingBlockTitle(String title) {
        return title != null && title.strip().matches("[\\p{L}\\p{M}]+(?: +[\\p{L}\\p{M}]+)*");
    }

    public static void validatePlanIdSyntax(String planId) {
        if (planId == null || !planId.matches("D-[0-9]+")) {
            throw new IllegalArgumentException("Plan ID must match D-[0-9]+ (for example, D-09999).");
        }
    }

    public static void validateCloneUrl(GitHubRepository repository) throws IOException {
        validateRepositoryIdentity(repository);
        validateCloneUrl(repository.getCloneUrl(), repository.getName(), repositoryOrganization(repository));
    }

    public static void validateCloneUrl(String cloneUrl, String repositoryName, String organization)
            throws IOException {
        validateOrganization(organization);
        validateRepositoryName(repositoryName);
        if (cloneUrl == null) throw new IOException("GitHub returned an invalid HTTPS clone URL.");
        java.net.URI uri;
        try {
            uri = java.net.URI.create(cloneUrl);
        } catch (IllegalArgumentException ex) {
            throw new IOException("GitHub returned an invalid HTTPS clone URL.");
        }
        String expectedPath = "/" + organization + "/" + repositoryName + ".git";
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !GITHUB_HOST.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null
                || uri.getFragment() != null || !expectedPath.equalsIgnoreCase(uri.getPath())) {
            throw new IOException("The selected repository clone URL does not match its GitHub identity.");
        }
    }

    private static void ensureSafeDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        Path current = normalized.getRoot();
        for (Path segment : normalized) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                rejectLink(current);
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("A workspace path component is not a directory.");
                }
            } else {
                try {
                    Files.createDirectory(current);
                } catch (java.nio.file.FileAlreadyExistsException race) {
                    rejectLink(current);
                }
            }
        }
        ensureContained(normalized, normalized);
    }

    private static void ensureContained(Path root, Path path) throws IOException {
        Path realRoot = Files.exists(root) ? root.toRealPath() : root.toAbsolutePath().normalize();
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(realRoot)) throw new IOException("A workspace path resolves outside its root.");
    }

    private static void rejectLink(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Symbolic links and redirected workspace paths are not allowed.");
        }
    }
}
