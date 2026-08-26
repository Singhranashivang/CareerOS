package com.careeros.backend.account;

import com.careeros.backend.account.dto.AuditLogExportResponse;
import com.careeros.backend.account.dto.CommitExportResponse;
import com.careeros.backend.account.dto.ScheduledPostExportResponse;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.achievement.timeline.AchievementTimelineResponse;
import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogRepository;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.github.GithubRepositoryService;
import com.careeros.backend.github.GithubTokenRevoker;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.schedule.ScheduledPostRepository;
import com.careeros.backend.user.GithubTokenEncryptor;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final AchievementRepository achievementRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final ScheduledPostRepository scheduledPostRepository;
    private final AuditLogRepository auditLogRepository;
    private final GithubRepositoryService githubRepositoryService;
    private final GithubTokenEncryptor githubTokenEncryptor;
    private final GithubTokenRevoker githubTokenRevoker;
    private final AuditLogService auditLogService;

    /**
     * Everything mapped to a DTO inside this one read-only transaction —
     * GithubCommit.repository and AchievementEntity/GithubRepository's own
     * lazy relations only resolve safely while the session backing them is
     * still open, same reasoning as every other controller in this app.
     */
    @Transactional(readOnly = true)
    public AccountExportResponse export(User user) {

        List<GithubRepository> repositories = githubRepositoryRepository.findByUser(user);

        return new AccountExportResponse(
                LocalDateTime.now(),
                achievementRepository.findByUser(user).stream()
                        .map(AchievementTimelineResponse::from)
                        .toList(),
                githubRepositoryService.listForUser(user),
                githubCommitRepository.findByRepositoryIn(repositories).stream()
                        .map(CommitExportResponse::from)
                        .toList(),
                scheduledPostRepository.findByUserOrderByScheduledForDesc(user).stream()
                        .map(ScheduledPostExportResponse::from)
                        .toList(),
                auditLogRepository.findByUserOrderByOccurredAtDesc(user).stream()
                        .map(AuditLogExportResponse::from)
                        .toList()
        );
    }

    /**
     * confirmUsername must exactly match the caller's own GitHub username —
     * not their id, not "yes"/"DELETE" — so this can't be triggered by a
     * mis-clicked button, only by someone who actually typed their own name.
     * The delete itself is a single row: every other table cascades from
     * users at the database level (see V24 for the four that didn't used to).
     *
     * The success audit row is written BEFORE the delete, not after — every
     * other controller in this app audits after the action, but audit_log
     * cascades from users same as everything else, so a row written after
     * the user is gone can't even be inserted (its own foreign key target no
     * longer exists). This row captures the deletion event while there's
     * still a user for it to reference, then disappears along with
     * everything else a moment later — the correct outcome for a real
     * deletion, not a bug in this call.
     */
    public void deleteAccount(User user, String confirmUsername) {

        if (confirmUsername == null || !confirmUsername.equals(user.getUsername())) {
            auditLogService.record(user, AuditAction.ACCOUNT_DELETE, null, AuditOutcome.FAILURE);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Confirmation username does not match your account");
        }

        auditLogService.record(user, AuditAction.ACCOUNT_DELETE, null, AuditOutcome.SUCCESS);
        userRepository.delete(user);
    }

    /**
     * Revokes the GitHub grant (best-effort, see GithubTokenRevoker) and
     * clears the stored token — achievements, repositories, everything else
     * already generated stays untouched. Idempotent: a user with no token on
     * file is already disconnected, so this is a no-op rather than an error.
     */
    @Transactional
    public void disconnectGithub(User user) {

        if (user.getGithubAccessToken() == null) {
            return;
        }

        githubTokenRevoker.revoke(githubTokenEncryptor.decrypt(user.getGithubAccessToken()));

        user.setGithubAccessToken(null);
        userRepository.save(user);
    }
}
