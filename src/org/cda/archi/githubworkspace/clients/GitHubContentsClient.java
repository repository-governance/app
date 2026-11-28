package org.cda.archi.githubworkspace.clients;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.JsonParser;
import org.cda.archi.githubworkspace.infrastructure.JsonWriter;
import org.cda.archi.githubworkspace.models.RemoteFile;

/** GitHub Contents API boundary for Data Manager, always scoped to a validated repo path. */
public final class GitHubContentsClient {
    public record RepositoryInfo(String defaultBranch, boolean isPrivate, boolean empty) { }
    public record Entry(String path, String name, String type, String sha, long size) { }
    public static final class EmptyRepositoryConflict extends IOException {
        private static final long serialVersionUID = 1L;
        public EmptyRepositoryConflict() {
            super("The GitHub Contents API cannot write to this repository's unborn branch.");
        }
    }
    private static final long MAX_FILE_BYTES = 256 * 1024;
    private final GitHubApiClient api;

    public GitHubContentsClient(GitHubApiClient api) { this.api = api; }

    public RepositoryInfo repository(String owner, String repository, String token, CancellationToken cancellation)
            throws IOException, InterruptedException {
        RestResponse response = api.get(uri("/repos/" + segment(owner) + "/" + segment(repository)),
                token, cancellation);
        if (response.statusCode() == 404) {
            throw new IOException("Data repository was not found or is not accessible to this GitHub account.");
        }
        require(response, 200, "Could not inspect the data repository");
        Map<?, ?> json = object(response.body());
        Object branch = json.get("default_branch");
        Object visibility = json.get("private");
        Object rawSize = json.get("size");
        if (!(visibility instanceof Boolean) || !(rawSize instanceof java.math.BigDecimal sizeValue)
                || sizeValue.signum() < 0 || (branch != null && !(branch instanceof String))) {
            throw new IOException("GitHub returned incomplete data repository metadata.");
        }
        boolean isPrivate = Boolean.TRUE.equals(visibility);
        long size = sizeValue.longValue();
        String defaultBranch = branch == null ? "" : String.valueOf(branch);
        if (defaultBranch.isBlank()) {
            if (size == 0) return new RepositoryInfo("", isPrivate, true);
            throw new IOException("GitHub returned no default branch for a non-empty data repository.");
        }
        RestResponse ref = api.get(URI.create(RestClient.API_ROOT + "/repos/" + segment(owner) + "/"
                + segment(repository) + "/git/ref/heads/" + encodeRef(defaultBranch)), token, cancellation);
        if (ref.statusCode() == 200) return new RepositoryInfo(defaultBranch, isPrivate, false);
        if (size == 0 && (ref.statusCode() == 404
                || ref.statusCode() == 409 && isEmptyRepositoryConflict(ref.body()))) {
            return new RepositoryInfo(defaultBranch, isPrivate, true);
        }
        if (ref.statusCode() == 404) {
            throw new IOException("The configured default branch was not found in this non-empty repository.");
        }
        throw failure(ref, "Could not verify the data repository's default branch");
    }

