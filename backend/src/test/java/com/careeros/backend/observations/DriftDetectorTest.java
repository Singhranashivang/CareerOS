package com.careeros.backend.observations;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DriftDetectorTest {

    private final GithubRepositoryRepository githubRepositoryRepository = mock(GithubRepositoryRepository.class);
    private final GithubCommitRepository githubCommitRepository = mock(GithubCommitRepository.class);
    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final DriftDetector detector = new DriftDetector(
            githubRepositoryRepository, githubCommitRepository, achievementRepository, new ObjectMapper());

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();

    @Test
    void firesForALanguageWithRecentCommitsNeverClaimedInAnAchievement() {
        GithubRepository repo = GithubRepository.builder().fullName("u/web").language("TypeScript").build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        when(achievementRepository.findByUser(USER)).thenReturn(List.of(
                AchievementEntity.builder().id(1L).technologiesJson("[\"Java\",\"Postgres\"]").build()));
        when(githubCommitRepository.countByRepositoryAndCommittedAtAfter(eq(repo), any())).thenReturn(6L);
        ReflectionTestUtils.setField(detector, "windowWeeks", 8);
        ReflectionTestUtils.setField(detector, "maxRepos", 3);

        List<Observation> observations = detector.detect(USER);

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).statement()).contains("TypeScript");
        assertThat(observations.get(0).statement()).contains("never appeared");
    }

    @Test
    void doesNotFireWhenTheLanguageAlreadyAppearedInAnAchievement() {
        GithubRepository repo = GithubRepository.builder().fullName("u/web").language("TypeScript").build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        when(achievementRepository.findByUser(USER)).thenReturn(List.of(
                AchievementEntity.builder().id(1L).technologiesJson("[\"TypeScript\"]").build()));
        ReflectionTestUtils.setField(detector, "windowWeeks", 8);
        ReflectionTestUtils.setField(detector, "maxRepos", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }

    @Test
    void doesNotFireWhenTheRepositoryHasNoLanguageRecorded() {
        GithubRepository repo = GithubRepository.builder().fullName("u/web").language(null).build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        when(achievementRepository.findByUser(USER)).thenReturn(List.of());
        ReflectionTestUtils.setField(detector, "windowWeeks", 8);
        ReflectionTestUtils.setField(detector, "maxRepos", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }

    @Test
    void doesNotFireWithoutRecentCommitActivity() {
        GithubRepository repo = GithubRepository.builder().fullName("u/web").language("TypeScript").build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        when(achievementRepository.findByUser(USER)).thenReturn(List.of());
        when(githubCommitRepository.countByRepositoryAndCommittedAtAfter(eq(repo), any())).thenReturn(0L);
        ReflectionTestUtils.setField(detector, "windowWeeks", 8);
        ReflectionTestUtils.setField(detector, "maxRepos", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }
}
