package org.cda.archi.githubworkspace.clients;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;

/** Reused HTTP boundary, now constrained to GitHub's HTTPS API origin. */
public final class RestClient implements GitHubApiClient {
    public static final String API_ROOT = "https://api.github.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private final HttpClient httpClient;

    public RestClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    public RestClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public RestResponse get(URI uri, String bearerToken, CancellationToken cancellation)
            throws IOException, InterruptedException {
        validateApiUri(uri);
        cancellation.throwIfCancelled();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        CompletableFuture<HttpResponse<String>> future =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        long deadline = System.nanoTime() + REQUEST_TIMEOUT.toNanos();
        try {
            while (true) {
                cancellation.throwIfCancelled();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException();
                try {
                    HttpResponse<String> response = future.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)),
                            TimeUnit.NANOSECONDS);
                    return new RestResponse(response.statusCode(), response.body(), response.headers());
                } catch (TimeoutException polling) {
                    if (System.nanoTime() >= deadline) throw polling;
                }
            }
        } catch (CancellationException ex) {
            future.cancel(true);
            throw ex;
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new IOException("GitHub request timed out.");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (hasCause(cause, java.net.http.HttpTimeoutException.class)) {
                throw new IOException("GitHub request timed out.");
            }
            if (hasCause(cause, java.net.UnknownHostException.class)) {
                throw new IOException("DNS could not resolve api.github.com.");
            }
            if (hasCause(cause, javax.net.ssl.SSLException.class)) {
                throw new IOException("A secure TLS connection to GitHub could not be established.");
            }
            if (cause instanceof IOException) throw new IOException("Could not connect to the GitHub API.");
            throw new IOException("GitHub request failed.");
        } finally {
            if (cancellation.isCancelled()) future.cancel(true);
        }
    }

    private static void validateApiUri(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"api.github.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new IllegalArgumentException("Authorization is only allowed for https://api.github.com.");
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }
}
