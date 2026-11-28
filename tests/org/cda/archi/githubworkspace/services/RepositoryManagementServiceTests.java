package org.cda.archi.githubworkspace.services;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.cda.archi.githubworkspace.clients.GitHubApiClient;
import org.cda.archi.githubworkspace.clients.RestResponse;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.GitHubUser;

/** API-contract regressions for organization repository creation from a template folder. */
public final class RepositoryManagementServiceTests {
    private RepositoryManagementServiceTests() { }

    public static int run() throws Exception {
        testOrganizationRepositoryListing();
        testTemplateFolderListingAndCopy();
        testUnsafeTemplateFilesAreRejectedBeforeRemoteCreation();
        testTemplateRepositoryValidation();
        return 13;
    }

    private static void testOrganizationRepositoryListing() throws Exception {
        GitHubSession session = session();
        try {
            List<GitHubRepository> repositories = new RepositoryManagementService(new RecordingApi())
                    .listOrganizationRepositories(session, "target-org", new CancellationToken());
            require(repositories.size() == 2
                    && repositories.get(0).getName().equals("archimate-lowercase")
                    && repositories.get(1).getName().equals("archimate-mixedcase"),
                    "Management filters organization repositories to the fixed default topic");
        } finally {
            session.close();
        }
    }

    private static void testTemplateFolderListingAndCopy() throws Exception {
        RecordingApi api = new RecordingApi();
        RepositoryManagementService service = new RepositoryManagementService(api);
        GitHubSession session = session();
        try {
            var folders = service.listTemplateFolders(session, "https://github.com/source/templates",
                    new CancellationToken());
            require(folders.size() == 2 && folders.get(0).name().equals("Architecture")
                    && folders.get(1).name().equals("Governance"),
                    "All root-level template folders are listed alphabetically");

            List<String> progress = new ArrayList<>();
            GitHubRepository created = service.createFromTemplateFolder(session, "target-org",
                    "source/templates", "Architecture", "SBB-010001", "Created from a model template",
                    false, new CancellationToken(), progress::add);
            require(created.getFullName().equals("target-org/SBB-010001"),
                    "The new repository is created inside the configured organization");
            require(api.postRequests.size() == 5 && api.patchRequests.size() == 1 && api.putRequests.size() == 1,
                    "Creation posts repository, blobs, tree and commit, advances its ref, and assigns the topic");
            require(api.postRequests.get(0).body().contains("\"private\":false")
                    && api.postRequests.get(0).body().contains("\"name\":\"SBB-010001\"")
                    && api.postRequests.get(0).body().contains("\"auto_init\":true"),
                    "Repository creation initializes GitHub's default branch before writing Git objects");
            require(api.postRequests.get(3).body().contains("\"base_tree\":\"initial-tree\"")
                    && api.postRequests.get(3).body().contains("\"path\":\"source/design.archimate\"")
                    && api.postRequests.get(3).body().contains("\"path\":\".gitkeep\"")
                    && api.postRequests.get(3).body().contains("\"path\":\"README.md\",\"sha\":null"),
                    "Template files extend the initial tree and remove GitHub's generated README");
            require(api.postRequests.get(4).body().contains("\"parents\":[\"initial-commit\"]"),
                    "The template commit retains the auto-initialization commit as its parent");
            require(api.patchRequests.get(0).uri().getPath()
                    .equals("/repos/target-org/SBB-010001/git/refs/heads/master")
                    && api.patchRequests.get(0).body().contains("\"force\":false"),
                    "The initialized default branch is advanced safely to the template commit");
            require(api.putRequests.get(0).uri().getPath().equals("/repos/target-org/SBB-010001/topics")
                    && api.putRequests.get(0).body().contains("\"names\":[\"archimate\"]"),
                    "New repositories are assigned the fixed default topic");
            require(progress.stream().anyMatch(item -> item.contains("Publishing")),
                    "The create flow reports progress through initial publication");
        } finally {
            session.close();
        }
    }

    private static void testUnsafeTemplateFilesAreRejectedBeforeRemoteCreation() throws Exception {
        RecordingApi api = new RecordingApi();
        api.unsafeSymlink = true;
        RepositoryManagementService service = new RepositoryManagementService(api);
        GitHubSession session = session();
        try {
            try {
                service.createFromTemplateFolder(session, "target-org", "source/templates",
                        "Architecture", "SBB-010002", "", true, new CancellationToken(), ignored -> { });
                throw new AssertionError("Template symlinks must be rejected.");
            } catch (java.io.IOException expected) {
                require(api.postRequests.isEmpty(), "Unsafe template entries are rejected before creating a repo");
            }
        } finally {
            session.close();
        }
    }

    private static void testTemplateRepositoryValidation() throws Exception {
        require(RepositoryManagementService.normalizeTemplateRepository(
                "https://github.com/source/templates.git/").equals("source/templates"),
                "A GitHub URL is normalized to owner/repository");
        try {
            RepositoryManagementService.normalizeTemplateRepository("https://evil.example/source/templates");
            throw new AssertionError("Non-GitHub template URLs must be rejected.");
        } catch (java.io.IOException expected) {
            // Expected: authorization and source reads are restricted to GitHub.
        }
    }

