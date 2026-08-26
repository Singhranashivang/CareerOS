package com.careeros.backend.observations;

import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A repository with commits sitting unanalyzed for weeksThreshold or more —
 * "unanalyzed" meaning after lastAnalyzedAt, or ever if never analyzed. Most
 * stale first, capped at maxRepos so one dormant account doesn't flood the
 * response.
 */
@Component
@RequiredArgsConstructor
public class StalenessDetector implements ObservationDetector {

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitRepository githubCommitRepository;

    @Value("${app.observations.staleness.weeks:3}")
    private int weeksThreshold;

    @Value("${app.observations.staleness.max-repos:3}")
    private int maxRepos;

    private record Candidate(GithubRepository repository, long unanalyzedCount, LocalDateTime earliest, long weeks) {
    }

    @Override
    public List<Observation> detect(User user) {
        return githubRepositoryRepository.findByUser(user).stream()
                .map(this::candidateFor)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(c -> c.weeks() >= weeksThreshold)
                .sorted(Comparator.comparingLong(Candidate::weeks).reversed())
                .limit(maxRepos)
                .map(this::toObservation)
                .toList();
    }

    private Optional<Candidate> candidateFor(GithubRepository repository) {
        LocalDateTime since = repository.getLastAnalyzedAt() == null
                ? null : repository.getLastAnalyzedAt().toLocalDateTime();

        Optional<GithubCommit> earliestUnanalyzed = since == null
                ? githubCommitRepository.findFirstByRepositoryOrderByCommittedAtAsc(repository)
                : githubCommitRepository.findFirstByRepositoryAndCommittedAtAfterOrderByCommittedAtAsc(repository, since);

        if (earliestUnanalyzed.isEmpty()) {
            return Optional.empty();
        }

        long count = since == null
                ? githubCommitRepository.countByRepository(repository)
                : githubCommitRepository.countByRepositoryAndCommittedAtAfter(repository, since);

        LocalDateTime earliest = earliestUnanalyzed.get().getCommittedAt();
        long weeks = ChronoUnit.WEEKS.between(earliest, LocalDateTime.now());

        return Optional.of(new Candidate(repository, count, earliest, weeks));
    }

    private Observation toObservation(Candidate c) {
        String statement = c.repository().getLastAnalyzedAt() == null
                ? "%s has %d commit%s going back %d weeks that have never been analyzed.".formatted(
                        c.repository().getFullName(), c.unanalyzedCount(), c.unanalyzedCount() == 1 ? "" : "s", c.weeks())
                : "%s hasn't been analyzed in %d weeks despite %d new commit%s.".formatted(
                        c.repository().getFullName(), c.weeks(), c.unanalyzedCount(), c.unanalyzedCount() == 1 ? "" : "s");

        List<String> evidence = List.of(
                "%d unanalyzed commit(s), earliest %s".formatted(c.unanalyzedCount(), c.earliest().toLocalDate()),
                c.repository().getLastAnalyzedAt() == null
                        ? "Never analyzed"
                        : "Last analyzed " + c.repository().getLastAnalyzedAt().toLocalDate());

        return new Observation(ObservationType.STALENESS, statement, evidence,
                "Run analysis on " + c.repository().getFullName() + ".");
    }
}
