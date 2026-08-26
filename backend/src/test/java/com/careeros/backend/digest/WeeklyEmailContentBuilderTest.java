package com.careeros.backend.digest;

import com.careeros.backend.achievement.timeline.AchievementTimelineResponse;
import com.careeros.backend.observations.Observation;
import com.careeros.backend.observations.ObservationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyEmailContentBuilderTest {

    private static final Observation OBSERVATION = new Observation(
            ObservationType.SILENCE, "You have 7 unposted achievements.",
            List.of("7 non-dismissed achievements have no posted scheduled post"),
            "Schedule a post from your suggestions.");

    private static final Observation SECOND_OBSERVATION = new Observation(
            ObservationType.DRIFT, "u/web has 6 commits in TypeScript never claimed.",
            List.of("6 commits"), "Analyze u/web.");

    @Test
    void skipsWhenAllThreeAreEmpty() {
        assertThat(WeeklyEmailContentBuilder.build(List.of(), List.of(), List.of(), "http://app")).isEmpty();
    }

    @Test
    void subjectIsTheStrongestObservationNotAGenericLine() {
        var content = WeeklyEmailContentBuilder.build(
                List.of(OBSERVATION, SECOND_OBSERVATION), List.of(), List.of(), "http://app");

        assertThat(content).isPresent();
        assertThat(content.get().subject()).isEqualTo(OBSERVATION.statement());
        assertThat(content.get().subject()).doesNotContain("Your weekly summary");
    }

    @Test
    void subjectFallsBackToTheNewestAchievementWhenThereIsNoObservation() {
        var achievement = achievement("Shipped rate limiting");

        var content = WeeklyEmailContentBuilder.build(List.of(), List.of(achievement), List.of(), "http://app");

        assertThat(content).isPresent();
        assertThat(content.get().subject()).isEqualTo("New achievement: Shipped rate limiting");
    }

    @Test
    void subjectFallsBackToTheNeedsAnalysisBacklogWhenNothingElseFired() {
        var content = WeeklyEmailContentBuilder.build(List.of(), List.of(), List.of("u/a", "u/b"), "http://app");

        assertThat(content).isPresent();
        assertThat(content.get().subject()).isEqualTo("u/a and 1 other repository are ready to analyze");
    }

    @Test
    void bodyLeadsWithTheStrongestObservationThenNewAchievements() {
        var achievement = achievement("Shipped rate limiting");

        var content = WeeklyEmailContentBuilder.build(
                List.of(OBSERVATION, SECOND_OBSERVATION), List.of(achievement), List.of("u/c"), "http://app");

        assertThat(content).isPresent();
        String body = content.get().body();
        int observationIndex = body.indexOf(OBSERVATION.statement());
        int secondObservationIndex = body.indexOf(SECOND_OBSERVATION.statement());
        int achievementIndex = body.indexOf("Shipped rate limiting");
        int repoIndex = body.indexOf("u/c");

        assertThat(observationIndex).isZero();
        assertThat(secondObservationIndex).isGreaterThan(observationIndex);
        assertThat(achievementIndex).isGreaterThan(secondObservationIndex);
        assertThat(repoIndex).isGreaterThan(achievementIndex);
        assertThat(body).contains("Schedule a post from your suggestions.");
        assertThat(body).contains("http://app");
    }

    @Test
    void bodySaysNoAchievementsWereGeneratedWhenOnlyTheBacklogFired() {
        var content = WeeklyEmailContentBuilder.build(List.of(), List.of(), List.of("u/a"), "http://app");

        assertThat(content).isPresent();
        assertThat(content.get().body()).contains("No achievements were generated this week.");
    }

    private static AchievementTimelineResponse achievement(String title) {
        return AchievementTimelineResponse.builder().title(title).repository("u/repo").build();
    }
}
