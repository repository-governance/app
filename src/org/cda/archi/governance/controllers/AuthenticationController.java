package org.cda.archi.governance.controllers;

import java.io.IOException;

import org.cda.archi.governance.infrastructure.CancellationToken;
import org.cda.archi.governance.models.GitConfiguration;
import org.cda.archi.governance.services.GitHubAuthenticationService;
import org.cda.archi.governance.services.GitHubSession;
import org.cda.archi.governance.services.GitService;

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
