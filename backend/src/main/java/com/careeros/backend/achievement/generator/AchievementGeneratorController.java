package com.careeros.backend.achievement.generator;

import com.careeros.backend.github.GithubRepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AchievementGeneratorController {

    private final GithubRepositoryRepository repositoryRepository;
    private final AchievementGeneratorService achievementGeneratorService;

    @GetMapping("/achievement/generate")
    public AchievementOutput generate() {

        var repository = repositoryRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow();

        return achievementGeneratorService.generate(repository);

    }

}