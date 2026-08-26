package com.careeros.backend.account;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogEntity;
import com.careeros.backend.audit.AuditLogRepository;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.github.GithubRepositoryService;
import com.careeros.backend.github.GithubTokenRevoker;
import com.careeros.backend.github.dto.RepositoryResponse;
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.schedule.PostPlatform;
import com.careeros.backend.schedule.PostStatus;
import com.careeros.backend.schedule.ScheduledPost;
import com.careeros.backend.schedule.ScheduledPostRepository;
import com.careeros.backend.user.GithubTokenEncryptor;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final GithubRepositoryRepository githubRepositoryRepository = mock(GithubRepositoryRepository.class);
    private final GithubCommitRepository githubCommitRepository = mock(GithubCommitRepository.class);
    private final ScheduledPostRepository scheduledPostRepository = mock(ScheduledPostRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final GithubRepositoryService githubRepositoryService = mock(GithubRepositoryService.class);
    private final GithubTokenEncryptor githubTokenEncryptor = mock(GithubTokenEncryptor.class);
    private final GithubTokenRevoker githubTokenRevoker = mock(GithubTokenRevoker.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final AccountService service = new AccountService(
            userRepository, achievementRepository, githubRepositoryRepository, githubCommitRepository,
            scheduledPostRepository, auditLogRepository, githubRepositoryService, githubTokenEncryptor,
            githubTokenRevoker, auditLogService);

    private static final User USER = User.builder().id(1L).githubId(1L).username("alice").build();

    @Test
    void exportCollectsAllFiveCategoriesMappedToDtos() {
        AchievementEntity achievement = AchievementEntity.builder().id(1L).title("A").build();
        GithubRepository repo = GithubRepository.builder().id(1L).name("repo").build();
        GithubCommit commit = GithubCommit.builder().id(1L).repository(repo).githubCommitSha("sha").build();
        ScheduledPost post = ScheduledPost.builder().id(1L).platform(PostPlatform.LINKEDIN)
                .status(PostStatus.DRAFT).body("body").build();
        AuditLogEntity logEntry = AuditLogEntity.builder().id(1L)
                .action(AuditAction.SIGN_IN).outcome(AuditOutcome.SUCCESS)
                .occurredAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        RepositoryResponse repoResponse = RepositoryResponse.from(repo, 0, 0);

        when(achievementRepository.findByUser(USER)).thenReturn(List.of(achievement));
        when(githubRepositoryRepository.findByUser(USER)).thenReturn(List.of(repo));
        when(githubRepositoryService.listForUser(USER)).thenReturn(List.of(repoResponse));
        when(githubCommitRepository.findByRepositoryIn(List.of(repo))).thenReturn(List.of(commit));
        when(scheduledPostRepository.findByUserOrderByScheduledForDesc(USER)).thenReturn(List.of(post));
        when(auditLogRepository.findByUserOrderByOccurredAtDesc(USER)).thenReturn(List.of(logEntry));

        AccountExportResponse result = service.export(USER);

        assertThat(result.achievements()).hasSize(1);
        assertThat(result.achievements().get(0).getTitle()).isEqualTo("A");
        assertThat(result.repositories()).containsExactly(repoResponse);
        assertThat(result.commits()).hasSize(1);
        assertThat(result.commits().get(0).githubCommitSha()).isEqualTo("sha");
        assertThat(result.commits().get(0).repositoryId()).isEqualTo(1L);
        assertThat(result.scheduledPosts()).hasSize(1);
        assertThat(result.scheduledPosts().get(0).body()).isEqualTo("body");
        assertThat(result.auditLog()).hasSize(1);
        assertThat(result.auditLog().get(0).action()).isEqualTo(AuditAction.SIGN_IN);
        assertThat(result.exportedAt()).isNotNull();
    }

    @Test
    void deleteAccountDeletesWhenTheUsernameMatches() {
        service.deleteAccount(USER, "alice");

        verify(userRepository).delete(USER);
        verify(auditLogService).record(USER, AuditAction.ACCOUNT_DELETE, null, AuditOutcome.SUCCESS);
    }

    @Test
    void deleteAccountRejectsAMismatchedUsernameWithoutDeleting() {
        assertThatThrownBy(() -> service.deleteAccount(USER, "bob"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match");

        verify(userRepository, never()).delete(any());
        verify(auditLogService).record(USER, AuditAction.ACCOUNT_DELETE, null, AuditOutcome.FAILURE);
    }

    @Test
    void deleteAccountRejectsANullConfirmationWithoutDeleting() {
        assertThatThrownBy(() -> service.deleteAccount(USER, null))
                .isInstanceOf(ResponseStatusException.class);

        verify(userRepository, never()).delete(any());
    }

    @Test
    void disconnectGithubRevokesAndClearsTheStoredToken() {
        User user = User.builder().id(1L).githubId(1L).username("alice")
                .githubAccessToken("encrypted").build();
        when(githubTokenEncryptor.decrypt("encrypted")).thenReturn("plaintext-token");

        service.disconnectGithub(user);

        verify(githubTokenRevoker).revoke("plaintext-token");
        assertThat(user.getGithubAccessToken()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void disconnectGithubIsANoOpWhenAlreadyDisconnected() {
        User user = User.builder().id(1L).githubId(1L).username("alice").githubAccessToken(null).build();

        service.disconnectGithub(user);

        verifyNoInteractions(githubTokenRevoker);
        verify(userRepository, never()).save(any());
    }
}
