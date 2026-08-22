package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.generator.AchievementOutput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementFabricationValidatorTest {

    private final AchievementFabricationValidator validator = new AchievementFabricationValidator();

    private static AchievementOutput output(String field) {
        return AchievementOutput.builder().title("x").resumeBullet(field).build();
    }

    @Test
    void rejectsFirstPersonPluralPronoun() {
        var result = validator.fabricationReason(
                output("We built a faster search algorithm for the team"), Evidence.builder().build());

        assertThat(result).isPresent();
        assertThat(result.get()).contains("first-person plural");
    }

    @Test
    void rejectsATechnologyNotInEvidence() {
        Evidence evidence = Evidence.builder().technologies(List.of("Java", "PostgreSQL")).build();

        var result = validator.fabricationReason(
                output("Deployed the service to Kubernetes for better scaling"), evidence);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("kubernetes");
    }

    @Test
    void allowsATechnologyThatIsInEvidence() {
        Evidence evidence = Evidence.builder().technologies(List.of("React", "TypeScript")).build();

        var result = validator.fabricationReason(
                output("Built a React component for the settings page"), evidence);

        assertThat(result).isEmpty();
    }

    @Test
    void allowsPlainTextWithNoPronounOrUnlistedTechnology() {
        Evidence evidence = Evidence.builder().technologies(List.of("Java")).build();

        var result = validator.fabricationReason(
                output("Refactored the commit clustering algorithm to use union-find"), evidence);

        assertThat(result).isEmpty();
    }

    @Test
    void technologyMatchIsCaseInsensitive() {
        Evidence evidence = Evidence.builder().technologies(List.of("react")).build();

        var result = validator.fabricationReason(output("Built a REACT component"), evidence);

        assertThat(result).isEmpty();
    }
}
