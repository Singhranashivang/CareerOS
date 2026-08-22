package com.careeros.backend.achievement.generator;

import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.github.GithubRepositoryService;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.GithubTokenEncryptor;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class AchievementGeneratorController {

    private final AchievementGeneratorService achievementGeneratorService;
    private final CurrentUserService currentUserService;
    private final GithubRepositoryService githubRepositoryService;
    private final GithubTokenEncryptor githubTokenEncryptor;
    private final AuditLogService auditLogService;

    @PostMapping("/{repositoryId}/analyze")
    public List<AchievementOutput> analyze(@PathVariable Long repositoryId) {
        User user = currentUserService.require();
        var repository = githubRepositoryService.requireOwned(user, repositoryId);
        try {
            List<AchievementOutput> output = achievementGeneratorService.generate(
                    repository, githubTokenEncryptor.decrypt(user.getGithubAccessToken()));
            auditLogService.record(user, AuditAction.ACHIEVEMENT_ANALYZE,
                    repositoryId.toString(), AuditOutcome.SUCCESS);
            return output;
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.ACHIEVEMENT_ANALYZE,
                    repositoryId.toString(), AuditOutcome.FAILURE);
            throw e;
        }
    }
}
