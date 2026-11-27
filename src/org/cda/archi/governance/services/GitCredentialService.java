package org.cda.archi.governance.services;

import java.io.IOException;

import org.cda.archi.governance.infrastructure.CancellationToken;
import org.cda.archi.governance.infrastructure.GitCredentialAdapter;
import org.cda.archi.governance.models.GitConfiguration;
import org.cda.archi.governance.models.GitCredential;

/** Application service guarding delegated credential retrieval with local Git readiness. */
public final class GitCredentialService {
    private final GitCredentialAdapter adapter;

    public GitCredentialService() {
        this(new GitCredentialAdapter());
    }

    public GitCredentialService(GitCredentialAdapter adapter) {
        this.adapter = adapter;
    }

    public GitCredential retrieve(GitConfiguration git, CancellationToken cancellation) throws IOException {
        if (!git.isAvailable()) throw new IOException("Install Git and make sure it is available in PATH.");
        if (!git.hasCredentialHelper()) {
            throw new IOException("No Git credential helper is configured. Configure Git Credential Manager locally.");
        }
        return adapter.fill(cancellation);
    }
}
