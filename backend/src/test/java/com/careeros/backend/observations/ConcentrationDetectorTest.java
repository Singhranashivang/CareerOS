package com.careeros.backend.observations;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConcentrationDetectorTest {

    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final GithubRepositoryRepository githubRepositoryRepository = mock(GithubRepositoryRepository.class);
    private final GithubCommitRepository githubCommitRepository = mock(GithubCommitRepository.class);
    private final ConcentrationDetector detector = new ConcentrationDetector(
            achievementRepository, githubRepositoryRepository, githubCommitRepository);

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();

    @Test
    void firesWhenTheLastKAreAllOneRepoAndAnotherRepoHasSilentActivity() {
        List<AchievementEntity> lastThree = List.of(
                achievement("api", 3), achievement("api", 2), achievement("api", 1));
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(lastThree);
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());

        GithubRepository web = GithubRepository.builder().id(20L).name("web").fullName("u/web").build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(web));
        when(githubCommitRepository.existsByRepositoryAndCommittedAtAfter(eq(web), any())).thenReturn(true);
        when(githubCommitRepository.countByRepositoryAndCommittedAtAfter(eq(web), any())).thenReturn(14L);
        ReflectionTestUtils.setField(detector, "window", 3);

        List<Observation> observations = detector.detect(USER);

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).statement()).contains("all from api");
        assertThat(observations.get(0).statement()).contains("u/web");
        assertThat(observations.get(0).statement()).contains("14 commits");
    }

    @Test
    void doesNotFireWhenTheLastKSpanMoreThanOneRepo() {
        List<AchievementEntity> lastThree = List.of(
                achievement("api", 3), achievement("web", 2), achievement("api", 1));
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(lastThree);
        ReflectionTestUtils.setField(detector, "window", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }

    @Test
    void doesNotFireWithFewerThanKAchievements() {
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(achievement("api", 1)));
        ReflectionTestUtils.setField(detector, "window", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }

    @Test
    void doesNotFireWhenNoOtherRepoHasSilentActivity() {
        List<AchievementEntity> lastThree = List.of(
                achievement("api", 3), achievement("api", 2), achievement("api", 1));
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(lastThree);
        when(achievementRepository.countPerRepository(USER)).thenReturn(List.of());
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of());
        ReflectionTestUtils.setField(detector, "window", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }

    private static AchievementEntity achievement(String repositoryName, int daysAgo) {
        return AchievementEntity.builder().id((long) daysAgo).title("t" + daysAgo)
                .repositoryName(repositoryName).generatedAt(LocalDateTime.now().minusDays(daysAgo)).build();
    }
}
