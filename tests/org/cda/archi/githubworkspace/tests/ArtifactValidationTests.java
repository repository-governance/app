package org.cda.archi.githubworkspace.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

/** Verifies the generated OSGi bundle and official Archi installer layout. */
public final class ArtifactValidationTests {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        String version = Files.readString(root.resolve("VERSION")).trim();
        Path jarPath = root.resolve("dist/github-workspace-" + version + ".jar");
        Path pluginPath = root.resolve("dist/github-workspace-" + version + ".archiplugin");
        require(Files.isRegularFile(jarPath), "OSGi bundle JAR exists");
        require(Files.isRegularFile(pluginPath), ".archiplugin exists");

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var manifest = jar.getManifest();
            require(manifest != null, "OSGi manifest exists");
            var attributes = manifest.getMainAttributes();
            require(attributes.getValue("Bundle-SymbolicName").startsWith("org.cda.archi.githubworkspace"),
                    "Bundle symbolic name matches");
            require(version.equals(attributes.getValue("Bundle-Version")), "Bundle version matches VERSION");
            String requiredBundles = attributes.getValue("Require-Bundle");
            require(requiredBundles.contains("com.archimatetool.editor")
                    && requiredBundles.contains("com.archimatetool.model"),
                    "Native Archi model APIs are declared as bundle dependencies");
            for (String entry : new String[] {
                    "org/cda/archi/githubworkspace/controllers/GitHubWorkspaceController.class",
                    "org/cda/archi/githubworkspace/controllers/AuthenticationController.class",
                    "org/cda/archi/githubworkspace/controllers/RepositoryController.class",
                    "org/cda/archi/githubworkspace/views/RepositoryBrowserView.class",
                    "org/cda/archi/githubworkspace/views/CloneRepositoryDialog.class",
                    "org/cda/archi/githubworkspace/views/GitHubWorkspacePreferencePage.class",
                    "org/cda/archi/githubworkspace/services/GitService.class",
                    "org/cda/archi/githubworkspace/services/GitCredentialService.class",
                    "org/cda/archi/githubworkspace/services/GitHubAuthenticationService.class",
                    "org/cda/archi/githubworkspace/services/GitHubRepositoryService.class",
                    "org/cda/archi/githubworkspace/services/WorkspaceService.class",
                    "org/cda/archi/githubworkspace/services/PreferencesService.class",
                    "org/cda/archi/githubworkspace/services/ArchiModelService.class",
                    "org/cda/archi/githubworkspace/infrastructure/ProcessExecutor.class",
                    "org/cda/archi/githubworkspace/infrastructure/GitCredentialAdapter.class",
                    "plugin.xml" }) {
                require(jar.getEntry(entry) != null, "Bundle contains " + entry);
            }
            String xml = new String(jar.getInputStream(jar.getEntry("plugin.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            require(xml.contains("org.eclipse.ui.commands") && xml.contains("org.eclipse.ui.handlers")
                    && xml.contains("org.eclipse.ui.menus") && xml.contains("org.eclipse.ui.preferencePages"),
                    "Archi/Eclipse extension points are valid");
            require(xml.contains("GitHubWorkspaceController") && xml.contains("Browse Repositories...")
                    && xml.contains("menu:org.eclipse.ui.main.menu/file?after=com.archimatetool.editor.action.openModel"),
                    "File menu command points to the native repository browser");
            require(!xml.contains("Governance"), "Independent Governance menu is absent");
        }

        try (ZipFile archive = new ZipFile(pluginPath.toFile())) {
            require(archive.getEntry("archi-plugin") != null, "Official Archi marker exists");
            require(archive.getEntry("github-workspace-" + version + ".jar") != null, "Bundle JAR is packaged");
        }
        System.out.println("PASS: bundle manifest, classes, plugin.xml, installer layout, and version");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
