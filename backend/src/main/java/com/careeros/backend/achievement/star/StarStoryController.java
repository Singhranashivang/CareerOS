package com.careeros.backend.achievement.star;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/achievement")
@RequiredArgsConstructor
public class StarStoryController {

    private final StarStoryService starStoryService;
    private final UserRepository userRepository;

    @GetMapping("/star")
    public StarStory generate() {

        User user = userRepository.findByGithubId(92661186L)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return starStoryService.generate(user);
    }
}