package com.careeros.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts GitHub access tokens at rest. The key comes from an environment
 * variable (GITHUB_TOKEN_ENCRYPTION_KEY, see application.properties) so it's
 * never checked into source — the app fails to start without it rather than
 * silently falling back to a hardcoded key.
 */
@Component
public class GithubTokenEncryptor {

    // Not secret: TextEncryptor only uses this to derive the key alongside
    // the password below, not as key material by itself. Fixed so ciphertext
    // written by one instance stays decryptable by every other.
    private static final String SALT = "c6a3f1e9d24b7085";

    private final TextEncryptor encryptor;

    public GithubTokenEncryptor(@Value("${app.github-token.encryption-key}") String key) {
        this.encryptor = Encryptors.text(key, SALT);
    }

    public String encrypt(String plaintext) {
        return encryptor.encrypt(plaintext);
    }

    /** @throws IllegalStateException if the user has no stored token — they need to reconnect GitHub. */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            throw new IllegalStateException(
                    "No GitHub token on file for this user — reconnect GitHub to continue.");
        }
        return encryptor.decrypt(ciphertext);
    }
}
