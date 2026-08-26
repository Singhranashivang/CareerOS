package com.careeros.backend.suggestions;

import com.careeros.backend.achievement.record.AchievementEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionScorerTest {

    private final SuggestionScorer scorer = new SuggestionScorer();

    {
        // @Value fields — only set by Spring's container normally.
        ReflectionTestUtils.setField(scorer, "confidenceWeight", 0.5);
        ReflectionTestUtils.setField(scorer, "recencyWeight", 0.3);
        ReflectionTestUtils.setField(scorer, "repoMomentumWeight", 0.2);
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
}
