package com.careeros.backend.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubTokenEncryptorTest {

    private final GithubTokenEncryptor encryptor = new GithubTokenEncryptor("test-only-key");

    @Test
    void roundTripsAndDoesNotStorePlaintext() {
        String plaintext = "gho_realGithubTokenLooksLikeThis";

        String ciphertext = encryptor.encrypt(plaintext);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void sameInputEncryptsDifferentlyEachTime() {
        String plaintext = "gho_sameToken";

        // Random IV per call — equal ciphertexts would leak that two users
        // (or two generations) share a token.
        assertThat(encryptor.encrypt(plaintext)).isNotEqualTo(encryptor.encrypt(plaintext));
    }

    @Test
    void missingTokenFailsWithAClearMessageInsteadOfANpe() {
        assertThatThrownBy(() -> encryptor.decrypt(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reconnect");
    }
}
