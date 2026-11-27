package org.cda.archi.governance.tests;

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
        Path jarPath = root.resolve("dist/governance-" + version + ".jar");
        Path pluginPath = root.resolve("dist/governance-" + version + ".archiplugin");
        require(Files.isRegularFile(jarPath), "OSGi bundle JAR exists");
        require(Files.isRegularFile(pluginPath), ".archiplugin exists");

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var manifest = jar.getManifest();
            require(manifest != null, "OSGi manifest exists");
            var attributes = manifest.getMainAttributes();
            require(attributes.getValue("Bundle-SymbolicName").startsWith("org.cda.archi.governance"),
                    "Bundle symbolic name matches");
            require(version.equals(attributes.getValue("Bundle-Version")), "Bundle version matches VERSION");
            for (String entry : new String[] {
                    "org/cda/archi/governance/controllers/GovernanceController.class",
                    "org/cda/archi/governance/controllers/AuthenticationController.class",
                    "org/cda/archi/governance/controllers/RepositoryController.class",
                    "org/cda/archi/governance/views/GovernanceView.class",
                    "org/cda/archi/governance/views/ConnectionView.class",
                    "org/cda/archi/governance/views/RepositoryListView.class",
                    "org/cda/archi/governance/services/GitService.class",
                    "org/cda/archi/governance/services/GitCredentialService.class",
                    "org/cda/archi/governance/services/GitHubAuthenticationService.class",
                    "org/cda/archi/governance/services/GitHubRepositoryService.class",
                    "org/cda/archi/governance/infrastructure/ProcessExecutor.class",
                    "org/cda/archi/governance/infrastructure/GitCredentialAdapter.class",
                    "plugin.xml" }) {
                require(jar.getEntry(entry) != null, "Bundle contains " + entry);
            }
            String xml = new String(jar.getInputStream(jar.getEntry("plugin.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            require(xml.contains("org.eclipse.ui.commands") && xml.contains("org.eclipse.ui.handlers")
                    && xml.contains("org.eclipse.ui.menus"), "Archi/Eclipse extension points are valid");
            require(xml.contains("GovernanceController"), "Menu handler points to the refactored controller");
        }

        try (ZipFile archive = new ZipFile(pluginPath.toFile())) {
            require(archive.getEntry("archi-plugin") != null, "Official Archi marker exists");
            require(archive.getEntry("governance-" + version + ".jar") != null, "Bundle JAR is packaged");
        }
        System.out.println("PASS: bundle manifest, classes, plugin.xml, installer layout, and version");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
