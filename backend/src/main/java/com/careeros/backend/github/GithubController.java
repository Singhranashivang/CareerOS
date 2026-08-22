package com.careeros.backend.github;

import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.github.dto.RepositoryResponse;
import com.careeros.backend.githubcommit.GithubCommitService;
import com.careeros.backend.githubpullrequest.GithubPullRequestService;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.GithubTokenEncryptor;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
@Slf4j
public class GithubController {

    private final CurrentUserService currentUserService;
    private final GithubRepositoryService githubRepositoryService;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitService githubCommitService;
    private final GithubPullRequestService githubPullRequestService;
    private final GithubTokenEncryptor githubTokenEncryptor;
    private final AuditLogService auditLogService;

    /** Reads from Postgres only. Never calls GitHub. */
    @GetMapping
    public List<RepositoryResponse> list() {
        return githubRepositoryService.listForUser(currentUserService.require());
    }

    /** Whole-account operation, not scoped to one repo — no single audit target. */
    @PostMapping("/sync")
    public String syncRepositories() {
        User user = currentUserService.require();
        try {
            int count = githubRepositoryService.syncRepositories(user);
            auditLogService.record(user, AuditAction.REPOSITORY_SYNC, null, AuditOutcome.SUCCESS);
            return "Synced " + count + " repositories";
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.REPOSITORY_SYNC, null, AuditOutcome.FAILURE);
            throw e;
        }
    }

    @PostMapping("/sync/commits")
    public String syncCommits() {
        User user = currentUserService.require();

        var repositories = githubRepositoryRepository.findByUser(user);

        try {
            // The owner id comes from the attached User here; the repository
            // entities are detached, so their lazy user cannot be dereferenced.
            int saved = 0;
            for (GithubRepository repository : repositories) {
                log.info("Syncing commits for {}", repository.getFullName());
                saved += githubCommitService.syncCommits(
                        repository,
                        user.getGithubId(),
                        githubTokenEncryptor.decrypt(user.getGithubAccessToken()));
            }

            auditLogService.record(user, AuditAction.COMMIT_SYNC, null, AuditOutcome.SUCCESS);

            // Reports commits, not repositories. The old message printed "30" while
            // saving nothing at all.
            return "Synced " + saved + " commits across "
                    + repositories.size() + " repositories";
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.COMMIT_SYNC, null, AuditOutcome.FAILURE);
            throw e;
        }
    }

    @PostMapping("/sync/pull-requests")
    public String syncPullRequests() {
        User user = currentUserService.require();

        var repositories = githubRepositoryRepository.findByUser(user);
        try {
            for (GithubRepository repository : repositories) {
                log.info("Syncing pull requests for {}", repository.getFullName());
                githubPullRequestService.syncPullRequests(
                        repository, githubTokenEncryptor.decrypt(user.getGithubAccessToken()));
            }
            auditLogService.record(user, AuditAction.PULL_REQUEST_SYNC, null, AuditOutcome.SUCCESS);
            return "Synced pull requests for " + repositories.size() + " repositories";
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.PULL_REQUEST_SYNC, null, AuditOutcome.FAILURE);
            throw e;
        }
    }
}
