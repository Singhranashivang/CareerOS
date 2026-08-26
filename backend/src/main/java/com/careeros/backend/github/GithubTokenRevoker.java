package com.careeros.backend.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Revokes this OAuth app's grant for a user — DELETE /applications/{client_id}/grant,
 * Basic-authed with the app's own client id/secret (not the user's token),
 * body names which access token to revoke. This invalidates every token
 * GitHub has issued this app for that user, not just the one on file, and is
 * the same call GitHub's own "Revoke access" button makes. Same RestClient
 * bean GithubApiService uses for every other GitHub call.
 */
@Service
@Slf4j
public class GithubTokenRevoker {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public GithubTokenRevoker(
            RestClient restClient,
            @Value("${spring.security.oauth2.client.registration.github.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.github.client-secret}") String clientSecret) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Best-effort — the caller clears the locally stored token regardless of
     * whether this succeeds. A user asking to disconnect wants this app to
     * forget their token even if GitHub's revoke call fails; failing the
     * whole disconnect request over a network blip to GitHub would leave the
     * token sitting in our own database for no benefit to the user.
     */
    public void revoke(String accessToken) {
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri("https://api.github.com/applications/{client_id}/grant", clientId)
                    .headers(h -> h.setBasicAuth(clientId, clientSecret))
                    .header("Accept", "application/vnd.github+json")
                    .body(Map.of("access_token", accessToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to revoke GitHub token via applications/grant: {}", e.getMessage());
        }
    }
}
