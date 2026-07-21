package com.careeros.backend.achievement.repositoryachievement;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/repository-achievements")
@RequiredArgsConstructor
public class RepositoryAchievementController {

    private final RepositoryAchievementPersistenceService service;

    @GetMapping("/{repositoryId}")
    public List<RepositoryAchievementEntity> getAchievements(
            @PathVariable Long repositoryId
    ) {

        return service.findByRepositoryId(repositoryId);

    }
}