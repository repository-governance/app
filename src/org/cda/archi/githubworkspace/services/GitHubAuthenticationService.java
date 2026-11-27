package org.cda.archi.githubworkspace.services;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.cda.archi.githubworkspace.clients.GitHubApiClient;
import org.cda.archi.githubworkspace.clients.RestResponse;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.JsonParser;
import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.models.GitCredential;
import org.cda.archi.githubworkspace.models.GitHubUser;

/** Validates the local Git credential against GitHub's user identity endpoint. */
public final class GitHubAuthenticationService {
    private final GitCredentialService credentials;
    private final GitHubApiClient api;

    public GitHubAuthenticationService(GitCredentialService credentials, GitHubApiClient api) {
        this.credentials = credentials;
        this.api = api;
    }

    public GitHubSession authenticate(GitConfiguration git, CancellationToken cancellation)
            throws IOException, InterruptedException {
        cancellation.throwIfCancelled();
        GitCredential credential = credentials.retrieve(git, cancellation);
        char[] tokenChars = credential.copyPassword();
        try (credential) {
            String candidate = new String(tokenChars);
            RestResponse response = api.get(URI.create("https://api.github.com/user"), candidate, cancellation);
            if (response.statusCode() != 200) throw ApiErrors.from(response, true);
            Map<String, Object> json = JsonObjects.object(JsonParser.parse(response.body()), "GitHub user");
            long id = JsonObjects.integer(json, "id", 0);
            GitHubUser user = new GitHubUser(id, JsonObjects.string(json, "login"),
                    JsonObjects.stringOrEmpty(json, "name"), JsonObjects.stringOrEmpty(json, "html_url"));
            return new GitHubSession(user, tokenChars);
        } catch (CancellationException ex) {
            throw ex;
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new IOException("GitHub returned an invalid user response.");
        } finally {
            java.util.Arrays.fill(tokenChars, '\0');
        }
    }
}
