package com.careeros.backend.github;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GithubController {

    private final UserRepository userRepository;
    private final GithubRepositoryService githubRepositoryService;

    @GetMapping("/github/repos")
    public String getRepositories() {

        System.out.println("GithubController HIT");

        User user = userRepository.findByGithubId(92661186L)
                .orElseThrow(() -> new RuntimeException("User not found"));

        githubRepositoryService.syncRepositories(user);

        return "Repositories Synced";
    }
}