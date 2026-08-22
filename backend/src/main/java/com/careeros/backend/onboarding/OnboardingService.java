package com.careeros.backend.onboarding;

import com.careeros.backend.achievement.generator.AchievementGeneratorService;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendation;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendationService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.github.GithubRepositoryService;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubcommit.GithubCommitService;
import com.careeros.backend.githubpullrequest.GithubPullRequestService;
import com.careeros.backend.user.GithubTokenEncryptor;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Chains the four manual calls (repository sync, commit sync, PR sync,
 * analyze top N) into one background run. Each step reuses the existing,
 * already-transactional service methods as-is — this class only sequences
 * them and records progress after each one.
 *
 * Runs on a bounded executor (see AsyncConfig) rather than one thread per
 * call: Ollama is a single local instance, so concurrent analyze calls would
 * only queue there anyway while tying up more app threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private final OnboardingRunService onboardingRunService;
    private final UserRepository userRepository;
    private final GithubRepositoryService githubRepositoryService;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitService githubCommitService;
    private final GithubCommitRepository githubCommitRepository;
    private final GithubPullRequestService githubPullRequestService;
    private final RepositoryRecommendationService repositoryRecommendationService;
    private final AchievementGeneratorService achievementGeneratorService;
    private final GithubTokenEncryptor githubTokenEncryptor;

    @Async("onboardingExecutor")
    public void run(Long runId, Long userId, int repoLimit) {

        // Reload rather than take the User the controller resolved: this runs
        // on a different thread than the request, so that instance belongs to
        // a request-scoped persistence context that's already gone.
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            onboardingRunService.fail(runId, "User no longer exists");
            return;
        }
        try {
            String accessToken = githubTokenEncryptor.decrypt(user.getGithubAccessToken());

            onboardingRunService.stage(runId, OnboardingStage.CONNECTING);

            int reposFound = githubRepositoryService.syncRepositories(user);
            onboardingRunService.reposFound(runId, reposFound);

            List<GithubRepository> repositories = githubRepositoryRepository.findByUser(user);

            onboardingRunService.stage(runId, OnboardingStage.SYNCING_COMMITS);
            for (GithubRepository repository : repositories) {
                githubCommitService.syncCommits(repository, user.getGithubId(), accessToken);
                // The repo's total, not syncCommits' return value: that return is
                // rows newly inserted THIS call, which is legitimately 0 for a repo
                // already synced by an earlier run — correct for that method, wrong
                // for a progress screen, which should show what the user actually
                // has synced regardless of whether any of it is new today.
                long total = githubCommitRepository.countByRepository(repository);
                onboardingRunService.commitsSynced(runId, total);
            }

            onboardingRunService.stage(runId, OnboardingStage.SYNCING_PULL_REQUESTS);
            for (GithubRepository repository : repositories) {
                try {
                    githubPullRequestService.syncPullRequests(repository, accessToken);
                } catch (Exception e) {
                    // One repo's PR sync failing (syncPullRequests has no internal
                    // error handling) must not abort onboarding for the rest.
                    log.warn("PR sync failed for {}, continuing", repository.getFullName(), e);
                }
                onboardingRunService.prRepoSynced(runId);
            }

            List<GithubRepository> selected = repositoryRecommendationService.recommend(user)
                    .stream()
                    .limit(repoLimit)
                    .map(RepositoryRecommendation::getRepository)
                    .toList();
            onboardingRunService.startAnalyzing(runId, selected.size());

            // Sequential, not parallel: see class javadoc.
            for (GithubRepository repository : selected) {
                onboardingRunService.analyzing(runId, repository.getName());
                boolean achievementCreated = false;
                try {
                    var outputs = achievementGeneratorService.generate(repository, accessToken);
                    achievementCreated = outputs != null
                            && outputs.stream().anyMatch(output -> !output.isInsufficient());
                } catch (Exception e) {
                    // AchievementGeneratorService already records the ERROR outcome
                    // on the repository; here we just keep the run moving.
                    log.warn("Analyze failed for {}, continuing", repository.getFullName(), e);
                }
                onboardingRunService.repoAnalyzed(runId, achievementCreated);
            }

            onboardingRunService.complete(runId);

        } catch (Exception e) {
            log.error("Onboarding run {} failed", runId, e);
            onboardingRunService.fail(runId, describe(e));
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }
}
