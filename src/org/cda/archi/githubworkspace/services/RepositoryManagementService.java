package org.cda.archi.githubworkspace.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.cda.archi.githubworkspace.clients.GitHubApiClient;
import org.cda.archi.githubworkspace.clients.RestClient;
import org.cda.archi.githubworkspace.clients.RestResponse;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.JsonParser;
import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.RepositoryTemplateFolder;

/** Lists organization repositories and creates one from a selected folder in a template repository. */
public final class RepositoryManagementService {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_TEMPLATE_FILES = 1000;
    private static final long MAX_TEMPLATE_BYTES = 25L * 1024 * 1024;
    private final GitHubApiClient api;

    public RepositoryManagementService(GitHubApiClient api) {
        this.api = api;
    }

    public List<GitHubRepository> listOrganizationRepositories(GitHubSession session, String organization,
            CancellationToken cancellation) throws IOException, InterruptedException {
        WorkspaceService.validateOrganization(organization);
        List<GitHubRepository> repositories = new ArrayList<>();
        for (int page = 1; ; page++) {
            cancellation.throwIfCancelled();
            URI uri = URI.create(RestClient.API_ROOT + "/orgs/" + encodeSegment(organization)
                    + "/repos?type=all&sort=full_name&direction=asc&per_page=" + PAGE_SIZE + "&page=" + page);
            RestResponse response = api.get(uri, session.tokenForRequest(), cancellation);
            if (response.statusCode() != 200) throw ApiErrors.from(response, false);
            try {
                Object parsed = JsonParser.parse(response.body());
                if (!(parsed instanceof List<?> entries)) throw new IllegalArgumentException("Expected list.");
                for (Object entry : entries) {
                    repositories.add(GitHubRepositoryService.parseRepository(
                            JsonObjects.object(entry, "organization repository")));
                }
                if (entries.size() < PAGE_SIZE) return List.copyOf(repositories);
            } catch (IllegalArgumentException | ArithmeticException ex) {
                throw new IOException("GitHub returned invalid organization repository data.");
            }
            if (page >= 100) throw new IOException("The organization repository list exceeded 10,000 entries.");
        }
    }

