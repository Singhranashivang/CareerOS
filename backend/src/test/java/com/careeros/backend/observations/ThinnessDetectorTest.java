package com.careeros.backend.observations;

import com.careeros.backend.github.AnalysisOutcome;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThinnessDetectorTest {

    private final GithubRepositoryRepository githubRepositoryRepository = mock(GithubRepositoryRepository.class);
    private final ThinnessDetector detector = new ThinnessDetector(githubRepositoryRepository);

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();

    @Test
    void firesWhenEveryRepositoryAnalyzedInTheWindowWasInsufficient() {
        GithubRepository repo = GithubRepository.builder().fullName("u/repo")
                .lastAnalyzedAt(OffsetDateTime.now().minusDays(3))
                .analysisOutcome(AnalysisOutcome.INSUFFICIENT)
                .analysisReason("evidence below the floor").build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        ReflectionTestUtils.setField(detector, "windowWeeks", 2);

        List<Observation> observations = detector.detect(USER);

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).type()).isEqualTo(ObservationType.THINNESS);
        assertThat(observations.get(0).statement()).contains("2 week");
    }

    @Test
    void doesNotFireWhenOneRepositoryProducedAnAchievement() {
        GithubRepository insufficient = GithubRepository.builder().fullName("u/a")
                .lastAnalyzedAt(OffsetDateTime.now().minusDays(3)).analysisOutcome(AnalysisOutcome.INSUFFICIENT).build();
        GithubRepository succeeded = GithubRepository.builder().fullName("u/b")
                .lastAnalyzedAt(OffsetDateTime.now().minusDays(2)).analysisOutcome(AnalysisOutcome.ACHIEVEMENT).build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(insufficient, succeeded));
        ReflectionTestUtils.setField(detector, "windowWeeks", 2);

        assertThat(detector.detect(USER)).isEmpty();
    }

    @Test
    void doesNotFireWhenNothingWasAnalyzedInTheWindow() {
        GithubRepository staleAnalysis = GithubRepository.builder().fullName("u/a")
                .lastAnalyzedAt(OffsetDateTime.now().minusWeeks(10)).analysisOutcome(AnalysisOutcome.INSUFFICIENT).build();
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(staleAnalysis));
        ReflectionTestUtils.setField(detector, "windowWeeks", 2);

        assertThat(detector.detect(USER)).isEmpty();
    }
}
