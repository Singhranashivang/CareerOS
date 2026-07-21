package com.careeros.backend.github;

import com.careeros.backend.githubcommit.GithubCommitService;
import com.careeros.backend.githubpullrequest.GithubPullRequestService;
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
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitService githubCommitService;
    private final GithubPullRequestService githubPullRequestService;

    @GetMapping("/github/repos")
    public String getRepositories() {

        System.out.println("GithubController HIT");

        User user = userRepository.findByGithubId(92661186L)
                .orElseThrow(() -> new RuntimeException("User not found"));

        githubRepositoryService.syncRepositories(user);

        return "Repositories Synced";
    }

    @GetMapping("/github/test-commits")
    public String testCommits() {

        User user = userRepository.findByGithubId(92661186L)
                .orElseThrow(() -> new RuntimeException("User not found"));

        var repositories = githubRepositoryRepository.findByUser(user);

        for (GithubRepository repository : repositories) {

            System.out.println("--------------------------------");
            System.out.println("Syncing " + repository.getFullName());

            githubCommitService.syncCommits(
                    repository,
                    user.getEncryptedGithubAccessToken()
            );
        }

        return "All commits synced.";
    }

    @GetMapping("/github/test-pullrequests")
    public String testPullRequests() {

        User user = userRepository.findByGithubId(92661186L)
                .orElseThrow(() -> new RuntimeException("User not found"));

        var repositories = githubRepositoryRepository.findByUser(user);

        for (GithubRepository repository : repositories) {

            githubPullRequestService.syncPullRequests(
                    repository,
                    user.getEncryptedGithubAccessToken()
            );
        }

        return "Pull Requests Synced";
    }
}