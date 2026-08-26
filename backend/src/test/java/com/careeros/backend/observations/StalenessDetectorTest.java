package com.careeros.backend.observations;

import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StalenessDetectorTest {

    private final GithubRepositoryRepository githubRepositoryRepository = mock(GithubRepositoryRepository.class);
    private final GithubCommitRepository githubCommitRepository = mock(GithubCommitRepository.class);
    private final StalenessDetector detector = new StalenessDetector(githubRepositoryRepository, githubCommitRepository);

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();

    @Test
    void firesForANeverAnalyzedRepositoryWithOldCommits() {
        GithubRepository repo = GithubRepository.builder().fullName("u/repo").lastAnalyzedAt(null).build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        when(githubCommitRepository.findFirstByRepositoryOrderByCommittedAtAsc(repo))
                .thenReturn(Optional.of(commit(LocalDateTime.now().minusWeeks(5))));
        when(githubCommitRepository.countByRepository(repo)).thenReturn(9L);
        ReflectionTestUtils.setField(detector, "weeksThreshold", 3);
        ReflectionTestUtils.setField(detector, "maxRepos", 3);

        List<Observation> observations = detector.detect(USER);

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).statement()).contains("never been analyzed");
        assertThat(observations.get(0).statement()).contains("9 commits");
    }

    @Test
    void firesForARepositoryAnalyzedOnceWithNewerUnanalyzedCommits() {
        GithubRepository repo = GithubRepository.builder().fullName("u/repo")
                .lastAnalyzedAt(OffsetDateTime.now().minusWeeks(6)).build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        when(githubCommitRepository.findFirstByRepositoryAndCommittedAtAfterOrderByCommittedAtAsc(eq(repo), any()))
                .thenReturn(Optional.of(commit(LocalDateTime.now().minusWeeks(4))));
        when(githubCommitRepository.countByRepositoryAndCommittedAtAfter(eq(repo), any())).thenReturn(2L);
        ReflectionTestUtils.setField(detector, "weeksThreshold", 3);
        ReflectionTestUtils.setField(detector, "maxRepos", 3);

        List<Observation> observations = detector.detect(USER);

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).statement()).contains("hasn't been analyzed in 4 weeks");
    }

    @Test
    void doesNotFireWhenUnanalyzedCommitsAreTooRecent() {
        GithubRepository repo = GithubRepository.builder().fullName("u/repo").lastAnalyzedAt(null).build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        when(githubCommitRepository.findFirstByRepositoryOrderByCommittedAtAsc(repo))
                .thenReturn(Optional.of(commit(LocalDateTime.now().minusDays(2))));
        when(githubCommitRepository.countByRepository(repo)).thenReturn(1L);
        ReflectionTestUtils.setField(detector, "weeksThreshold", 3);
        ReflectionTestUtils.setField(detector, "maxRepos", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }

    @Test
    void doesNotFireWhenThereAreNoUnanalyzedCommitsAtAll() {
        GithubRepository repo = GithubRepository.builder().fullName("u/repo")
                .lastAnalyzedAt(OffsetDateTime.now().minusWeeks(1)).build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        when(githubCommitRepository.findFirstByRepositoryAndCommittedAtAfterOrderByCommittedAtAsc(eq(repo), any()))
                .thenReturn(Optional.empty());
        ReflectionTestUtils.setField(detector, "weeksThreshold", 3);
        ReflectionTestUtils.setField(detector, "maxRepos", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }

    private static GithubCommit commit(LocalDateTime committedAt) {
        return GithubCommit.builder().githubCommitSha("sha").message("m").committedAt(committedAt).build();
    }
}
