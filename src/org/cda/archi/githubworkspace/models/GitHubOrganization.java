package org.cda.archi.githubworkspace.models;

/** GitHub organization login, accepting either its login or canonical URL. */
public final class GitHubOrganization {
    private final String login;

    public GitHubOrganization(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.startsWith("https://github.com/")) {
            candidate = candidate.substring("https://github.com/".length());
            if (candidate.endsWith("/")) candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (!candidate.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")) {
            throw new IllegalArgumentException("Enter a GitHub organization URL or name.");
        }
        this.login = candidate;
    }

    public String getLogin() { return login; }
    public String getUrl() { return "https://github.com/" + login; }
}