    public java.util.List<Entry> list(String owner, String repository, String path, String branch,
            String token, CancellationToken cancellation) throws IOException, InterruptedException {
        RestResponse response = api.get(contentsUri(owner, repository, path, branch), token, cancellation);
        if (response.statusCode() == 404) return java.util.List.of();
        require(response, 200, "Could not list " + path);
        Object value = JsonParser.parse(response.body());
        if (!(value instanceof java.util.List<?> list)) {
            throw new IOException("Expected a directory listing from GitHub for " + path + ".");
        }
        if (list.size() >= 1000) {
            throw new IOException("GitHub Contents API reached its 1,000-entry directory limit; results are incomplete.");
        }
        java.util.List<Entry> entries = new java.util.ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) throw new IOException("GitHub returned an invalid directory entry.");
            entries.add(new Entry(str(map.get("path")), str(map.get("name")), str(map.get("type")),
                    str(map.get("sha")), number(map.get("size"))));
        }
        return entries;
    }

    public RemoteFile read(String owner, String repository, String path, String branch,
            String token, CancellationToken cancellation) throws IOException, InterruptedException {
        RestResponse response = api.get(contentsUri(owner, repository, path, branch), token, cancellation);
        if (response.statusCode() == 404) return null;
        require(response, 200, "Could not read " + path);
        Map<?, ?> file = object(response.body());
        if (!"file".equals(str(file.get("type")))) throw new IOException("Expected a file at " + path + ".");
        long size = number(file.get("size"));
        if (size > MAX_FILE_BYTES) throw new IOException("Data file exceeds the 256 KiB safety limit.");
        if (!"base64".equals(str(file.get("encoding")))) {
            throw new IOException("GitHub did not return Base64 content for " + path + ".");
        }
        try {
            String encoded = str(file.get("content")).replaceAll("\\s", "");
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Data file exceeds the 256 KiB safety limit.");
            return new RemoteFile(path, new String(bytes, StandardCharsets.UTF_8), str(file.get("sha")));
        } catch (IllegalArgumentException ex) {
            throw new IOException("GitHub returned invalid Base64 content for " + path + ".");
        }
    }

    public String put(String owner, String repository, String path, String branch, String content,
            String message, String sha, String token, CancellationToken cancellation)
            throws IOException, InterruptedException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Data file exceeds the 256 KiB safety limit.");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("message", message);
        body.put("content", Base64.getEncoder().encodeToString(bytes));
        body.put("branch", branch);
        if (sha != null) body.put("sha", sha);
        RestResponse response = api.put(contentsUri(owner, repository, path, null),
                token, JsonWriter.write(body), cancellation);
        if ((response.statusCode() == 409 || response.statusCode() == 422)
                && isEmptyRepositoryConflict(response.body())) {
            throw new EmptyRepositoryConflict();
        }
        if (response.statusCode() == 409 || response.statusCode() == 422) {
            throw conflict(response, path);
        }
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw failure(response, "Could not write " + path);
        }
        return shaFromMutation(response.body());
    }

    public void delete(String owner, String repository, String path, String branch, String sha, String message,
            String token, CancellationToken cancellation) throws IOException, InterruptedException {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("message", message);
        body.put("sha", sha);
        body.put("branch", branch);
        RestResponse response = api.delete(contentsUri(owner, repository, path, null),
                token, JsonWriter.write(body), cancellation);
        if (response.statusCode() == 409 || response.statusCode() == 422) throw conflict(response, path);
        if (response.statusCode() != 200) throw failure(response, "Could not delete " + path);
    }

    /** Initializes an explicitly confirmed empty repository using a create-only ref operation. */
    public void initializeEmptyRepository(String owner, String repository, String branch, String path,
            String content, String message, String token, CancellationToken cancellation)
            throws IOException, InterruptedException {
        String apiRoot = RestClient.API_ROOT + "/repos/" + segment(owner) + "/" + segment(repository);
        Map<String, Object> treeEntry = new java.util.LinkedHashMap<>();
        treeEntry.put("path", path);
        treeEntry.put("mode", "100644");
        treeEntry.put("type", "blob");
        treeEntry.put("content", content);
        RestResponse tree = api.post(URI.create(apiRoot + "/git/trees"), token,
                JsonWriter.write(Map.of("tree", java.util.List.of(treeEntry))), cancellation);
        require(tree, 201, "Could not create the initial data tree");
        String treeSha = str(object(tree.body()).get("sha"));
        RestResponse commit = api.post(URI.create(apiRoot + "/git/commits"), token,
                JsonWriter.write(Map.of("message", message, "tree", treeSha)), cancellation);
        require(commit, 201, "Could not create the initial data commit");
        String commitSha = str(object(commit.body()).get("sha"));
        Map<String, Object> ref = Map.of("ref", "refs/heads/" + branch, "sha", commitSha);
        RestResponse created = api.post(URI.create(apiRoot + "/git/refs"), token, JsonWriter.write(ref), cancellation);
        if (created.statusCode() == 422 || created.statusCode() == 409) {
            throw new IOException("The repository was initialized concurrently. Refresh before retrying.");
        }
        require(created, 201, "Could not initialize the empty repository");
    }

    private static boolean isEmptyRepositoryConflict(String responseBody) {
        try {
            Object message = object(responseBody).get("message");
            return message != null && String.valueOf(message).toLowerCase(java.util.Locale.ROOT)
                    .contains("git repository is empty");
        } catch (IOException ex) {
            return false;
        }
    }

    private static URI contentsUri(String owner, String repository, String path, String branch) {
        StringBuilder url = new StringBuilder(RestClient.API_ROOT).append("/repos/")
                .append(segment(owner)).append('/').append(segment(repository)).append("/contents");
        if (path != null && !path.isBlank()) {
            for (String part : path.split("/")) url.append('/').append(segment(part));
        }
        if (branch != null && !branch.isBlank()) url.append("?ref=").append(query(branch));
        return URI.create(url.toString());
    }

    private static URI uri(String path) { return URI.create(RestClient.API_ROOT + path); }
    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static String query(String value) { return segment(value); }
    private static String encodeRef(String value) {
        return String.join("/", java.util.Arrays.stream(value.split("/", -1)).map(GitHubContentsClient::segment).toList());
    }
    private static Map<?, ?> object(String json) throws IOException {
        Object parsed;
        try { parsed = JsonParser.parse(json); }
        catch (RuntimeException ex) { throw new IOException("GitHub returned invalid JSON."); }
        if (!(parsed instanceof Map<?, ?> map)) throw new IOException("GitHub returned an invalid JSON object.");
        return map;
    }
    private static String str(Object value) { return value == null ? "" : String.valueOf(value); }
    private static long number(Object value) {
        return value instanceof java.math.BigDecimal decimal ? decimal.longValue() : 0L;
    }
    private static void require(RestResponse response, int expected, String message) throws IOException {
        if (response.statusCode() != expected) throw failure(response, message);
    }
    private static IOException failure(RestResponse response, String prefix) {
        String detail = "";
        try {
            Object value = object(response.body()).get("message");
            if (value != null) detail = ": " + value;
        } catch (IOException ignored) { }
        return new IOException(prefix + " (HTTP " + response.statusCode() + ")" + detail);
    }
    private static IOException conflict(RestResponse response, String path) {
        return new IOException("GitHub reports a concurrent change or validation conflict for " + path
                + " (HTTP " + response.statusCode() + "). Refresh and retry.");
    }
    private static String shaFromMutation(String body) throws IOException {
        try {
            Object value = object(body).get("content");
            if (value instanceof Map<?, ?> content && content.get("sha") != null) return str(content.get("sha"));
            throw new IOException("GitHub did not return the file SHA.");
        } catch (RuntimeException ex) {
            throw new IOException("GitHub returned an invalid write response.");
        }
    }
}
