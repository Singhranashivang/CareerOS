package com.careeros.backend.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Exists only to force eager, strict resolution of the OAuth client secret
 * at startup. Spring Boot's ConfigurationProperties binding for
 * spring.security.oauth2.client.registration.github.client-secret does NOT
 * fail on a missing GITHUB_OAUTH_CLIENT_SECRET on its own — an unresolved
 * ${...} placeholder in application.properties is silently accepted as the
 * literal secret value there, so a missing env var would otherwise only
 * surface later as a confusing GitHub OAuth failure instead of a clear
 * startup error. @Value constructor injection resolves strictly (see
 * GithubTokenEncryptor, same pattern), so this bean's construction alone is
 * the fail-fast check — nothing else needs to use it.
 */
@Component
public class GithubOAuthSecretGuard {

    public GithubOAuthSecretGuard(
            @Value("${spring.security.oauth2.client.registration.github.client-secret}") String clientSecret) {
    }
}
