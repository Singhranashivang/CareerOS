package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.record.AchievementEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedInPromptBuilderTest {

    private final LinkedInPromptBuilder builder = new LinkedInPromptBuilder();

    @Test
    void generatorSourcedAchievementIncludesResumeBulletAndStarButNotSummaryOrTheLimitedEvidenceNote() {
        AchievementEntity achievement = AchievementEntity.builder()
                .title("Refactored auth routes")
                .resumeBullet("Refactored auth routes through CurrentUserService")
                .starSituation("Controllers resolved the user inline")
                .starTask("Route every controller through the shared service")
                .starAction("Rewired each controller by hand")
                .starResult("Removed the duplicated logic")
                .build();

        String prompt = builder.build(achievement);

        assertThat(prompt).contains("Resume bullet:").contains("Situation:")
                .contains("Task:").contains("Action:").contains("Result:");
        assertThat(prompt).doesNotContain("Summary:");
        assertThat(prompt).doesNotContain("NOTE: Only a title and summary");
    }

    @Test
    void weeklyPipelineAchievementIncludesOnlyTitleAndSummaryPlusTheLimitedEvidenceNote() {
        AchievementEntity achievement = AchievementEntity.builder()
                .title("Hacktoberfest progress")
                .summary("Added an F1 race predictor and a few algorithm solutions.")
                .build();

        String prompt = builder.build(achievement);

        assertThat(prompt).contains("Title:").contains("Summary:");
        assertThat(prompt).doesNotContain("Resume bullet:").doesNotContain("Situation:")
                .doesNotContain("Task:").doesNotContain("Action:").doesNotContain("Result:");
        assertThat(prompt).contains("NOTE: Only a title and summary");
    }

    @Test
    void aPartialStarSectionSuppressesTheLimitedEvidenceNote() {
        AchievementEntity achievement = AchievementEntity.builder()
                .title("Partial STAR")
                .starSituation("Only the situation was generated")
                .build();

        String prompt = builder.build(achievement);

        assertThat(prompt).contains("Situation:");
        assertThat(prompt).doesNotContain("Task:").doesNotContain("Action:").doesNotContain("Result:");
        // Some real content exists beyond title — not the "only title/summary" case.
        assertThat(prompt).doesNotContain("NOTE: Only a title and summary");
    }

    @Test
    void blankFieldsAreTreatedTheSameAsNull() {
        AchievementEntity achievement = AchievementEntity.builder()
                .title("Blank fields")
                .resumeBullet("   ")
                .summary("")
                .build();

        String prompt = builder.build(achievement);

        assertThat(prompt).doesNotContain("Resume bullet:").doesNotContain("Summary:");
        assertThat(prompt).contains("NOTE: Only a title and summary");
    }
}
