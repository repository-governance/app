package org.cda.archi.githubworkspace.services;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.cda.archi.githubworkspace.clients.GitHubApiClient;
import org.cda.archi.githubworkspace.clients.RestResponse;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.models.GitHubRepository;

/** Authenticated, deterministic REST tests for progressive catalog thumbnail loading. */
public final class ThumbnailServiceTests {
    private ThumbnailServiceTests() { }

    public static int run() throws Exception {
        int[] assertions = {0};
        byte[] png = png(2, 3);
        String encoded = Base64.getEncoder().encodeToString(png);
        List<URI> uris = new ArrayList<>();
        int[] calls = {0};
        GitHubApiClient api = (uri, token, cancellation) -> {
            calls[0]++;
            uris.add(uri);
            require(assertions, "secret-test-token".equals(token),
                    "Authenticated API request receives the current session token");
            require(assertions, "api.github.com".equals(uri.getHost()), "Thumbnail requests stay on GitHub API host");
            require(assertions, !uri.toString().contains("secret-test-token"), "Credentials are absent from request URLs");
            String body;
            if (uri.getPath().endsWith("/contents/thumbnails")) {
                body = "[{\"name\":\"z.png\",\"type\":\"file\"},{\"name\":\"cover.gif\",\"type\":\"file\"},"
                        + "{\"name\":\"folder\",\"type\":\"dir\"},{\"name\":\"a.png\",\"type\":\"file\"}]";
            } else {
                require(assertions, uri.getPath().endsWith("/thumbnails/a.png"),
                        "The first supported PNG is selected alphabetically");
                body = "{\"encoding\":\"base64\",\"content\":\"" + encoded + "\"}";
            }
            return new RestResponse(200, body,
                    java.net.http.HttpHeaders.of(Map.of(), (name, value) -> true));
        };
        GitHubRepositoryService service = new GitHubRepositoryService(api);
        GitHubRepository repository = repository("SBB-009999", "trunk");
        GitHubSession session = new GitHubSession(
                new org.cda.archi.githubworkspace.models.GitHubUser(7, "test-user", "", ""),
                "secret-test-token".toCharArray());
        try {
            byte[] first = service.loadThumbnail(session, repository, new CancellationToken());
            require(assertions, java.util.Arrays.equals(png, first), "PNG contents are returned as image bytes");
            require(assertions, calls[0] == 2, "Directory listing and selected image use two REST calls");
            require(assertions, uris.stream().allMatch(uri -> uri.getRawQuery().contains("ref=trunk")),
                    "Thumbnail API is scoped to the repository default branch");
            byte[] second = service.loadThumbnail(session, repository, new CancellationToken());
            require(assertions, java.util.Arrays.equals(first, second), "Cached thumbnail content is reused");
            require(assertions, calls[0] == 2, "Repeated visible-card requests do not call the API again");
            java.util.Arrays.fill(first, (byte) 0);
            java.util.Arrays.fill(second, (byte) 0);
            service.clearThumbnailCache();
            require(assertions, calls[0] == 2, "Clearing cache does not create extra requests");

            int[] noImageCalls = {0};
            GitHubRepositoryService noImageService = new GitHubRepositoryService((uri, token, cancellation) -> {
                noImageCalls[0]++;
                return new RestResponse(200,
                        "[{\"name\":\"cover.gif\",\"type\":\"file\"},{\"name\":\"photo.jpg\",\"type\":\"file\"}]",
                        java.net.http.HttpHeaders.of(Map.of(), (name, value) -> true));
            });
            GitHubRepository noImageRepository = repository("SBB-010000", "trunk");
            byte[] absent = noImageService.loadThumbnail(session, noImageRepository, new CancellationToken());
            require(assertions, absent.length == 0, "Unsupported image formats use the placeholder path");
            noImageService.loadThumbnail(session, noImageRepository, new CancellationToken());
            require(assertions, noImageCalls[0] == 1,
                    "Repositories without PNG files are cached without duplicate API requests");
            noImageService.clearThumbnailCache();
            return assertions[0];
        } finally {
            session.close();
            service.clearThumbnailCache();
            java.util.Arrays.fill(png, (byte) 0);
        }
    }

    private static byte[] png(int width, int height) {
        byte[] bytes = new byte[24];
        byte[] signature = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        ByteBuffer.wrap(bytes, 16, 4).putInt(width);
        ByteBuffer.wrap(bytes, 20, 4).putInt(height);
        return bytes;
    }

    private static GitHubRepository repository(String name, String defaultBranch) {
        return new GitHubRepository(9001, name, "repository-governance/" + name, "", "private", true,
                List.of("archimate"), "https://github.com/repository-governance/" + name,
                "https://github.com/repository-governance/" + name + ".git",
                "git@github.com:repository-governance/" + name + ".git", defaultBranch, null);
    }

    private static void require(int[] assertions, boolean value, String message) {
        assertions[0]++;
        if (!value) throw new AssertionError(message);
    }
}
