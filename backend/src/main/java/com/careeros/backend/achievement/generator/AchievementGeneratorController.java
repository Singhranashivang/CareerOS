package com.careeros.backend.achievement.generator;

import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/achievement")
@RequiredArgsConstructor
public class AchievementGeneratorController {

    private final GithubRepositoryRepository repositoryRepository;
    private final AchievementGeneratorService achievementGeneratorService;
    private final CurrentUserService currentUserService;

    @GetMapping("/generate")
    public AchievementOutput generate() {

        User user = currentUserService.require();

        var repository = repositoryRepository.findByUser(user)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No repositories synced yet"));

        return achievementGeneratorService.generate(repository);
    }
}