    public List<RepositoryTemplateFolder> listTemplateFolders(GitHubSession session, String templateRepository,
            CancellationToken cancellation) throws IOException, InterruptedException {
        String fullName = normalizeTemplateRepository(templateRepository);
        RepositoryMetadata metadata = repositoryMetadata(session, fullName, cancellation);
        URI contentsUri = URI.create(RestClient.API_ROOT + "/repos/" + encodePath(fullName)
                + "/contents?ref=" + encodeSegment(metadata.defaultBranch()));
        RestResponse response = api.get(contentsUri, session.tokenForRequest(), cancellation);
        if (response.statusCode() != 200) throw ApiErrors.from(response, false);
        try {
            Object parsed = JsonParser.parse(response.body());
            if (!(parsed instanceof List<?> entries)) throw new IllegalArgumentException("Expected root listing.");
            List<RepositoryTemplateFolder> folders = new ArrayList<>();
            for (Object raw : entries) {
                Map<String, Object> entry = JsonObjects.object(raw, "template repository entry");
                if (!"dir".equals(JsonObjects.stringOrEmpty(entry, "type"))) continue;
                String name = JsonObjects.string(entry, "name");
                String sha = JsonObjects.string(entry, "sha");
                if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
                    throw new IllegalArgumentException("Unsafe template folder name.");
                }
                folders.add(new RepositoryTemplateFolder(name, sha));
            }
            folders.sort(Comparator.comparing(RepositoryTemplateFolder::name, String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(folders);
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("The template repository returned invalid folder data.");
        }
    }

    public GitHubRepository createFromTemplateFolder(GitHubSession session, String organization,
            String templateRepository, String folderName, String repositoryName, String description,
            boolean isPrivate, CancellationToken cancellation, Consumer<String> progress)
            throws IOException, InterruptedException {
        WorkspaceService.validateOrganization(organization);
        WorkspaceService.validateRepositoryName(repositoryName);
        String templateFullName = normalizeTemplateRepository(templateRepository);
        RepositoryMetadata metadata = repositoryMetadata(session, templateFullName, cancellation);
        RepositoryTemplateFolder folder = findTemplateFolder(session, templateFullName, metadata,
                folderName, cancellation);
        List<TemplateFile> files = loadTemplateFiles(session, templateFullName, folder, cancellation, progress);
        cancellation.throwIfCancelled();

        progress.accept("Creating repository in " + organization + "...");
        String createBody = "{\"name\":" + quote(repositoryName)
                + ",\"description\":" + quote(description == null ? "" : description.trim())
                + ",\"private\":" + isPrivate + ",\"auto_init\":true}";
        RestResponse createdResponse = api.post(
                URI.create(RestClient.API_ROOT + "/orgs/" + encodeSegment(organization) + "/repos"),
                session.tokenForRequest(), createBody, cancellation);
        if (createdResponse.statusCode() != 201) {
            if (createdResponse.statusCode() == 422) {
                throw new IOException("GitHub could not create the repository. Check that its name is available "
                        + "and that your account can create repositories in " + organization + ".");
            }
            throw ApiErrors.from(createdResponse, false);
        }

        GitHubRepository created = parseCreatedRepository(createdResponse);
        try {
            String createdPath = encodePath(created.getFullName());
            String branch = created.getDefaultBranch();
            if (branch.isBlank()) {
                throw new IOException("GitHub initialized the repository without reporting its default branch.");
            }
            progress.accept("Reading the initial repository version...");
            InitialCommit initial = readInitialCommit(session, createdPath, branch, cancellation);
            List<CreatedFile> createdFiles = new ArrayList<>(files.size());
            for (int index = 0; index < files.size(); index++) {
                cancellation.throwIfCancelled();
                TemplateFile file = files.get(index);
                progress.accept("Copying template files (" + (index + 1) + " of " + files.size() + ")...");
                String blobBody = "{\"content\":" + quote(Base64.getEncoder().encodeToString(file.bytes()))
                        + ",\"encoding\":\"base64\"}";
                RestResponse blobResponse = api.post(URI.create(RestClient.API_ROOT
                        + "/repos/" + createdPath + "/git/blobs"), session.tokenForRequest(), blobBody, cancellation);
                if (blobResponse.statusCode() != 201) throw ApiErrors.from(blobResponse, false);
                createdFiles.add(new CreatedFile(file.path(), file.mode(), responseSha(blobResponse, "blob")));
            }
            cancellation.throwIfCancelled();
            progress.accept("Building the initial repository version...");
            String treeBody = treeBody(createdFiles, initial.treeSha());
            RestResponse treeResponse = api.post(URI.create(RestClient.API_ROOT
                    + "/repos/" + createdPath + "/git/trees"), session.tokenForRequest(), treeBody, cancellation);
            if (treeResponse.statusCode() != 201) throw ApiErrors.from(treeResponse, false);
            String treeSha = responseSha(treeResponse, "tree");

            String commitBody = "{\"message\":" + quote("Create repository from " + folder.name() + " template")
                    + ",\"tree\":" + quote(treeSha) + ",\"parents\":["
                    + quote(initial.commitSha()) + "]}";
            RestResponse commitResponse = api.post(URI.create(RestClient.API_ROOT
                    + "/repos/" + createdPath + "/git/commits"), session.tokenForRequest(),
                    commitBody, cancellation);
            if (commitResponse.statusCode() != 201) throw ApiErrors.from(commitResponse, false);
            String commitSha = responseSha(commitResponse, "commit");

            cancellation.throwIfCancelled();
            progress.accept("Publishing the initial branch...");
            String refBody = "{\"sha\":" + quote(commitSha) + ",\"force\":false}";
            RestResponse refResponse = api.patch(URI.create(RestClient.API_ROOT
                    + "/repos/" + createdPath + "/git/refs/heads/" + encodeSegment(branch)),
                    session.tokenForRequest(), refBody, cancellation);
            if (refResponse.statusCode() != 200) throw ApiErrors.from(refResponse, false);
            return created;
        } catch (java.util.concurrent.CancellationException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Repository " + created.getFullName()
                    + " was created, but copying the template was interrupted. "
                    + "The new repository may be empty or incomplete.", ex);
        } catch (IOException | RuntimeException ex) {
            throw new IOException("Repository " + created.getFullName()
                    + " was created, but GitHub could not finish copying the template folder: "
                    + safeMessage(ex) + " The new repository may be empty or incomplete.", ex);
        }
    }

