package org.cda.archi.githubworkspace.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cda.archi.githubworkspace.clients.GitHubApiClient;
import org.cda.archi.githubworkspace.clients.RestClient;
import org.cda.archi.githubworkspace.clients.RestResponse;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.JsonParser;
import org.cda.archi.githubworkspace.models.GitHubOrganization;
import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.RepositorySearchResult;

/** Searches only GitHub's authorized search results; it does not clone or write repositories. */
public final class GitHubRepositoryService {
    public static final int PAGE_SIZE = 100;
    public static final int SEARCH_RESULT_LIMIT = 1000;
    private final GitHubApiClient api;
    private final LinkedHashMap<String, byte[]> thumbnailCache =
            new LinkedHashMap<>(32, 0.75f, true);
    private int cachedThumbnailBytes;

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

    /** Downloads the alphabetically first PNG thumbnail through authenticated GitHub REST API calls. */
    public byte[] loadThumbnail(GitHubSession session, GitHubRepository repository,
            CancellationToken cancellation) throws IOException, InterruptedException {
        WorkspaceService.validateRepositoryIdentity(repository);
        if (repository.getDefaultBranch().isBlank()) return new byte[0];
        String cacheKey = repository.getId() + "@" + repository.getDefaultBranch();
        byte[] cached = cachedThumbnail(cacheKey);
        if (cached != null) return cached;
        String repoPath = encodePath(repository.getFullName());
        String ref = URLEncoder.encode(repository.getDefaultBranch(), StandardCharsets.UTF_8);
        URI directoryUri = URI.create(RestClient.API_ROOT + "/repos/" + repoPath
                + "/contents/thumbnails?ref=" + ref);
        RestResponse directory = api.get(directoryUri, session.tokenForRequest(), cancellation);
        if (directory.statusCode() == 404) return cacheThumbnail(cacheKey, new byte[0]);
        if (directory.statusCode() != 200) throw ApiErrors.from(directory, false);

        Map<String, Object> selected = null;
        try {
            Object parsed = JsonParser.parse(directory.body());
            if (!(parsed instanceof List<?> entries)) return cacheThumbnail(cacheKey, new byte[0]);
            for (Object item : entries) {
                Map<String, Object> entry = JsonObjects.object(item, "thumbnail directory entry");
                String name = JsonObjects.stringOrEmpty(entry, "name");
                if (!"file".equals(JsonObjects.stringOrEmpty(entry, "type"))
                        || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")
                        || !name.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                if (selected == null || name.compareToIgnoreCase(JsonObjects.string(selected, "name")) < 0) {
                    selected = entry;
                }
            }
        } catch (IllegalArgumentException ex) {
            throw new IOException("GitHub returned invalid thumbnail directory metadata.");
        }
        if (selected == null) return cacheThumbnail(cacheKey, new byte[0]);
        String name = JsonObjects.string(selected, "name");
        URI contentUri = URI.create(RestClient.API_ROOT + "/repos/" + repoPath
                + "/contents/thumbnails/" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "?ref=" + ref);
        RestResponse response = api.get(contentUri, session.tokenForRequest(), cancellation);
        if (response.statusCode() == 404) return cacheThumbnail(cacheKey, new byte[0]);
        if (response.statusCode() != 200) throw ApiErrors.from(response, false);
        try {
            Map<String, Object> content = JsonObjects.object(JsonParser.parse(response.body()), "thumbnail response");
            if (!"base64".equalsIgnoreCase(JsonObjects.stringOrEmpty(content, "encoding"))) {
                return cacheThumbnail(cacheKey, new byte[0]);
            }
            String encoded = JsonObjects.stringOrEmpty(content, "content").replaceAll("\\s", "");
            if (encoded.length() > 5_600_000) throw new IOException("The repository thumbnail is too large.");
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (!isSafePng(bytes)) {
                java.util.Arrays.fill(bytes, (byte) 0);
                return cacheThumbnail(cacheKey, new byte[0]);
            }
            return cacheThumbnail(cacheKey, bytes);
        } catch (IllegalArgumentException ex) {
            throw new IOException("GitHub returned invalid thumbnail image data.");
        }
    }

    public synchronized void clearThumbnailCache() {
        thumbnailCache.values().forEach(bytes -> java.util.Arrays.fill(bytes, (byte) 0));
        thumbnailCache.clear();
        cachedThumbnailBytes = 0;
    }

    private synchronized byte[] cachedThumbnail(String key) {
        byte[] value = thumbnailCache.get(key);
        return value == null ? null : value.clone();
    }

    private synchronized byte[] cacheThumbnail(String key, byte[] bytes) {
        byte[] previous = thumbnailCache.remove(key);
        if (previous != null) {
            cachedThumbnailBytes -= previous.length;
            java.util.Arrays.fill(previous, (byte) 0);
        }
        byte[] stored = bytes.clone();
        thumbnailCache.put(key, stored);
        cachedThumbnailBytes += stored.length;
        while ((cachedThumbnailBytes > 16 * 1024 * 1024 || thumbnailCache.size() > 128)
                && !thumbnailCache.isEmpty()) {
            String eldest = thumbnailCache.keySet().iterator().next();
            byte[] removed = thumbnailCache.remove(eldest);
            cachedThumbnailBytes -= removed.length;
            java.util.Arrays.fill(removed, (byte) 0);
        }
        return bytes.clone();
    }

    private static String encodePath(String path) {
        StringBuilder encoded = new StringBuilder();
        for (String segment : path.split("/", -1)) {
            if (encoded.length() > 0) encoded.append('/');
            encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }

    private static boolean isSafePng(byte[] bytes) {
        if (bytes.length < 24 || bytes.length > 4 * 1024 * 1024
                || (bytes[0] & 0xff) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N'
                || bytes[3] != 'G' || bytes[4] != 0x0d || bytes[5] != 0x0a
                || bytes[6] != 0x1a || bytes[7] != 0x0a) return false;
        long width = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes, 16, 4).getInt());
        long height = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes, 20, 4).getInt());
        return width > 0 && height > 0 && width <= 8192 && height <= 8192 && width * height <= 16_000_000;
    }

    public static String validateTopic(String topic) {
        if (topic == null || !topic.matches("[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?")) {
            throw new IllegalArgumentException("Topic must be 1–50 lowercase letters, numbers, or hyphens.");
        }
        return topic;
    }

    static GitHubRepository parseRepository(Map<String, Object> item) {
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
