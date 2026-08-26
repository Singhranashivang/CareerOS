package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.CodeStats;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementConfidenceCalculatorTest {

    private final AchievementConfidenceCalculator calculator = new AchievementConfidenceCalculator();

    {
        // @Value fields — only set by Spring's container normally.
        ReflectionTestUtils.setField(calculator, "magnitudeWeight", 0.30);
        ReflectionTestUtils.setField(calculator, "largeChangeLines", 5000);
        ReflectionTestUtils.setField(calculator, "largeChangeFiles", 100);
    }

    private static CodeStats codeStats(int linesAdded, int linesDeleted, int filesTouched) {
        return CodeStats.builder().linesAdded(linesAdded).linesDeleted(linesDeleted).filesTouched(filesTouched).build();
    }

    private static Evidence.EvidenceBuilder baseEvidence() {
        return Evidence.builder()
                .features(List.of(Feature.builder().name("f").evidence(List.of("e")).build()))
                .changedFileInsights(List.of("insight"));
    }

    @Test
    void nullEvidenceScoresZero() {
        assertThat(calculator.calculate(null)).isZero();
    }

    @Test
    void aLargerClusterScoresHigherThanASmallerOneWithIdenticalOtherEvidence() {
        // The real bug this replaced: within the same repository, a 1-commit
        // and an 8-commit cluster produced the identical score, because the
        // only components that discriminate at all are magnitude-based.
        Evidence small = baseEvidence().codeStats(codeStats(100, 65, 4)).build();
        Evidence large = baseEvidence().codeStats(codeStats(40000, 21656, 376)).build();

        double smallScore = calculator.calculate(small);
        double largeScore = calculator.calculate(large);

        assertThat(largeScore).isGreaterThan(smallScore);
    }

    @Test
    void repositoryScopedComponentsAloneCannotProduceAPerfectScore() {
        // features/pullRequestTitles/dependencies/repositoryFeatures/
        // changedFileInsights/readme sum to 0.70, not 1.0 — the remaining
        // 0.30 can only come from magnitude, which needs a real CodeStats.
        Evidence noCodeStats = Evidence.builder()
                .features(List.of(Feature.builder().name("f").evidence(List.of("e")).build()))
                .pullRequestTitles(List.of("PR"))
                .dependencies(List.of("dep"))
                .repositoryFeatures(List.of("feature"))
                .changedFileInsights(List.of("insight"))
                .readme("some readme text")
                .build();

        assertThat(calculator.calculate(noCodeStats)).isEqualTo(0.70);
    }

    @Test
    void aHugeChangeCapsAtOneNotAboveIt() {
        Evidence huge = baseEvidence().codeStats(codeStats(500_000, 0, 10_000)).build();

        assertThat(calculator.calculate(huge)).isLessThanOrEqualTo(1.0);
    }

    @Test
    void aClusterWithNoCodeStatsGetsNoMagnitudeCredit() {
        Evidence noStats = baseEvidence().build();
        Evidence withStats = baseEvidence().codeStats(codeStats(1000, 200, 20)).build();

        assertThat(calculator.calculate(withStats)).isGreaterThan(calculator.calculate(noStats));
    }

    @Test
    void matchesTheRealAchievement30And33ComparisonFromTheInvestigation() {
        // id 30: 1 commit, 165 lines, 4 files, no repositoryFeatures/dependencies, readme present.
        Evidence id30 = Evidence.builder()
                .features(List.of(Feature.builder().name("f").evidence(List.of("e")).build()))
                .changedFileInsights(List.of("a", "b"))
                .readme("readme")
                .codeStats(codeStats(165, 0, 4))
                .build();
        // id 33: 1 commit, 4840 lines, 100 files, repositoryFeatures present, no readme.
        Evidence id33 = Evidence.builder()
                .features(List.of(Feature.builder().name("f").evidence(List.of("e")).build()))
                .changedFileInsights(List.of("a", "b", "c"))
                .repositoryFeatures(List.of("feature"))
                .codeStats(codeStats(4487, 353, 100))
                .build();

        // Both were a single commit; id33 touched far more code and scores higher.
        assertThat(calculator.calculate(id33)).isGreaterThan(calculator.calculate(id30));
    }
}
