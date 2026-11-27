package org.cda.archi.governance.models;

import java.time.Instant;
import java.util.List;

/** Repository metadata returned by GitHub Search API. */
public final class GitHubRepository {
    private final long id;
    private final String name;
    private final String fullName;
    private final String description;
    private final String visibility;
    private final boolean privateRepository;
    private final List<String> topics;
    private final String htmlUrl;
    private final String cloneUrl;
    private final String sshUrl;
    private final String defaultBranch;
    private final Instant updatedAt;

    public GitHubRepository(long id, String name, String fullName, String description, String visibility,
            boolean privateRepository, List<String> topics, String htmlUrl, String cloneUrl, String sshUrl,
            String defaultBranch, Instant updatedAt) {
        this.id = id;
        this.name = safe(name);
        this.fullName = safe(fullName);
        this.description = safe(description);
        this.visibility = safe(visibility);
        this.privateRepository = privateRepository;
        this.topics = List.copyOf(topics);
        this.htmlUrl = safe(htmlUrl);
        this.cloneUrl = safe(cloneUrl);
        this.sshUrl = safe(sshUrl);
        this.defaultBranch = safe(defaultBranch);
        this.updatedAt = updatedAt;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getFullName() { return fullName; }
    public String getDescription() { return description; }
    public String getVisibility() { return visibility; }
    public boolean isPrivate() { return privateRepository; }
    public List<String> getTopics() { return topics; }
    public String getHtmlUrl() { return htmlUrl; }
    public String getCloneUrl() { return cloneUrl; }
    public String getSshUrl() { return sshUrl; }
    public String getDefaultBranch() { return defaultBranch; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String safe(String value) { return value == null ? "" : value; }
}
