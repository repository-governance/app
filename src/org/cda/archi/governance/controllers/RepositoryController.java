package org.cda.archi.governance.controllers;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.cda.archi.governance.infrastructure.CancellationToken;
import org.cda.archi.governance.models.GitHubOrganization;
import org.cda.archi.governance.models.GitHubRepository;
import org.cda.archi.governance.models.RepositorySearchResult;
import org.cda.archi.governance.services.GitHubRepositoryService;
import org.cda.archi.governance.services.GitHubSession;

/** Coordinates repository search and safe browser navigation. */
public final class RepositoryController {
    private final GitHubRepositoryService repositories;

    public RepositoryController(GitHubRepositoryService repositories) {
        this.repositories = repositories;
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
}
