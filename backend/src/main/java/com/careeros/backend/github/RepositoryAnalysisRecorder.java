package com.careeros.backend.github;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class RepositoryAnalysisRecorder {

    private final GithubRepositoryRepository githubRepositoryRepository;

    /**
     * REQUIRES_NEW so an ERROR outcome outlives the caller's rollback. Analysis
     * runs in a transaction that rolls back when it throws, which would discard
     * the very row explaining why it failed.
     *
     * Loads its own copy rather than taking the entity: the caller's instance
     * belongs to a persistence context that is about to be rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long repositoryId, AnalysisOutcome outcome, String reason) {
        githubRepositoryRepository.findById(repositoryId).ifPresent(repository -> {
            repository.setLastAnalyzedAt(OffsetDateTime.now());
            repository.setAnalysisOutcome(outcome);
            repository.setAnalysisReason(reason);
        });
    }
}