    private List<TemplateFile> loadTemplateFiles(GitHubSession session, String fullName,
            RepositoryTemplateFolder folder, CancellationToken cancellation, Consumer<String> progress)
            throws IOException, InterruptedException {
        URI treeUri = URI.create(RestClient.API_ROOT + "/repos/" + encodePath(fullName)
                + "/git/trees/" + encodeSegment(folder.treeSha()) + "?recursive=1");
        RestResponse treeResponse = api.get(treeUri, session.tokenForRequest(), cancellation);
        if (treeResponse.statusCode() != 200) throw ApiErrors.from(treeResponse, false);
        List<Map<String, Object>> blobs;
        try {
            Map<String, Object> tree = JsonObjects.object(JsonParser.parse(treeResponse.body()), "template tree");
            if (JsonObjects.bool(tree, "truncated", false)) {
                throw new IOException("The selected template folder is too large to copy safely.");
            }
            Object entries = tree.get("tree");
            if (!(entries instanceof List<?> rawEntries)) throw new IllegalArgumentException("Missing tree entries.");
            blobs = new ArrayList<>();
            for (Object raw : rawEntries) {
                Map<String, Object> entry = JsonObjects.object(raw, "template tree entry");
                String path = JsonObjects.string(entry, "path");
                String type = JsonObjects.stringOrEmpty(entry, "type");
                if ("tree".equals(type)) continue;
                if (!"blob".equals(type)) {
                    throw new IOException("Template folders cannot contain nested repositories or special Git links.");
                }
                validateRelativePath(path);
                String mode = JsonObjects.string(entry, "mode");
                if (!mode.equals("100644") && !mode.equals("100755")) {
                    throw new IOException("Template folders cannot contain symbolic links or special file types.");
                }
                blobs.add(entry);
                if (blobs.size() > MAX_TEMPLATE_FILES) {
                    throw new IOException("The selected template has more than " + MAX_TEMPLATE_FILES + " files.");
                }
            }
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("The template repository returned invalid file data.");
        }
        if (blobs.isEmpty()) throw new IOException("The selected template folder is empty.");

        List<TemplateFile> files = new ArrayList<>(blobs.size());
        long totalBytes = 0;
        for (int index = 0; index < blobs.size(); index++) {
            cancellation.throwIfCancelled();
            Map<String, Object> entry = blobs.get(index);
            String path = JsonObjects.string(entry, "path");
            String sha = JsonObjects.string(entry, "sha");
            progress.accept("Reading template files (" + (index + 1) + " of " + blobs.size() + ")...");
            URI blobUri = URI.create(RestClient.API_ROOT + "/repos/" + encodePath(fullName)
                    + "/git/blobs/" + encodeSegment(sha));
            RestResponse blobResponse = api.get(blobUri, session.tokenForRequest(), cancellation);
            if (blobResponse.statusCode() != 200) throw ApiErrors.from(blobResponse, false);
            try {
                Map<String, Object> blob = JsonObjects.object(JsonParser.parse(blobResponse.body()), "template blob");
                if (!"base64".equalsIgnoreCase(JsonObjects.string(blob, "encoding"))) {
                    throw new IOException("GitHub returned an unsupported template file encoding.");
                }
                // Empty files are valid Git blobs and are represented by an empty Base64 string.
                byte[] bytes = Base64.getMimeDecoder().decode(JsonObjects.stringOrEmpty(blob, "content"));
                totalBytes += bytes.length;
                if (totalBytes > MAX_TEMPLATE_BYTES) {
                    java.util.Arrays.fill(bytes, (byte) 0);
                    throw new IOException("The selected template exceeds the 25 MB copy limit.");
                }
                files.add(new TemplateFile(path, JsonObjects.string(entry, "mode"), bytes));
            } catch (IllegalArgumentException | ArithmeticException ex) {
                throw new IOException("GitHub returned invalid template file contents.");
            }
        }
        return files;
    }

    private RepositoryTemplateFolder findTemplateFolder(GitHubSession session, String fullName,
            RepositoryMetadata metadata, String folderName, CancellationToken cancellation)
            throws IOException, InterruptedException {
        if (folderName == null || folderName.isBlank()) throw new IOException("Choose a template folder.");
        URI contentsUri = URI.create(RestClient.API_ROOT + "/repos/" + encodePath(fullName)
                + "/contents?ref=" + encodeSegment(metadata.defaultBranch()));
        RestResponse response = api.get(contentsUri, session.tokenForRequest(), cancellation);
        if (response.statusCode() != 200) throw ApiErrors.from(response, false);
        try {
            Object parsed = JsonParser.parse(response.body());
            if (!(parsed instanceof List<?> entries)) throw new IllegalArgumentException("Expected root listing.");
            for (Object raw : entries) {
                Map<String, Object> entry = JsonObjects.object(raw, "template root entry");
                if ("dir".equals(JsonObjects.stringOrEmpty(entry, "type"))
                        && folderName.equals(JsonObjects.stringOrEmpty(entry, "name"))) {
                    return new RepositoryTemplateFolder(folderName, JsonObjects.string(entry, "sha"));
                }
            }
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("The template repository returned invalid folder data.");
        }
        throw new IOException("The selected folder is no longer present in the template repository.");
    }

    private RepositoryMetadata repositoryMetadata(GitHubSession session, String fullName,
            CancellationToken cancellation) throws IOException, InterruptedException {
        URI uri = URI.create(RestClient.API_ROOT + "/repos/" + encodePath(fullName));
        RestResponse response = api.get(uri, session.tokenForRequest(), cancellation);
        if (response.statusCode() != 200) throw ApiErrors.from(response, false);
        try {
            Map<String, Object> repository = JsonObjects.object(JsonParser.parse(response.body()),
                    "template repository");
            String resolvedName = JsonObjects.string(repository, "full_name");
            if (!resolvedName.equalsIgnoreCase(fullName)) throw new IllegalArgumentException("Unexpected repository.");
            return new RepositoryMetadata(JsonObjects.string(repository, "default_branch"));
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("The configured template repository has invalid metadata.");
        }
    }

