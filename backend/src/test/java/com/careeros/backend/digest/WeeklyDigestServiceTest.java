package com.careeros.backend.digest;

import com.careeros.backend.achievement.generator.AchievementGeneratorService;
import com.careeros.backend.achievement.generator.AchievementOutput;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.github.GithubRepositoryService;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubcommit.GithubCommitService;
import com.careeros.backend.githubpullrequest.GithubPullRequestService;
import com.careeros.backend.security.RateLimiter;
import com.careeros.backend.user.GithubTokenEncryptor;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WeeklyDigestServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final GithubTokenEncryptor githubTokenEncryptor = mock(GithubTokenEncryptor.class);
    private final GithubRepositoryService githubRepositoryService = mock(GithubRepositoryService.class);
    private final GithubRepositoryRepository githubRepositoryRepository = mock(GithubRepositoryRepository.class);
    private final GithubCommitService githubCommitService = mock(GithubCommitService.class);
    private final GithubCommitRepository githubCommitRepository = mock(GithubCommitRepository.class);
    private final GithubPullRequestService githubPullRequestService = mock(GithubPullRequestService.class);
    private final AchievementGeneratorService achievementGeneratorService = mock(AchievementGeneratorService.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final WeeklyDigestRunService weeklyDigestRunService = mock(WeeklyDigestRunService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final WeeklyDigestService service = new WeeklyDigestService(
            userRepository, githubTokenEncryptor, githubRepositoryService, githubRepositoryRepository,
            githubCommitService, githubCommitRepository, githubPullRequestService,
            achievementGeneratorService, rateLimiter, weeklyDigestRunService, auditLogService);

    private static final User USER = User.builder()
            .id(1L).githubId(100L).username("u").githubAccessToken("cipher").build();

    @Test
    void nullTokenIsSkippedNotErrored() {
        User user = User.builder().id(1L).githubId(100L).username("u").githubAccessToken(null).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(githubTokenEncryptor.decrypt(null))
                .thenThrow(new IllegalStateException("No GitHub token on file for this user"));

        service.runForUser(1L);

        verify(weeklyDigestRunService).record(
                eq(user), eq(WeeklyDigestOutcome.SKIPPED), any(), eq(0), eq(0), eq(0), eq(0));
        verifyNoInteractions(githubRepositoryService, achievementGeneratorService);
    }

    @Test
    void undecryptableTokenIsSkippedNotErrored() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(USER));
        when(githubTokenEncryptor.decrypt("cipher"))
                .thenThrow(new IllegalArgumentException("Given final block not properly padded"));

        service.runForUser(1L);

        verify(weeklyDigestRunService).record(
                eq(USER), eq(WeeklyDigestOutcome.SKIPPED), any(), eq(0), eq(0), eq(0), eq(0));
        verifyNoInteractions(githubRepositoryService, achievementGeneratorService);
        // Skipped is not a security-relevant failure worth auditing — only a real run outcome is.
        verifyNoInteractions(auditLogService);
    }

    @Test
    void onlyReposWithNewCommitsSinceLastRunAreAnalyzed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(USER));
        when(githubTokenEncryptor.decrypt("cipher")).thenReturn("token");
        when(weeklyDigestRunService.findByUser(USER)).thenReturn(Optional.empty());

        GithubRepository repoA = GithubRepository.builder().id(10L).name("A").fullName("u/A").build();
        GithubRepository repoB = GithubRepository.builder().id(20L).name("B").fullName("u/B").build();
        when(githubRepositoryService.syncRepositories(USER)).thenReturn(2);
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repoA, repoB));
        when(githubCommitService.syncCommits(eq(repoA), eq(100L), eq("token"))).thenReturn(3);
        when(githubCommitService.syncCommits(eq(repoB), eq(100L), eq("token"))).thenReturn(0);
        when(githubCommitRepository.existsByRepositoryAndCommittedAtAfter(eq(repoA), any())).thenReturn(true);
        when(githubCommitRepository.existsByRepositoryAndCommittedAtAfter(eq(repoB), any())).thenReturn(false);
        when(achievementGeneratorService.generate(eq(repoA), eq("token")))
                .thenReturn(List.of(AchievementOutput.builder().title("x").build()));

        service.runForUser(1L);

        verify(achievementGeneratorService).generate(repoA, "token");
        verify(achievementGeneratorService, never()).generate(eq(repoB), any());
        verify(weeklyDigestRunService).record(USER, WeeklyDigestOutcome.COMPLETED, null, 2, 3, 1, 1);
    }

    @Test
    void oneRepoFailingAnalysisDoesNotAbortTheRun() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(USER));
        when(githubTokenEncryptor.decrypt("cipher")).thenReturn("token");
        when(weeklyDigestRunService.findByUser(USER)).thenReturn(Optional.empty());

        GithubRepository repoA = GithubRepository.builder().id(10L).name("A").fullName("u/A").build();
        GithubRepository repoB = GithubRepository.builder().id(20L).name("B").fullName("u/B").build();
        when(githubRepositoryService.syncRepositories(USER)).thenReturn(2);
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repoA, repoB));
        when(githubCommitRepository.existsByRepositoryAndCommittedAtAfter(any(), any())).thenReturn(true);
        when(achievementGeneratorService.generate(eq(repoA), any())).thenThrow(new RuntimeException("boom"));
        when(achievementGeneratorService.generate(eq(repoB), any()))
                .thenReturn(List.of(AchievementOutput.builder().title("y").build()));

        service.runForUser(1L);

        verify(achievementGeneratorService).generate(repoB, "token");
        verify(weeklyDigestRunService).record(USER, WeeklyDigestOutcome.COMPLETED, null, 2, 0, 2, 1);
    }
}
