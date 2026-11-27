package org.cda.archi.governance.controllers;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.cda.archi.governance.infrastructure.CancellationToken;
import org.cda.archi.governance.models.GitHubOrganization;
import org.cda.archi.governance.models.GitHubRepository;
import org.cda.archi.governance.models.RepositorySearchResult;
import org.cda.archi.governance.services.GitHubRepositoryService;
import org.cda.archi.governance.services.GitHubSession;
import org.cda.archi.governance.services.ArchiModelService;
import org.cda.archi.governance.services.GitService;
import org.cda.archi.governance.services.WorkspaceService;

/** Coordinates repository search and safe browser navigation. */
public final class RepositoryController {
    private final GitHubRepositoryService repositories;
    private final GitService git;
    private final WorkspaceService workspace;
    private final ArchiModelService archiModels;

    public RepositoryController(GitHubRepositoryService repositories) {
        this(repositories, new GitService(), new WorkspaceService(), new ArchiModelService());
    }

    public RepositoryController(GitHubRepositoryService repositories, GitService git,
            WorkspaceService workspace, ArchiModelService archiModels) {
        this.repositories = repositories;
        this.git = git;
        this.workspace = workspace;
        this.archiModels = archiModels;
    }

    public RepositorySearchResult search(GitHubSession session, String organization, String topic,
            CancellationToken cancellation) throws IOException, InterruptedException {
        return repositories.search(session, new GitHubOrganization(organization), topic, cancellation);
    }

    public void openInBrowser(GitHubRepository repository) throws IOException {
        URI uri;
        try {
            uri = new URI(repository.getHtmlUrl());
        } catch (URISyntaxException ex) {
            throw new IOException("The selected repository has an invalid browser URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new IOException("Only GitHub HTTPS repository pages can be opened.");
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("The operating system does not provide a default browser action.");
        }
        Desktop.getDesktop().browse(uri);
    }

    /** Performs workspace creation, Git validation/cloning and model lookup off the SWT thread. */
    public Path prepareModel(String organization, GitHubRepository repository, CancellationToken cancellation,
            Consumer<String> progress) throws IOException, InterruptedException {
        cancellation.throwIfCancelled();
        WorkspaceService.validateCloneUrl(repository, organization);
        progress.accept("Preparing local workspace...");
        Path destination = workspace.prepareRepositoryDirectory(organization, repository);
        workspace.verifyRepositoryPath(destination);
        cancellation.throwIfCancelled();
        git.cloneOrReuse(repository.getCloneUrl(), organization, repository.getName(), destination,
                cancellation, progress);
        workspace.verifyRepositoryPath(destination);
        cancellation.throwIfCancelled();
        progress.accept("Locating ArchiMate model...");
        return workspace.modelPath(destination);
    }

    /** Uses Archi's native File > Open model manager; invoke only on the SWT UI thread. */
    public Path openModel(Path modelFile) throws IOException {
        return archiModels.openOrActivate(modelFile);
    }
}
