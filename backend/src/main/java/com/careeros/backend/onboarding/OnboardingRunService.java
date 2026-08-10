package com.careeros.backend.onboarding;

import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Persistence for a run's progress. Each update is its own transaction so a
 * poller sees the latest committed step regardless of how far the orchestrator
 * (OnboardingService, a different bean — no self-invocation here) has gotten.
 */
@Service
@RequiredArgsConstructor
public class OnboardingRunService {

    private final OnboardingRunRepository onboardingRunRepository;

    /**
     * Returns the user's existing RUNNING run if there is one, otherwise
     * creates a fresh one. `created` tells the caller whether to actually
     * kick off the background job — without it, a double-click (or any
     * second request that lands while the first is still at its earliest
     * stage) would launch the pipeline twice against the same row.
     *
     * The partial unique index on (user_id) WHERE status = 'RUNNING' is the
     * real guard against two concurrent starts; the
     * DataIntegrityViolationException catch here just turns that race into
     * the same "join the existing run" outcome instead of a 500.
     */
    @Transactional
    public StartResult startOrJoin(User user) {
        var existing = onboardingRunRepository.findByUserAndStatus(user, OnboardingStatus.RUNNING);
        if (existing.isPresent()) {
            return new StartResult(existing.get(), false);
        }
        try {
            var created = onboardingRunRepository.save(
                    OnboardingRun.builder().user(user).build());
            return new StartResult(created, true);
        } catch (DataIntegrityViolationException e) {
            return new StartResult(
                    onboardingRunRepository.findByUserAndStatus(user, OnboardingStatus.RUNNING)
                            .orElseThrow(() -> e),
                    false);
        }
    }

    public record StartResult(OnboardingRun run, boolean created) {
    }

    @Transactional(readOnly = true)
    public OnboardingRun requireOwned(User user, Long id) {
        return onboardingRunRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new AccessDeniedException("Onboarding run not found"));
    }

    @Transactional
    public void stage(Long runId, OnboardingStage stage) {
        get(runId).setStage(stage);
    }

    @Transactional
    public void reposFound(Long runId, int count) {
        var run = get(runId);
        run.setReposFound(count);
        run.setStage(OnboardingStage.REPOS_FOUND);
    }

    @Transactional
    public void commitsSynced(Long runId, int savedThisRepo) {
        var run = get(runId);
        run.setCommitsSynced(run.getCommitsSynced() + savedThisRepo);
    }

    @Transactional
    public void prRepoSynced(Long runId) {
        var run = get(runId);
        run.setPrReposSynced(run.getPrReposSynced() + 1);
    }

    @Transactional
    public void startAnalyzing(Long runId, int reposToAnalyze) {
        var run = get(runId);
        run.setStage(OnboardingStage.ANALYZING);
        run.setReposToAnalyze(reposToAnalyze);
    }

    @Transactional
    public void analyzing(Long runId, String repositoryName) {
        get(runId).setCurrentRepositoryName(repositoryName);
    }

    @Transactional
    public void repoAnalyzed(Long runId, boolean achievementCreated) {
        var run = get(runId);
        run.setReposAnalyzed(run.getReposAnalyzed() + 1);
        if (achievementCreated) {
            run.setAchievementsCreated(run.getAchievementsCreated() + 1);
        }
    }

    @Transactional
    public void complete(Long runId) {
        var run = get(runId);
        run.setStatus(OnboardingStatus.DONE);
        run.setStage(OnboardingStage.DONE);
        run.setCurrentRepositoryName(null);
        run.setCompletedAt(OffsetDateTime.now());
    }

    @Transactional
    public void fail(Long runId, String message) {
        var run = get(runId);
        run.setStatus(OnboardingStatus.FAILED);
        run.setErrorMessage(message);
        run.setCompletedAt(OffsetDateTime.now());
    }

    private OnboardingRun get(Long runId) {
        return onboardingRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Onboarding run vanished: " + runId));
    }
}