    private InitialCommit readInitialCommit(GitHubSession session, String repositoryPath, String branch,
            CancellationToken cancellation) throws IOException, InterruptedException {
        URI refUri = URI.create(RestClient.API_ROOT + "/repos/" + repositoryPath
                + "/git/ref/heads/" + encodeSegment(branch));
        RestResponse refResponse = api.get(refUri, session.tokenForRequest(), cancellation);
        if (refResponse.statusCode() != 200) throw ApiErrors.from(refResponse, false);

        String commitSha;
        try {
            Map<String, Object> ref = JsonObjects.object(JsonParser.parse(refResponse.body()), "initial branch ref");
            Map<String, Object> object = JsonObjects.object(ref.get("object"), "initial branch object");
            if (!"commit".equals(JsonObjects.string(object, "type"))) {
                throw new IllegalArgumentException("The default branch does not point to a commit.");
            }
            commitSha = JsonObjects.string(object, "sha");
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("GitHub returned invalid initial branch data.");
        }

        URI commitUri = URI.create(RestClient.API_ROOT + "/repos/" + repositoryPath
                + "/git/commits/" + encodeSegment(commitSha));
        RestResponse commitResponse = api.get(commitUri, session.tokenForRequest(), cancellation);
        if (commitResponse.statusCode() != 200) throw ApiErrors.from(commitResponse, false);
        try {
            Map<String, Object> commit = JsonObjects.object(JsonParser.parse(commitResponse.body()), "initial commit");
            Map<String, Object> tree = JsonObjects.object(commit.get("tree"), "initial commit tree");
            return new InitialCommit(commitSha, JsonObjects.string(tree, "sha"));
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("GitHub returned invalid initial commit data.");
        }
    }

    private static GitHubRepository parseCreatedRepository(RestResponse response) throws IOException {
        try {
            return GitHubRepositoryService.parseRepository(
                    JsonObjects.object(JsonParser.parse(response.body()), "new repository"));
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("GitHub created the repository but returned invalid repository metadata.");
        }
    }

    private static String responseSha(RestResponse response, String description) throws IOException {
        try {
            Map<String, Object> object = JsonObjects.object(JsonParser.parse(response.body()), description);
            return JsonObjects.string(object, "sha");
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("GitHub returned invalid " + description + " metadata.");
        }
    }

    private static String treeBody(List<CreatedFile> files, String baseTreeSha) {
        StringBuilder body = new StringBuilder("{\"base_tree\":").append(quote(baseTreeSha)).append(",\"tree\":[");
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) body.append(',');
            CreatedFile file = files.get(i);
            body.append("{\"path\":").append(quote(file.path()))
                    .append(",\"mode\":").append(quote(file.mode()))
                    .append(",\"type\":\"blob\",\"sha\":").append(quote(file.sha())).append('}');
        }
        if (files.stream().noneMatch(file -> file.path().equals("README.md"))) {
            if (!files.isEmpty()) body.append(',');
            body.append("{\"path\":\"README.md\",\"sha\":null}");
        }
        return body.append("]}").toString();
    }

    private static void validateRelativePath(String path) throws IOException {
        if (path.isBlank() || path.startsWith("/") || path.contains("\\")
                || java.util.Arrays.stream(path.split("/", -1))
                        .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."))) {
            throw new IOException("A template file has an unsafe path.");
        }
    }

    public static String normalizeTemplateRepository(String value) throws IOException {
        if (value == null || value.isBlank()) throw new IOException("Configure the template repository in Preferences.");
        String trimmed = value.trim();
        String fullName;
        if (trimmed.startsWith("https://github.com/")) {
            String path = trimmed.substring("https://github.com/".length());
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
            fullName = path;
        } else {
            fullName = trimmed;
        }
        String[] parts = fullName.split("/", -1);
        if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
                || !parts[1].matches("[A-Za-z0-9_.-]{1,100}")
                || parts[1].equals(".") || parts[1].equals("..")) {
            throw new IOException("Template repository must be a GitHub URL or owner/repository.");
        }
        return parts[0] + "/" + parts[1];
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodePath(String fullName) {
        return String.join("/", java.util.Arrays.stream(fullName.split("/", -1))
                .map(RepositoryManagementService::encodeSegment).toList());
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "unexpected GitHub API error." : error.getMessage();
    }

    private record RepositoryMetadata(String defaultBranch) { }
    private record InitialCommit(String commitSha, String treeSha) { }
    private record TemplateFile(String path, String mode, byte[] bytes) { }
    private record CreatedFile(String path, String mode, String sha) { }
}
