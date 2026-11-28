package org.cda.archi.githubworkspace.clients;

import java.io.IOException;
import java.net.URI;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;

/** Narrow seam for fake API responses in service tests. */
public interface GitHubApiClient {
    RestResponse get(URI uri, String bearerToken, CancellationToken cancellation)
            throws IOException, InterruptedException;

    default RestResponse post(URI uri, String bearerToken, String jsonBody, CancellationToken cancellation)
            throws IOException, InterruptedException {
        throw new IOException("This GitHub API client does not support write requests.");
    }

    default RestResponse patch(URI uri, String bearerToken, String jsonBody, CancellationToken cancellation)
            throws IOException, InterruptedException {
        throw new IOException("This GitHub API client does not support write requests.");
    }
}