    private static GitHubSession session() {
        return new GitHubSession(new GitHubUser(123, "test-user", "Test User",
                "https://github.com/test-user"), "test-token".toCharArray());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static RestResponse response(int status, String body) {
        return new RestResponse(status, body, HttpHeaders.of(Map.of(), (name, value) -> true));
    }

    private record Posted(URI uri, String body) { }

    private static final class RecordingApi implements GitHubApiClient {
        private final List<Posted> postRequests = new ArrayList<>();
        private final List<Posted> patchRequests = new ArrayList<>();
        private final List<Posted> putRequests = new ArrayList<>();
        private boolean unsafeSymlink;

        @Override
        public RestResponse get(URI uri, String token, CancellationToken cancellation) {
            cancellation.throwIfCancelled();
            String path = uri.getPath();
            if (path.equals("/repos/source/templates")) {
                return response(200, "{\"full_name\":\"source/templates\",\"default_branch\":\"master\"}");
            }
            if (path.equals("/orgs/target-org/repos")) {
                String lowercase = repositoryJson("target-org/archimate-lowercase")
                        .replace("\"topics\":[]", "\"topics\":[\"archimate\"]");
                String mixedCase = repositoryJson("target-org/archimate-mixedcase")
                        .replace("\"topics\":[]", "\"topics\":[\"governance\",\"ARCHIMATE\"]");
                String governanceOnly = repositoryJson("target-org/governance-only")
                        .replace("\"topics\":[]", "\"topics\":[\"governance\"]");
                return response(200, "[" + lowercase + "," + mixedCase + ","
                        + governanceOnly + "," + repositoryJson("target-org/untagged") + "]");
            }
            if (path.equals("/repos/source/templates/contents")) {
                return response(200, """
                        [
                          {"name":"Governance","type":"dir","sha":"tree-governance"},
                          {"name":"README.md","type":"file","sha":"blob-readme"},
                          {"name":"Architecture","type":"dir","sha":"tree-architecture"}
                        ]
                        """);
            }
            if (path.equals("/repos/source/templates/git/trees/tree-architecture")) {
                String mode = unsafeSymlink ? "120000" : "100644";
                return response(200, "{\"truncated\":false,\"tree\":["
                        + "{\"path\":\"source\",\"mode\":\"040000\",\"type\":\"tree\",\"sha\":\"source-tree\"},"
                        + "{\"path\":\"source/design.archimate\",\"mode\":\"" + mode
                        + "\",\"type\":\"blob\",\"sha\":\"template-blob\"},"
                        + "{\"path\":\".gitkeep\",\"mode\":\"100644\",\"type\":\"blob\","
                        + "\"sha\":\"empty-blob\"}]}");
            }
            if (path.equals("/repos/source/templates/git/blobs/template-blob")) {
                String content = Base64.getEncoder().encodeToString(
                        "template model".getBytes(StandardCharsets.UTF_8));
                return response(200, "{\"encoding\":\"base64\",\"content\":\"" + content + "\"}");
            }
            if (path.equals("/repos/source/templates/git/blobs/empty-blob")) {
                return response(200, "{\"encoding\":\"base64\",\"content\":\"\"}");
            }
            if (path.equals("/repos/target-org/SBB-010001/git/ref/heads/master")) {
                return response(200, "{\"ref\":\"refs/heads/master\",\"object\":"
                        + "{\"sha\":\"initial-commit\",\"type\":\"commit\"}}");
            }
            if (path.equals("/repos/target-org/SBB-010001/git/commits/initial-commit")) {
                return response(200, "{\"sha\":\"initial-commit\",\"tree\":{\"sha\":\"initial-tree\"}}");
            }
            throw new AssertionError("Unexpected API GET " + uri);
        }

        @Override
        public RestResponse post(URI uri, String token, String body, CancellationToken cancellation) {
            cancellation.throwIfCancelled();
            postRequests.add(new Posted(uri, body));
            String path = uri.getPath();
            if (path.equals("/orgs/target-org/repos")) {
                return response(201, repositoryJson("target-org/SBB-010001"));
            }
            if (path.equals("/repos/target-org/SBB-010001/git/blobs")) {
                return response(201, "{\"sha\":\"target-blob-" + postRequests.size() + "\"}");
            }
            if (path.equals("/repos/target-org/SBB-010001/git/trees")) {
                return response(201, "{\"sha\":\"target-tree\"}");
            }
            if (path.equals("/repos/target-org/SBB-010001/git/commits")) {
                return response(201, "{\"sha\":\"target-commit\"}");
            }
            if (path.equals("/repos/target-org/SBB-010001/git/refs")) return response(201, "{}");
            throw new AssertionError("Unexpected API POST " + uri);
        }

        @Override
        public RestResponse patch(URI uri, String token, String body, CancellationToken cancellation) {
            cancellation.throwIfCancelled();
            patchRequests.add(new Posted(uri, body));
            if (uri.getPath().equals("/repos/target-org/SBB-010001/git/refs/heads/master")) {
                return response(200, "{\"ref\":\"refs/heads/master\"}");
            }
            throw new AssertionError("Unexpected API PATCH " + uri);
        }

        @Override
        public RestResponse put(URI uri, String token, String body, CancellationToken cancellation) {
            cancellation.throwIfCancelled();
            putRequests.add(new Posted(uri, body));
            if (uri.getPath().equals("/repos/target-org/SBB-010001/topics")) {
                return response(200, "{\"names\":[\"archimate\"]}");
            }
            throw new AssertionError("Unexpected API PUT " + uri);
        }

        private static String repositoryJson(String fullName) {
            String name = fullName.substring(fullName.indexOf('/') + 1);
            return "{\"id\":1,\"name\":\"" + name + "\",\"full_name\":\"" + fullName
                    + "\",\"html_url\":\"https://github.com/" + fullName
                    + "\",\"private\":false,\"visibility\":\"public\",\"description\":\"\","
                    + "\"topics\":[],\"clone_url\":\"https://github.com/" + fullName
                    + ".git\",\"ssh_url\":\"git@github.com:" + fullName + ".git\","
                    + "\"default_branch\":\"master\"}";
        }
    }
}
