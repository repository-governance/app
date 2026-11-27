package org.cda.archi.githubworkspace.controllers;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.List;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.models.GitHubOrganization;
import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.RepositorySearchResult;
import org.cda.archi.githubworkspace.services.GitHubRepositoryService;
import org.cda.archi.githubworkspace.services.GitHubSession;
import org.cda.archi.githubworkspace.services.ArchiModelService;
import org.cda.archi.githubworkspace.services.GitService;
import org.cda.archi.githubworkspace.services.WorkspaceService;

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

    public List<String> localPlans(GitHubRepository repository, CancellationToken cancellation)
            throws IOException, InterruptedException {
        return git.findValidPlanCheckouts(repository, workspace, cancellation).stream()
                .map(path -> path.getFileName().toString()).toList();
    }

    /** Clone into an independent staging checkout, create/track the Plan ID branch, then find the model. */
    public Path preparePlan(GitHubRepository repository, String planId, CancellationToken cancellation,
            Consumer<String> progress) throws IOException, InterruptedException {
        cancellation.throwIfCancelled();
        WorkspaceService.validateCloneUrl(repository);
        WorkspaceService.validatePlanIdSyntax(planId);
        Path destination = workspace.preparePlanDirectory(repository, planId);
        workspace.validateDestination(destination);
        cancellation.throwIfCancelled();
        progress.accept("Validating Plan ID...");
        git.validateBranchName(planId, cancellation);
        git.clonePlanOrReuse(repository.getCloneUrl(), WorkspaceService.repositoryOrganization(repository),
                repository.getName(), repository.getDefaultBranch(), planId, destination, cancellation, progress);
        workspace.validateDestination(destination);
        cancellation.throwIfCancelled();
        progress.accept("Locating source/design.archimate...");
        return workspace.modelPath(destination);
    }

    public Path existingPlanModel(GitHubRepository repository, String planId, CancellationToken cancellation)
            throws IOException, InterruptedException {
        WorkspaceService.validatePlanIdSyntax(planId);
        git.validateBranchName(planId, cancellation);
        Path plan = workspace.planPath(repository, planId);
        workspace.validateDestination(plan);
        git.validatePlanWorkingCopy(WorkspaceService.repositoryOrganization(repository),
                repository.getName(), planId, plan, cancellation);
        return workspace.modelPath(plan);
    }

    public byte[] loadThumbnail(GitHubSession session, GitHubRepository repository,
            CancellationToken cancellation) throws IOException, InterruptedException {
        return repositories.loadThumbnail(session, repository, cancellation);
    }

    public java.nio.file.Path workspaceRoot() {
        return workspace.workspaceRoot();
    }

    /** Uses Archi's native File > Open model manager; invoke only on the SWT UI thread. */
    public Path openModel(Path modelFile) throws IOException {
        return archiModels.openOrActivate(modelFile);
    }
}
