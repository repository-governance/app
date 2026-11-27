package org.cda.archi.governance.clients;

import java.io.IOException;
import java.net.URI;

import org.cda.archi.governance.infrastructure.CancellationToken;

/** Narrow seam for fake API responses in service tests. */
public interface GitHubApiClient {
    RestResponse get(URI uri, String bearerToken, CancellationToken cancellation)
            throws IOException, InterruptedException;
}
