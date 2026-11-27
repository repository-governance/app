package org.cda.archi.governance.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cda.archi.governance.clients.GitHubApiClient;
import org.cda.archi.governance.clients.RestClient;
import org.cda.archi.governance.clients.RestResponse;
import org.cda.archi.governance.infrastructure.CancellationToken;
import org.cda.archi.governance.infrastructure.JsonParser;
import org.cda.archi.governance.models.GitHubOrganization;
import org.cda.archi.governance.models.GitHubRepository;
import org.cda.archi.governance.models.RepositorySearchResult;

/** Searches only GitHub's authorized search results; it does not clone or write repositories. */
public final class GitHubRepositoryService {
    public static final int PAGE_SIZE = 100;
    public static final int SEARCH_RESULT_LIMIT = 1000;
    private final GitHubApiClient api;

    public GitHubRepositoryService(GitHubApiClient api) {
        this.api = api;
    }

    public RepositorySearchResult search(GitHubSession session, GitHubOrganization organization,
            String topic, CancellationToken cancellation) throws IOException, InterruptedException {
        String normalizedTopic = validateTopic(topic);
        String query = "org:" + organization.getLogin() + " topic:" + normalizedTopic;
        List<GitHubRepository> repositories = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        long expectedTotal = -1;
        int pageCount = 0;

        while (true) {
            cancellation.throwIfCancelled();
            int page = pageCount + 1;
            URI uri = URI.create(RestClient.API_ROOT + "/search/repositories?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&per_page=" + PAGE_SIZE + "&page=" + page);
            RestResponse response = api.get(uri, session.tokenForRequest(), cancellation);
            if (response.statusCode() != 200) throw ApiErrors.from(response, false);

            Map<String, Object> json;
            try {
                json = JsonObjects.object(JsonParser.parse(response.body()), "repository search response");
                Object incomplete = json.get("incomplete_results");
                if (!(incomplete instanceof Boolean)) {
                    throw new IllegalArgumentException("Missing incomplete_results.");
                }
                if ((Boolean) incomplete) {
                    throw new IncompleteSearchException("GitHub marked this search incomplete. "
                            + "No partial repository list is shown; narrow the topic or retry.");
                }
                long total = JsonObjects.integer(json, "total_count", -1);
                if (total < 0) throw new IllegalArgumentException("Missing total_count.");
                if (total > SEARCH_RESULT_LIMIT) {
                    throw new IncompleteSearchException("GitHub Search can return at most 1,000 results. "
                            + "Narrow the topic; the partial list was discarded.");
                }
                if (expectedTotal < 0) expectedTotal = total;
                else if (total != expectedTotal) {
                    throw new IncompleteSearchException("The search result changed during pagination. "
                            + "Refresh to request a consistent list.");
                }
                List<?> items = json.get("items") instanceof List<?> list ? list : null;
                if (items == null) throw new IllegalArgumentException("Missing items.");
                for (Object item : items) {
                    GitHubRepository repository = parseRepository(JsonObjects.object(item, "repository"));
                    if (!ids.add(repository.getId())) {
                        throw new IncompleteSearchException("GitHub returned duplicate search results. "
                                + "No partial repository list is shown.");
                    }
                    repositories.add(repository);
                }
            } catch (IncompleteSearchException ex) {
                throw ex;
            } catch (IllegalArgumentException | ArithmeticException ex) {
                throw new IOException("GitHub returned invalid repository search JSON.");
            }

            pageCount++;
            if (repositories.size() >= expectedTotal) break;
            if (pageCount >= 10) {
                throw new IncompleteSearchException("GitHub's 1,000-result search boundary was reached. "
                        + "No partial repository list is shown.");
            }
            if (repositories.size() != (long) pageCount * PAGE_SIZE) {
                throw new IncompleteSearchException("GitHub returned a short page before the search was complete. "
                        + "No partial repository list is shown.");
            }
        }

        if (repositories.size() != expectedTotal) {
            throw new IncompleteSearchException("GitHub returned an incomplete result set. "
                    + "No partial repository list is shown.");
        }
        return new RepositorySearchResult(repositories, expectedTotal);
    }

    public static String validateTopic(String topic) {
        if (topic == null || !topic.matches("[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?")) {
            throw new IllegalArgumentException("Topic must be 1–50 lowercase letters, numbers, or hyphens.");
        }
        return topic;
    }

    private static GitHubRepository parseRepository(Map<String, Object> item) {
        long id = JsonObjects.integer(item, "id", -1);
        String name = JsonObjects.string(item, "name");
        String fullName = JsonObjects.string(item, "full_name");
        String htmlUrl = JsonObjects.string(item, "html_url");
        URI browserUri;
        try {
            browserUri = URI.create(htmlUrl);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid repository URL.");
        }
        if (!"https".equalsIgnoreCase(browserUri.getScheme())
                || !"github.com".equalsIgnoreCase(browserUri.getHost())
                || browserUri.getUserInfo() != null || browserUri.getPort() != -1) {
            throw new IllegalArgumentException("Invalid repository URL.");
        }
        boolean isPrivate = JsonObjects.bool(item, "private", false);
        String visibility = JsonObjects.stringOrEmpty(item, "visibility");
        if (visibility.isBlank()) visibility = isPrivate ? "private" : "public";
        Instant updated = null;
        String timestamp = JsonObjects.stringOrEmpty(item, "updated_at");
        if (!timestamp.isBlank()) {
            try {
                updated = Instant.parse(timestamp);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Invalid updated_at.");
            }
        }
        return new GitHubRepository(id, name, fullName, JsonObjects.stringOrEmpty(item, "description"),
                visibility, isPrivate, JsonObjects.strings(item.get("topics")), htmlUrl,
                JsonObjects.stringOrEmpty(item, "clone_url"), JsonObjects.stringOrEmpty(item, "ssh_url"),
                JsonObjects.stringOrEmpty(item, "default_branch"), updated);
    }
}
