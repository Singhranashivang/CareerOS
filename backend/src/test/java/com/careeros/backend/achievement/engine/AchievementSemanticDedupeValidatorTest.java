package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.record.AchievementEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementSemanticDedupeValidatorTest {

    private final AchievementSemanticDedupeValidator validator = new AchievementSemanticDedupeValidator();

    private static AchievementEntity existing(String title, String resumeBullet) {
        return AchievementEntity.builder().title(title).resumeBullet(resumeBullet).build();
    }

    @Test
    void flagsANearParaphraseTitleAsADuplicate() {
        // Real CareerOS pair — same subsystem, two clusters weeks apart,
        // zero shared commit SHAs, near-paraphrase titles.
        var existingList = List.of(existing(
                "AI-Powered Achievement Generation",
                "Developed an AI-powered system for generating achievements based on code changes."));

        var result = validator.duplicateOf(
                "Enhanced Achievement Generation in Repository Knowledge Management",
                "Refactored evidence analyzers and feature extractors for the achievement engine.",
                existingList);

        assertThat(result).isPresent();
    }

    @Test
    void allowsAGenuinelyDifferentAchievement() {
        var existingList = List.of(existing(
                "AI-Powered Achievement Generation",
                "Developed an AI-powered system for generating achievements based on code changes."));

        var result = validator.duplicateOf(
                "Added Pagination to the GitHub Commit Sync",
                "Built a Link-header paginator so repositories with more than 100 commits sync in full.",
                existingList);

        assertThat(result).isEmpty();
    }

    @Test
    void emptyExistingListNeverMatches() {
        var result = validator.duplicateOf("Anything", "Anything at all", List.of());

        assertThat(result).isEmpty();
    }
}
