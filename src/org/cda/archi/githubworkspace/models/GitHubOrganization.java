package org.cda.archi.githubworkspace.models;

/** Configurable GitHub organization login (not an organization credential). */
public final class GitHubOrganization {
    private final String login;

    public GitHubOrganization(String login) {
        if (login == null || !login.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")) {
            throw new IllegalArgumentException("Enter a valid GitHub organization name.");
        }
        this.login = login;
    }

    public String getLogin() { return login; }
}
