package com.careeros.backend.achievement.repositoryachievement;

import com.careeros.backend.github.GithubRepositoryService;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/repository-achievements")
@RequiredArgsConstructor
public class RepositoryAchievementController {

    private final RepositoryAchievementPersistenceService service;
    private final CurrentUserService currentUserService;
    private final GithubRepositoryService githubRepositoryService;

    @GetMapping("/{repositoryId}")
    public List<RepositoryAchievementEntity> getAchievements(
            @PathVariable Long repositoryId
    ) {
        User user = currentUserService.require();
        githubRepositoryService.requireOwned(user, repositoryId);

        return service.findByRepositoryId(repositoryId);
    }
}