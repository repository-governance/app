package org.cda.archi.governance.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.cda.archi.governance.models.GitHubRepository;

/** Resolves persistent, per-user Git workspaces and rejects paths redirected outside them. */
public final class WorkspaceService {
    public static final String GITHUB_HOST = "github.com";

    public Path workspaceRoot() {
        return Path.of(System.getProperty("user.home"), ".governance", "workspace")
                .toAbsolutePath().normalize();
    }

    public Path repositoryPath(String organization, GitHubRepository repository) throws IOException {
        validateOrganization(organization);
        validateRepositoryName(repository.getName());
        String[] identity = repository.getFullName().split("/", -1);
        if (identity.length != 2 || !identity[0].equalsIgnoreCase(organization)
                || !identity[1].equalsIgnoreCase(repository.getName())) {
            throw new IOException("The selected repository does not match the configured organization.");
        }
        return workspaceRoot().resolve(GITHUB_HOST).resolve(identity[0]).resolve(identity[1]).normalize();
    }

    public Path prepareRepositoryDirectory(String organization, GitHubRepository repository) throws IOException {
        Path root = workspaceRoot();
        Path destination = repositoryPath(organization, repository);
        if (!destination.startsWith(root)) throw new IOException("The repository path is outside the Governance workspace.");
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        Path governance = home.resolve(".governance");
        Path workspace = governance.resolve("workspace");
        ensureDirectory(governance, home, null);
        ensureDirectory(workspace, home, null);
        Path actualRoot = workspace.toRealPath();
        ensureDirectory(workspace.resolve(GITHUB_HOST), home, actualRoot);
        ensureDirectory(workspace.resolve(GITHUB_HOST).resolve(organization), home, actualRoot);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            rejectLink(destination);
            Path realDestination = destination.toRealPath();
            if (!realDestination.startsWith(actualRoot)) {
                throw new IOException("The repository path resolves outside the Governance workspace.");
            }
        } else {
            verifyWithinWorkspace(destination.getParent(), actualRoot);
        }
        return destination;
    }

    public void verifyRepositoryPath(Path repositoryDirectory) throws IOException {
        Path root = workspaceRoot().toRealPath();
        Path normalized = repositoryDirectory.toAbsolutePath().normalize();
        if (!normalized.startsWith(workspaceRoot())) {
            throw new IOException("The repository path is outside the Governance workspace.");
        }
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            rejectLink(normalized);
            verifyWithinWorkspace(normalized, root);
        } else {
            verifyWithinWorkspace(normalized.getParent(), root);
        }
    }

    public Path modelPath(Path repositoryDirectory) throws IOException {
        Path root = repositoryDirectory.toRealPath();
        Path model = repositoryDirectory.resolve("source").resolve("design.archimate").normalize();
        if (!model.startsWith(repositoryDirectory.normalize())) {
            throw new IOException("The ArchiMate model path escapes the local repository.");
        }
        Path source = repositoryDirectory.resolve("source");
        rejectLink(source);
        rejectLink(model);
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(model, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The repository does not contain source/design.archimate.");
        }
        Path realModel = model.toRealPath();
        if (!realModel.startsWith(root)) throw new IOException("The ArchiMate model resolves outside the repository.");
        return realModel;
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
            throw new IllegalArgumentException("The GitHub repository name cannot be used as a local directory.");
        }
    }

    public static void validateCloneUrl(GitHubRepository repository, String organization) throws IOException {
        validateCloneUrl(repository.getCloneUrl(), repository.getName(), organization);
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

    private static void ensureDirectory(Path directory, Path boundary, Path workspaceBoundary) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!normalized.startsWith(boundary)) throw new IOException("Workspace path escapes the user profile.");
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
        Path real = normalized.toRealPath();
        Path realBoundary = boundary.toRealPath();
        if (!real.startsWith(realBoundary) || (workspaceBoundary != null && !real.startsWith(workspaceBoundary))) {
            throw new IOException("A workspace path resolves outside the permitted directory.");
        }
    }

    private static void rejectLink(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Symbolic links and redirected workspace paths are not allowed.");
        }
    }

    private static void verifyWithinWorkspace(Path path, Path realWorkspace) throws IOException {
        Path real = path.toRealPath();
        if (!real.startsWith(realWorkspace)) {
            throw new IOException("A repository path resolves outside the Governance workspace.");
        }
    }
}
