package org.cda.archi.githubworkspace.services;

import java.util.Arrays;

import org.cda.archi.githubworkspace.models.GitHubUser;

/** Ephemeral API session. Closing the dialog disposes the in-memory token buffer. */
public final class GitHubSession implements AutoCloseable {
    private final GitHubUser user;
    private char[] token;

    GitHubSession(GitHubUser user, char[] token) {
        this.user = user;
        this.token = token.clone();
    }

    public GitHubUser getUser() { return user; }

    public String tokenForRequest() {
        if (token.length == 0) throw new IllegalStateException("GitHub session has been closed.");
        return new String(token);
    }

    @Override
    public void close() {
        Arrays.fill(token, '\0');
        token = new char[0];
    }

    @Override
    public String toString() { return "GitHubSession[user=" + user.getLogin() + ", token=redacted]"; }
}
