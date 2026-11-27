package org.cda.archi.githubworkspace.models;

import java.util.List;

/** Complete result set; partial searches are rejected instead of being represented here. */
public final class RepositorySearchResult {
    private final List<GitHubRepository> repositories;
    private final long totalCount;

    public RepositorySearchResult(List<GitHubRepository> repositories, long totalCount) {
        this.repositories = List.copyOf(repositories);
        this.totalCount = totalCount;
    }

    public List<GitHubRepository> getRepositories() { return repositories; }
    public long getTotalCount() { return totalCount; }
    public boolean isEmpty() { return repositories.isEmpty(); }
}
