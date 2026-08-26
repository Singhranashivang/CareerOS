package com.careeros.backend.suggestions;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.user.UserGoal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionScorerTest {

    private final SuggestionScorer scorer = new SuggestionScorer(new ObjectMapper());

    {
        // @Value fields — only set by Spring's container normally.
        ReflectionTestUtils.setField(scorer, "confidenceWeight", 0.5);
        ReflectionTestUtils.setField(scorer, "recencyWeight", 0.3);
        ReflectionTestUtils.setField(scorer, "repoMomentumWeight", 0.2);
        ReflectionTestUtils.setField(scorer, "techBreadthWeight", 0.3);
        ReflectionTestUtils.setField(scorer, "techBreadthCeiling", 5);
        ReflectionTestUtils.setField(scorer, "audienceBuildingRecencyBoost", 0.3);
        ReflectionTestUtils.setField(scorer, "performanceReviewConfidenceBoost", 0.3);
        ReflectionTestUtils.setField(scorer, "highConfidenceThreshold", 0.75);
        ReflectionTestUtils.setField(scorer, "recentDays", 7);
    }

    private static AchievementEntity achievement(double confidence, LocalDateTime generatedAt) {
        return AchievementEntity.builder().confidence(confidence).generatedAt(generatedAt).build();
    }

    @Test
    void higherConfidenceScoresHigherAllElseEqual() {
        var low = scorer.score(achievement(0.3, LocalDateTime.now()), false);
        var high = scorer.score(achievement(0.9, LocalDateTime.now()), false);

        assertThat(high.score()).isGreaterThan(low.score());
    }

    @Test
    void moreRecentScoresHigherAllElseEqual() {
        var old = scorer.score(achievement(0.5, LocalDateTime.now().minusDays(60)), false);
        var recent = scorer.score(achievement(0.5, LocalDateTime.now()), false);

        assertThat(recent.score()).isGreaterThan(old.score());
    }

    @Test
    void repositoryWithPostedWorkScoresHigherAllElseEqual() {
        AchievementEntity achievement = achievement(0.5, LocalDateTime.now().minusDays(10));

        var withoutMomentum = scorer.score(achievement, false);
        var withMomentum = scorer.score(achievement, true);

        assertThat(withMomentum.score()).isGreaterThan(withoutMomentum.score());
    }

    @Test
    void reasonNamesHighConfidenceWhenAboveTheThreshold() {
        var scored = scorer.score(achievement(0.92, LocalDateTime.now().minusDays(30)), false);

        assertThat(scored.reason()).containsIgnoringCase("high confidence").contains("92%");
    }

    @Test
    void reasonNamesRecencyWhenWithinTheRecentWindow() {
        var scored = scorer.score(achievement(0.4, LocalDateTime.now().minusDays(2)), false);

        assertThat(scored.reason()).containsIgnoringCase("generated 2 days ago");
    }

    @Test
    void reasonSaysGeneratedTodayForZeroDays() {
        var scored = scorer.score(achievement(0.4, LocalDateTime.now()), false);

        assertThat(scored.reason()).containsIgnoringCase("generated today");
    }

    @Test
    void reasonNamesRepositoryMomentumWhenPresent() {
        var scored = scorer.score(achievement(0.4, LocalDateTime.now().minusDays(30)), true);

        assertThat(scored.reason()).containsIgnoringCase("this repository already has posted work");
    }

    @Test
    void reasonFallsBackToAGenericExplanationWhenNoFactorQualifies() {
        var scored = scorer.score(achievement(0.4, LocalDateTime.now().minusDays(30)), false);

        assertThat(scored.reason()).contains("Ranked by confidence, recency, and repository activity");
    }

    @Test
    void aMissingGeneratedAtIsTreatedAsArbitrarilyOldNotRecent() {
        var scored = scorer.score(achievement(0.4, null), false);

        assertThat(scored.reason()).doesNotContain("generated");
    }

    @Test
    void combinesMultipleQualifyingReasonsOnOneLine() {
        var scored = scorer.score(achievement(0.9, LocalDateTime.now()), true);

        assertThat(scored.reason())
                .containsIgnoringCase("high confidence")
                .containsIgnoringCase("generated today")
                .containsIgnoringCase("this repository already has posted work");
    }

    @Test
    void nullGoalScoresExactlyLikeTheTwoArgOverload() {
        AchievementEntity achievement = achievement(0.6, LocalDateTime.now().minusDays(3));

        var withoutGoalParam = scorer.score(achievement, false);
        var explicitNullGoal = scorer.score(achievement, false, null);

        assertThat(explicitNullGoal.score()).isEqualTo(withoutGoalParam.score());
    }

    @Test
    void jobHuntingFavoursTechnologyBreadthAllElseEqual() {
        AchievementEntity narrow = achievementWithTechnologies("[\"Java\"]");
        AchievementEntity broad = achievementWithTechnologies("[\"Java\",\"React\",\"Postgres\",\"Docker\"]");

        var narrowScore = scorer.score(narrow, false, UserGoal.JOB_HUNTING);
        var broadScore = scorer.score(broad, false, UserGoal.JOB_HUNTING);

        assertThat(broadScore.score()).isGreaterThan(narrowScore.score());
        assertThat(broadScore.reason()).containsIgnoringCase("spans 4 technologies");
    }

    @Test
    void technologyBreadthDoesNothingWithoutTheJobHuntingGoal() {
        AchievementEntity narrow = achievementWithTechnologies("[\"Java\"]");
        AchievementEntity broad = achievementWithTechnologies("[\"Java\",\"React\",\"Postgres\",\"Docker\"]");

        var narrowScore = scorer.score(narrow, false, null);
        var broadScore = scorer.score(broad, false, null);

        assertThat(broadScore.score()).isEqualTo(narrowScore.score());
    }

    @Test
    void audienceBuildingWeighsRecencyMoreHeavilyThanNoGoal() {
        AchievementEntity achievement = achievement(0.5, LocalDateTime.now());

        var noGoal = scorer.score(achievement, false, null);
        var audienceBuilding = scorer.score(achievement, false, UserGoal.AUDIENCE_BUILDING);

        assertThat(audienceBuilding.score()).isGreaterThan(noGoal.score());
    }

    @Test
    void performanceReviewWeighsConfidenceMoreHeavilyThanNoGoal() {
        AchievementEntity achievement = achievement(0.8, LocalDateTime.now().minusDays(30));

        var noGoal = scorer.score(achievement, false, null);
        var performanceReview = scorer.score(achievement, false, UserGoal.PERFORMANCE_REVIEW);

        assertThat(performanceReview.score()).isGreaterThan(noGoal.score());
    }

    private static AchievementEntity achievementWithTechnologies(String technologiesJson) {
        return AchievementEntity.builder()
                .confidence(0.5)
                .generatedAt(LocalDateTime.now().minusDays(10))
                .technologiesJson(technologiesJson)
                .build();
    }
}
