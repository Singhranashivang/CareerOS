package com.careeros.backend.onboarding;

import com.careeros.backend.achievement.generator.AchievementGeneratorService;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingServiceTest {

    private final OnboardingRunService onboardingRunService = mock(OnboardingRunService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GithubRepositoryService githubRepositoryService = mock(GithubRepositoryService.class);
    private final GithubRepositoryRepository githubRepositoryRepository = mock(GithubRepositoryRepository.class);
    private final GithubCommitService githubCommitService = mock(GithubCommitService.class);
    private final GithubCommitRepository githubCommitRepository = mock(GithubCommitRepository.class);
    private final GithubPullRequestService githubPullRequestService = mock(GithubPullRequestService.class);
    private final RepositoryRecommendationService repositoryRecommendationService =
            mock(RepositoryRecommendationService.class);
    private final AchievementGeneratorService achievementGeneratorService = mock(AchievementGeneratorService.class);
    private final GithubTokenEncryptor githubTokenEncryptor = mock(GithubTokenEncryptor.class);

    private final OnboardingService service = new OnboardingService(
            onboardingRunService,
            userRepository,
            githubRepositoryService,
            githubRepositoryRepository,
            githubCommitService,
            githubCommitRepository,
            githubPullRequestService,
            repositoryRecommendationService,
            achievementGeneratorService,
            githubTokenEncryptor);

    /**
     * Reproduces the reported bug: a repo already synced by an earlier run
     * has nothing new for syncCommits() to insert, so it correctly returns 0
     * — but the repo still has real commits, and the progress screen must
     * reflect them rather than accumulating zero for every repo.
     */
    @Test
    void commitsSyncedReflectsEachReposTotalNotSyncCommitsDelta() {
        User user = User.builder().id(1L).githubId(99L).githubAccessToken("encrypted-token").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(githubTokenEncryptor.decrypt(anyString())).thenReturn("token");
        when(githubRepositoryService.syncRepositories(user)).thenReturn(2);

        GithubRepository repoA = GithubRepository.builder().id(10L).name("a").fullName("u/a").build();
        GithubRepository repoB = GithubRepository.builder().id(11L).name("b").fullName("u/b").build();
        when(githubRepositoryRepository.findByUser(user)).thenReturn(List.of(repoA, repoB));

        // Already fully synced by an earlier run: nothing new to insert.
        when(githubCommitService.syncCommits(eq(repoA), any(), any())).thenReturn(0);
        when(githubCommitService.syncCommits(eq(repoB), any(), any())).thenReturn(0);
        when(githubCommitRepository.countByRepository(repoA)).thenReturn(100L);
        when(githubCommitRepository.countByRepository(repoB)).thenReturn(71L);

        when(repositoryRecommendationService.recommend(user)).thenReturn(List.of());

        service.run(5L, 1L, 5);

        verify(onboardingRunService).commitsSynced(5L, 100L);
        verify(onboardingRunService).commitsSynced(5L, 71L);
        verify(onboardingRunService).complete(5L);
    }
}
