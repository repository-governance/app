package org.cda.archi.githubworkspace.services;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import java.io.IOException;
import java.nio.file.Path;

/** Stores GitHub Workspace settings only; authentication secrets remain in Git Credential Manager. */
public final class PreferencesService {
    private static final String NODE = "org.cda.archi.githubworkspace";
    private static final String ORGANIZATION = "github.organization";
    private static final String TEMPLATE_REPOSITORY = "github.templateRepository";
    private static final String DEFAULT_WORKSPACE = "workspace.useDefault";
    private static final String CUSTOM_WORKSPACE = "workspace.location";
    private static final String LAST_ACCOUNT = "authentication.lastAccount";
    public static final String DEFAULT_ORGANIZATION = "repository-governance";
    public static final String DEFAULT_TOPIC = "archimate";
    public static final String DEFAULT_TEMPLATE_REPOSITORY = "https://github.com/repository-governance/templates";

    private final IEclipsePreferences preferences;

    public PreferencesService() {
        this(InstanceScope.INSTANCE.getNode(NODE));
    }

    public PreferencesService(IEclipsePreferences preferences) {
        this.preferences = preferences;
    }

    public String getOrganization() { return preferences.get(ORGANIZATION, DEFAULT_ORGANIZATION); }
    public String getOrganizationUrl() { return new org.cda.archi.githubworkspace.models.GitHubOrganization(
            getOrganization()).getUrl(); }
    public String getTopic() { return DEFAULT_TOPIC; }
    public String getTemplateRepository() {
        return preferences.get(TEMPLATE_REPOSITORY, DEFAULT_TEMPLATE_REPOSITORY);
    }
    public boolean useDefaultWorkspace() { return preferences.getBoolean(DEFAULT_WORKSPACE, true); }
    public String getCustomWorkspaceLocation() { return preferences.get(CUSTOM_WORKSPACE, ""); }
    public String getLastAccount() { return preferences.get(LAST_ACCOUNT, ""); }

    public Path defaultWorkspace() {
        return defaultWorkspace(Path.of(System.getProperty("user.home")));
    }

    public static Path defaultWorkspace(Path userHome) {
        return userHome.resolve(".github-workspace").resolve("workspace").toAbsolutePath().normalize();
    }

    public Path getWorkspaceRoot() {
        if (useDefaultWorkspace()) return defaultWorkspace();
        String custom = getCustomWorkspaceLocation().trim();
        return custom.isEmpty() ? defaultWorkspace() : Path.of(custom).toAbsolutePath().normalize();
    }

    public void saveSearch(String organization) throws org.osgi.service.prefs.BackingStoreException {
        String login = new org.cda.archi.githubworkspace.models.GitHubOrganization(organization).getLogin();
        preferences.put(ORGANIZATION, login);
        preferences.flush();
    }

    public void saveTemplateRepository(String repository) throws org.osgi.service.prefs.BackingStoreException {
        preferences.put(TEMPLATE_REPOSITORY, repository == null ? "" : repository.trim());
        preferences.flush();
    }

    public void saveWorkspace(boolean useDefault, String customLocation)
            throws org.osgi.service.prefs.BackingStoreException {
        preferences.putBoolean(DEFAULT_WORKSPACE, useDefault);
        preferences.put(CUSTOM_WORKSPACE, customLocation == null ? "" : customLocation.trim());
        preferences.flush();
    }

    public void saveLastAccount(String login) throws org.osgi.service.prefs.BackingStoreException {
        preferences.put(LAST_ACCOUNT, login == null ? "" : login);
        preferences.flush();
    }

    public static void validateWorkspaceLocation(String location) throws IOException {
        if (location == null || location.isBlank()) throw new IOException("Choose a workspace location.");
        Path path;
        try {
            path = Path.of(location).toAbsolutePath().normalize();
            Path current = path.getRoot();
            for (Path segment : path) {
                current = current.resolve(segment);
                if (java.nio.file.Files.exists(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    java.nio.file.attribute.BasicFileAttributes attributes = java.nio.file.Files.readAttributes(
                            current, java.nio.file.attribute.BasicFileAttributes.class,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    if (attributes.isSymbolicLink() || attributes.isOther()) {
                        throw new IOException("Symbolic links and redirected workspace paths are not allowed.");
                    }
                    if (!attributes.isDirectory()) throw new IOException("A workspace path component is not a directory.");
                } else {
                    try {
                        java.nio.file.Files.createDirectory(current);
                    } catch (java.nio.file.FileAlreadyExistsException race) {
                        if (java.nio.file.Files.isSymbolicLink(current)) {
                            throw new IOException("Symbolic links and redirected workspace paths are not allowed.");
                        }
                    }
                }
            }
            if (!java.nio.file.Files.isDirectory(path) || !java.nio.file.Files.isWritable(path)) {
                throw new IOException("The workspace location is not a writable directory.");
            }
            Path probe = java.nio.file.Files.createTempFile(path, ".github-workspace-write-test-", ".tmp");
            java.nio.file.Files.delete(probe);
        } catch (java.nio.file.InvalidPathException ex) {
            throw new IOException("The workspace location is not a valid path.");
        } catch (SecurityException ex) {
            throw new IOException("The workspace location cannot be accessed.");
        }
    }

    public void restoreDefaults() throws org.osgi.service.prefs.BackingStoreException {
        preferences.put(ORGANIZATION, DEFAULT_ORGANIZATION);
        preferences.put(TEMPLATE_REPOSITORY, DEFAULT_TEMPLATE_REPOSITORY);
        preferences.putBoolean(DEFAULT_WORKSPACE, true);
        preferences.put(CUSTOM_WORKSPACE, "");
        preferences.flush();
    }
}
