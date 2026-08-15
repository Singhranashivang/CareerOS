package com.careeros.backend.achievement.generator;

import com.careeros.backend.github.GithubRepositoryService;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.GithubTokenEncryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class AchievementGeneratorController {

    private final AchievementGeneratorService achievementGeneratorService;
    private final CurrentUserService currentUserService;
    private final GithubRepositoryService githubRepositoryService;
    private final GithubTokenEncryptor githubTokenEncryptor;

    @PostMapping("/{repositoryId}/analyze")
    public AchievementOutput analyze(@PathVariable Long repositoryId) {
        var user = currentUserService.require();
        var repository = githubRepositoryService.requireOwned(user, repositoryId);
        return achievementGeneratorService.generate(
                repository, githubTokenEncryptor.decrypt(user.getGithubAccessToken()));
    }
}