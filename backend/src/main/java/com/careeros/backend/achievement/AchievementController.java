package com.careeros.backend.achievement;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    private final UserRepository userRepository;

    @GetMapping("/achievement/test")
    public String test() {

        return "Achievement Module Working";
    }

    @GetMapping("/achievement/generate")
    public String generate() {

        User user = userRepository.findByGithubId(92661186L)
                .orElseThrow();

        achievementService.generateAchievements(user);

        return "Achievements Generated";
    }

}