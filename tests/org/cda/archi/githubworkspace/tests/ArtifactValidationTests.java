package org.cda.archi.githubworkspace.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.ui.IWorkbenchActionConstants;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
                    "org/cda/archi/githubworkspace/infrastructure/CatalogWorker.class",
                    "org/cda/archi/githubworkspace/infrastructure/GitCredentialAdapter.class",
                    "plugin.xml" }) {
                require(jar.getEntry(entry) != null, "Bundle contains " + entry);
            }
            String xml = new String(jar.getInputStream(jar.getEntry("plugin.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            require(xml.contains("org.eclipse.ui.commands") && xml.contains("org.eclipse.ui.handlers")
                    && xml.contains("org.eclipse.ui.menus") && xml.contains("org.eclipse.ui.preferencePages"),
                    "Archi/Eclipse extension points are valid");

            // Archi 5.11.0's ArchiActionBarAdvisor#createFileMenu() creates M_FILE
            // and terminates it with FILE_END; anchor this contribution to that real group.
            String fileMenuLocation = "menu:" + IWorkbenchActionConstants.M_FILE
                    + "?before=" + IWorkbenchActionConstants.FILE_END;
            require("menu:file?before=fileEnd".equals(fileMenuLocation),
                    "Archi File menu and FILE_END constants match the 5.11.0 source contract");
            Element contribution = findMenuContribution(document, fileMenuLocation);
            require(contribution != null, "Repository menu is inserted before Archi's File-end marker");

            Element submenu = findById(contribution.getElementsByTagName("menu"),
                    "org.cda.archi.githubworkspace.menu");
            require(submenu != null && "GitHub Workspace".equals(submenu.getAttribute("label")),
                    "GitHub Workspace submenu ID and label are preserved");
            Element browseCommand = findById(submenu.getElementsByTagName("command"),
                    "org.cda.archi.githubworkspace.browseRepositories.menu");
            require(browseCommand != null
                    && "org.cda.archi.githubworkspace.browseRepositories".equals(
                            browseCommand.getAttribute("commandId"))
                    && "Browse Repositories...".equals(browseCommand.getAttribute("label")),
                    "Browse Repositories command IDs and label are preserved");
            require(xml.contains("class=\"org.cda.archi.githubworkspace.controllers.GitHubWorkspaceController\"")
                    && xml.contains("commandId=\"org.cda.archi.githubworkspace.browseRepositories\""),
                    "Existing repository browser handler is preserved");
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

    private static Element findMenuContribution(org.w3c.dom.Document document, String location) {
        NodeList contributions = document.getElementsByTagName("menuContribution");
        for (int index = 0; index < contributions.getLength(); index++) {
            Element candidate = (Element) contributions.item(index);
            if (location.equals(candidate.getAttribute("locationURI"))) return candidate;
        }
        return null;
    }

    private static Element findById(NodeList elements, String id) {
        for (int index = 0; index < elements.getLength(); index++) {
            Element candidate = (Element) elements.item(index);
            if (id.equals(candidate.getAttribute("id"))) return candidate;
        }
        return null;
    }
}
