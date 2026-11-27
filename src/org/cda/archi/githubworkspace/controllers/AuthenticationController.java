package org.cda.archi.githubworkspace.controllers;

import java.io.IOException;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.services.GitHubAuthenticationService;
import org.cda.archi.githubworkspace.services.GitHubSession;
import org.cda.archi.githubworkspace.services.GitService;

/** Coordinates Git inspection and delegated GitHub authentication services. */
public final class AuthenticationController {
    private final GitService git;
    private final GitHubAuthenticationService authentication;

    public AuthenticationController(GitService git, GitHubAuthenticationService authentication) {
        this.git = git;
        this.authentication = authentication;
    }

    public GitConfiguration inspectGit(CancellationToken cancellation) {
        return git.inspect(cancellation);
    }

    public GitHubSession authenticate(GitConfiguration configuration, CancellationToken cancellation)
            throws IOException, InterruptedException {
        return authentication.authenticate(configuration, cancellation);
    }
}
