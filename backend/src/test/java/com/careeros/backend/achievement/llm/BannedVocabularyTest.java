package com.careeros.backend.achievement.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BannedVocabularyTest {

    @Test
    void catchesExactAndInflectedForms() {
        assertThat(BannedVocabulary.violationsIn(
                "I significantly enhanced the robust, seamless system by leveraging it."))
                .containsExactlyInAnyOrder("significantly", "enhanced", "robust", "seamless", "leveraging");
    }

    @Test
    void catchesMultiWordPhrases() {
        assertThat(BannedVocabulary.violationsIn(
                "This prevented unauthorized access and gave improved reliability."))
                .containsExactlyInAnyOrder("unauthorized access", "improved reliability");
    }

    @Test
    void cleanTextHasNoViolations() {
        assertThat(BannedVocabulary.violationsIn(
                "I rerouted two controllers through CurrentUserService."))
                .isEmpty();
    }

    @Test
    void nullAndBlankAreSafe() {
        assertThat(BannedVocabulary.violationsIn(null)).isEmpty();
        assertThat(BannedVocabulary.violationsIn("")).isEmpty();
    }
}
