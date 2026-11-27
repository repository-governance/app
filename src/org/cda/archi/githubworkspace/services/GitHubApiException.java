package org.cda.archi.githubworkspace.services;

import java.io.IOException;

/** Safe, user-facing API failure that never contains request headers or token material. */
public final class GitHubApiException extends IOException {
    private final int statusCode;

    public GitHubApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
