package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/achievement")
@RequiredArgsConstructor
public class LinkedInController {

    private final LinkedInPostService linkedInPostService;
    private final UserRepository userRepository;

    @GetMapping("/linkedin")
    public LinkedInPost generateLinkedInPost() {

        User user = userRepository.findByGithubId(92661186L)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return linkedInPostService.generate(user);
    }

}