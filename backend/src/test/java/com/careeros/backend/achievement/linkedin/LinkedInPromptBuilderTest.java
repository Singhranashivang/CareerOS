package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.record.AchievementEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    @Test
    void theSingleAchievementPromptHasNoMinimumWordCountOnlyAMaximum() {
        AchievementEntity achievement = AchievementEntity.builder().title("x").build();

        String prompt = builder.build(achievement);

        assertThat(prompt).contains("Under 250 words");
        assertThat(prompt).contains("no minimum");
        assertThat(prompt).doesNotContain("150 to 250").doesNotContain("Under 150 words").doesNotContain("140");
    }

    @Test
    void theSingleAchievementPromptRequiresTheHeadlineToBeLiftedFromThePost() {
        AchievementEntity achievement = AchievementEntity.builder().title("x").build();

        String prompt = builder.build(achievement);

        assertThat(prompt).contains("copied verbatim from one line of \"post\"");
        assertThat(prompt).contains("My Journey in X");
    }

    @Test
    void thePeriodPromptDoesNotAskForOrMentionAHeadlineAtAll() {
        AchievementEntity achievement = AchievementEntity.builder().title("x").build();

        String prompt = builder.buildPeriod(List.of(achievement), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        assertThat(prompt).doesNotContain("headline").doesNotContain("\"headline\"");
        assertThat(prompt).contains("\"paragraphs\":[").contains("\"confidence\":0.95");
    }

    @Test
    void thePeriodPromptAsksForParagraphsAsAJsonArrayNotOneStringWithBlankLines() {
        // A model that must emit N array elements is structurally more likely
        // to produce N paragraphs than one asked to insert "\n\n" into prose —
        // that instruction alone left 7/10 first attempts as one unbroken block.
        AchievementEntity achievement = AchievementEntity.builder().title("x").build();

        String prompt = builder.buildPeriod(List.of(achievement), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        assertThat(prompt).contains("\"paragraphs\":[\"\", \"\", \"\"]");
        assertThat(prompt).contains("exactly one paragraph");
        assertThat(prompt).doesNotContain("\"post\":");
    }

    @Test
    void periodPromptListsEachAchievementWithItsRepositoryAndDateRange() {
        AchievementEntity a = AchievementEntity.builder()
                .title("Refactored auth routes")
                .resumeBullet("Refactored auth routes through CurrentUserService")
                .repositoryName("CareerOS")
                .build();
        AchievementEntity b = AchievementEntity.builder()
                .title("Added commit clustering")
                .resumeBullet("Grouped commits by time and path overlap in CommitClusterer")
                .repositoryName("CareerOS")
                .build();

        String prompt = builder.buildPeriod(List.of(a, b), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        assertThat(prompt).contains("Period: 2026-07-01 to 2026-08-01");
        assertThat(prompt).contains("[CareerOS] Refactored auth routes — Refactored auth routes through CurrentUserService");
        assertThat(prompt).contains("[CareerOS] Added commit clustering — Grouped commits by time and path overlap in CommitClusterer");
        assertThat(prompt).contains("Under 250 words").contains("no minimum");
        assertThat(prompt).contains("two or three");
    }

    @Test
    void thePeriodPromptInstructsBreakingExistingContentRatherThanAddingMoreToHitParagraphCount() {
        AchievementEntity achievement = AchievementEntity.builder().title("x").build();

        String period = builder.buildPeriod(List.of(achievement), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        assertThat(period).contains("BREAKING your material");
        assertThat(period).contains("just to fill");
        assertThat(period).contains("more elements");
    }

    @Test
    void theSingleAchievementPromptDoesNotRequireMultipleParagraphsUnlikeThePeriodPrompt() {
        // A single achievement rarely has 3+ paragraphs of honest material — a
        // reliability check found the model either ignoring that requirement
        // or padding with invented detail to satisfy it. Dropped for this
        // prompt only; the period prompt below still requires it.
        AchievementEntity achievement = AchievementEntity.builder().title("x").build();

        String single = builder.build(achievement);
        String period = builder.buildPeriod(List.of(achievement), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        assertThat(single).doesNotContain("3 to 4 short paragraphs").doesNotContain("BREAKING your material");
        assertThat(single).contains("not three paragraphs");
        assertThat(period).contains("3 to 4 short paragraphs");
    }

    @Test
    void thePeriodPromptCapsAtFourParagraphsNotFive() {
        // Two real posts used all 5 of the old slots and read as a changelog
        // that listed every piece of work instead of picking two or three.
        AchievementEntity achievement = AchievementEntity.builder().title("x").build();

        String period = builder.buildPeriod(List.of(achievement), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        assertThat(period).contains("never 5");
        assertThat(period).contains("3 to 4 elements");
        assertThat(period).doesNotContain("3 to 5 elements");
    }

    @Test
    void periodPromptFallsBackToSummaryThenTitleWhenNoResumeBulletExists() {
        AchievementEntity withSummary = AchievementEntity.builder()
                .title("Hacktoberfest progress")
                .summary("Added an F1 race predictor.")
                .repositoryName("Hacktoberfest2025")
                .build();
        AchievementEntity titleOnly = AchievementEntity.builder()
                .title("Bare title only")
                .build();

        String prompt = builder.buildPeriod(
                List.of(withSummary, titleOnly), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        assertThat(prompt).contains("[Hacktoberfest2025] Hacktoberfest progress — Added an F1 race predictor.");
        assertThat(prompt).contains("[unknown repository] Bare title only — Bare title only");
    }

    @Test
    void periodPromptCapsAtTwelveAndSummarisesTheRest() {
        List<AchievementEntity> achievements = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            achievements.add(AchievementEntity.builder()
                    .title("Achievement " + i)
                    .resumeBullet("Did thing " + i)
                    .repositoryName("Repo")
                    .build());
        }

        String prompt = builder.buildPeriod(achievements, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        assertThat(prompt).contains("Did thing 11");
        assertThat(prompt).doesNotContain("Did thing 12");
        assertThat(prompt).contains("...and 3 more achievements in this period.");
    }

    @Test
    void bothPromptsProminentlyStateTheWorkWasDoneByOnePersonAlone() {
        AchievementEntity achievement = AchievementEntity.builder().title("x").build();

        String single = builder.build(achievement);
        String period = builder.buildPeriod(List.of(achievement), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        for (String prompt : List.of(single, period)) {
            assertThat(prompt).contains("SOLO AUTHOR");
            assertThat(prompt).contains("There is no \"we\" — only \"I\".");
        }
    }

    @Test
    void periodRetryAppendsTheNamedProblemsOntoTheFullPrompt() {
        AchievementEntity a = AchievementEntity.builder()
                .title("A").resumeBullet("Did A").repositoryName("Repo").build();

        // Problem descriptions distinct enough not to coincidentally appear
        // elsewhere in the prompt (the banned-vocabulary list itself contains
        // "robust, leveraged" as consecutive entries — asserting on that
        // substring would pass even if the retry suffix were never appended).
        String retry = builder.buildPeriodRetry(
                List.of(a), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1),
                List.of("only 1 paragraph break — needs at least 2", "only 90 words — needs at least 140"));

        assertThat(retry).contains("Period: 2026-07-01 to 2026-08-01");
        assertThat(retry).contains("Your previous attempt had these problems: "
                + "only 1 paragraph break — needs at least 2; only 90 words — needs at least 140");
        assertThat(retry).contains("Rewrite the whole post from scratch");
    }
}
