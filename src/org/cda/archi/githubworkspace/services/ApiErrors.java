package org.cda.archi.githubworkspace.services;

import java.time.Instant;
import java.util.Map;

import org.cda.archi.githubworkspace.clients.RestResponse;
import org.cda.archi.githubworkspace.infrastructure.JsonParser;

/** Converts GitHub HTTP/rate-limit responses into safe, actionable messages. */
final class ApiErrors {
    private ApiErrors() { }

    static GitHubApiException from(RestResponse response, boolean authenticationRequest) {
        int status = response.statusCode();
        String message = switch (status) {
            case 401 -> authenticationRequest
                    ? "Git returned a credential, but GitHub rejected it as a REST API bearer token. "
                      + "It may be a Git-only credential, expired, or revoked. No alternate sign-in was attempted."
                    : "GitHub rejected the session. Connect again to revalidate the local Git credential.";
            case 403, 429 -> rateLimitMessage(response);
            case 404 -> authenticationRequest
                    ? "GitHub could not resolve the authenticated user."
                    : "The organization is unavailable to this account, or does not exist.";
            case 422 -> "GitHub rejected the organization/topic search query (HTTP 422).";
            default -> status >= 500
                    ? "GitHub is temporarily unavailable (HTTP " + status + ")."
                    : "GitHub API request failed (HTTP " + status + ").";
        };
        return new GitHubApiException(status, message);
    }

    private static String rateLimitMessage(RestResponse response) {
        String remaining = response.headers().firstValue("X-RateLimit-Remaining").orElse("");
        String reset = response.headers().firstValue("X-RateLimit-Reset").orElse("");
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        String body = response.body() == null ? "" : response.body().toLowerCase(java.util.Locale.ROOT);
        if ("0".equals(remaining)) {
            String when = parseReset(reset);
            return "GitHub's primary API rate limit has been reached." + (when.isEmpty() ? "" : " Resets " + when + ".");
        }
        if (!retryAfter.isBlank() || body.contains("secondary rate limit")) {
            return "GitHub's secondary rate limit is active."
                    + (retryAfter.isBlank() ? "" : " Retry after " + retryAfter + " seconds.");
        }
        return "GitHub denied the request (HTTP " + response.statusCode()
                + "). The account may lack access or be rate limited.";
    }

    private static String parseReset(String epoch) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(epoch)).toString();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
