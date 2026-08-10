package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingValidatorTest {

    private final GroundingValidator validator = new GroundingValidator();

    @Test
    void matchesACamelCaseFilenameToken() {
        Evidence evidence = Evidence.builder()
                .changedFiles(List.of("src/main/java/SpiralSearch.java"))
                .build();

        // "Spiral" only appears inside the camelCase filename "SpiralSearch" —
        // the tokenizer has to split that boundary to find it.
        var result = validator.ungroundedReason(
                "Spiral Search Tool", "Built a spiral search utility", evidence);

        assertThat(result).isEmpty();
    }

    @Test
    void matchesACommitMessageTokenWhenNoFileMatches() {
        Evidence evidence = Evidence.builder()
                .features(List.of(Feature.builder()
                        .name("Feature Development")
                        .evidence(List.of("Add rate limiter for outbound webhook calls"))
                        .build()))
                .build();

        var result = validator.ungroundedReason(
                "Rate Limiting", "Added a rate limiter for outbound calls", evidence);

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsAClaimSharingNoTokenWithEitherSource() {
        Evidence evidence = Evidence.builder()
                .changedFiles(List.of("SpiralSearch.java"))
                .features(List.of(Feature.builder()
                        .name("Feature Development")
                        .evidence(List.of("Implement spiral search"))
                        .build()))
                .build();

        var result = validator.ungroundedReason(
                "Cloud Infrastructure Migration",
                "Migrated production workloads to Kubernetes clusters",
                evidence);

        assertThat(result).isPresent();
    }

    @Test
    void shortAndStopwordTokensDoNotCountAsAMatch() {
        // "the", "with" (stopwords) and "cpp" (< 4 chars) all appear in both
        // sides, but none of them should be enough to call this grounded.
        Evidence evidence = Evidence.builder()
                .changedFiles(List.of("x.cpp"))
                .features(List.of(Feature.builder()
                        .name("General Development")
                        .evidence(List.of("Updated the file with the new code"))
                        .build()))
                .build();

        var result = validator.ungroundedReason(
                "Refactored the module",
                "Updated the file with cleaner code",
                evidence);

        assertThat(result).isPresent();
    }
}
