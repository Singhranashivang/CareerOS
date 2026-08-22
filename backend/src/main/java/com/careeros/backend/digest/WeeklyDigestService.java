package com.careeros.backend.digest;

import com.careeros.backend.achievement.generator.AchievementGeneratorService;
import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.github.GithubRepositoryService;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubcommit.GithubCommitService;
import com.careeros.backend.githubpullrequest.GithubPullRequestService;
import com.careeros.backend.security.RateLimitTier;
import com.careeros.backend.security.RateLimiter;
import com.careeros.backend.user.GithubTokenEncryptor;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One user's weekly run: sync repositories/commits/PRs, analyze every repo
 * with new owner-authored commits since the last run, record the outcome.
 * Reuses OnboardingService's shape (same four sync/analyze steps) since a
 * weekly digest is the same pipeline on a schedule instead of a click.
 *
 * Runs on the same bounded executor as onboarding (see AsyncConfig) — both
 * ultimately serialize on the same local Ollama instance, so sharing the
 * pool caps how much either can pile onto it, and @Async's per-task
 * isolation is what keeps one user's run from blocking another's: each
 * dispatch is an independent submitted task, not a shared loop.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyDigestService {

    private final UserRepository userRepository;
    private final GithubTokenEncryptor githubTokenEncryptor;
    private final GithubRepositoryService githubRepositoryService;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitService githubCommitService;
    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestService githubPullRequestService;
    private final AchievementGeneratorService achievementGeneratorService;
    private final RateLimiter rateLimiter;
    private final WeeklyDigestRunService weeklyDigestRunService;
    private final AuditLogService auditLogService;

    @Async("onboardingExecutor")
    public void runForUser(Long userId) {

        // Reload rather than take a User from the scheduler tick — same
        // reasoning as OnboardingService: this runs on a different thread,
        // and any instance from another thread's persistence context is gone.
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        String accessToken;
        try {
            accessToken = githubTokenEncryptor.decrypt(user.getGithubAccessToken());
        } catch (Exception e) {
            // Null token or a ciphertext that no longer decrypts (key rotated,
            // corrupted row) — the user needs to reconnect GitHub. Not a
            // failure: there was nothing to run yet.
            log.info("Skipping weekly digest for user {}: {}", userId, describe(e));
            weeklyDigestRunService.record(user, WeeklyDigestOutcome.SKIPPED,
                    "GitHub not connected: " + describe(e), 0, 0, 0, 0);
            return;
        }

        // First-ever run has no prior digest to anchor "new since last run"
        // to — seven days matches the cadence this job runs on.
        LocalDateTime since = weeklyDigestRunService.findByUser(user)
                .map(WeeklyDigestRun::getRunAt)
                .orElse(LocalDateTime.now().minusDays(7));

        int reposSynced = 0, commitsSynced = 0, reposAnalyzed = 0, achievementsCreated = 0;

        try {
            rateLimiter.consumeBlocking(user.getGithubId(), RateLimitTier.SYNC);
            reposSynced = githubRepositoryService.syncRepositories(user);

            List<GithubRepository> repositories = githubRepositoryRepository.findByUser(user);

            for (GithubRepository repository : repositories) {
                rateLimiter.consumeBlocking(user.getGithubId(), RateLimitTier.SYNC);
                commitsSynced += githubCommitService.syncCommits(
                        repository, user.getGithubId(), accessToken);
            }

            for (GithubRepository repository : repositories) {
                try {
                    rateLimiter.consumeBlocking(user.getGithubId(), RateLimitTier.SYNC);
                    githubPullRequestService.syncPullRequests(repository, accessToken);
                } catch (Exception e) {
                    // One repo's PR sync failing must not abort the run — same
                    // guard OnboardingService applies for the same reason.
                    log.warn("PR sync failed for {} during weekly digest, continuing",
                            repository.getFullName(), e);
                }
            }

            for (GithubRepository repository : repositories) {
                if (!githubCommitRepository.existsByRepositoryAndCommittedAtAfter(repository, since)) {
                    continue;
                }
                reposAnalyzed++;
                try {
                    rateLimiter.consumeBlocking(user.getGithubId(), RateLimitTier.ANALYZE);
                    var outputs = achievementGeneratorService.generate(repository, accessToken);
                    if (outputs != null) {
                        achievementsCreated += (int) outputs.stream()
                                .filter(output -> !output.isInsufficient())
                                .count();
                    }
                } catch (Exception e) {
                    // AchievementGeneratorService already records its own ERROR
                    // outcome on the repository; here we just keep the run moving.
                    log.warn("Analyze failed for {} during weekly digest, continuing",
                            repository.getFullName(), e);
                }
            }

            weeklyDigestRunService.record(user, WeeklyDigestOutcome.COMPLETED, null,
                    reposSynced, commitsSynced, reposAnalyzed, achievementsCreated);
            auditLogService.record(user, AuditAction.WEEKLY_DIGEST_RUN, "weekly", AuditOutcome.SUCCESS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Weekly digest interrupted for user {}", userId, e);
            weeklyDigestRunService.record(user, WeeklyDigestOutcome.ERROR, "Interrupted while waiting on rate limit",
                    reposSynced, commitsSynced, reposAnalyzed, achievementsCreated);

        } catch (Exception e) {
            log.error("Weekly digest failed for user {}", userId, e);
            weeklyDigestRunService.record(user, WeeklyDigestOutcome.ERROR, describe(e),
                    reposSynced, commitsSynced, reposAnalyzed, achievementsCreated);
            auditLogService.record(user, AuditAction.WEEKLY_DIGEST_RUN, "weekly", AuditOutcome.FAILURE);
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }
}
