package org.cda.archi.githubworkspace.models;

/** Authenticated GitHub account returned by GET /user. */
public final class GitHubUser {
    private final long id;
    private final String login;
    private final String name;
    private final String htmlUrl;

    public GitHubUser(long id, String login, String name, String htmlUrl) {
        this.id = id;
        this.login = required(login, "login");
        this.name = name == null ? "" : name;
        this.htmlUrl = htmlUrl == null ? "" : htmlUrl;
    }

    public long getId() { return id; }
    public String getLogin() { return login; }
    public String getName() { return name; }
    public String getHtmlUrl() { return htmlUrl; }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing GitHub " + field + ".");
        return value;
    }
}
