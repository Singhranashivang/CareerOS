package com.careeros.backend.github;

import com.careeros.backend.github.dto.RepositoryResponse;
import com.careeros.backend.githubcommit.GithubCommitService;
import com.careeros.backend.githubpullrequest.GithubPullRequestService;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
@Slf4j
public class GithubController {

    private final CurrentUserService currentUserService;
    private final GithubRepositoryService githubRepositoryService;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubCommitService githubCommitService;
    private final GithubPullRequestService githubPullRequestService;

    /** Reads from Postgres only. Never calls GitHub. */
    @GetMapping
    public List<RepositoryResponse> list() {
        return githubRepositoryService.listForUser(currentUserService.require());
    }

    @PostMapping("/sync")
    public String syncRepositories() {
        User user = currentUserService.require();
        int count = githubRepositoryService.syncRepositories(user);
        return "Synced " + count + " repositories";
    }

    @PostMapping("/sync/commits")
    public String syncCommits() {
        User user = currentUserService.require();

        var repositories = githubRepositoryRepository.findByUser(user);
        for (GithubRepository repository : repositories) {
            log.info("Syncing commits for {}", repository.getFullName());
            githubCommitService.syncCommits(
                    repository, user.getEncryptedGithubAccessToken());
        }
        return "Synced commits for " + repositories.size() + " repositories";
    }

    @PostMapping("/sync/pull-requests")
    public String syncPullRequests() {
        User user = currentUserService.require();

        var repositories = githubRepositoryRepository.findByUser(user);
        for (GithubRepository repository : repositories) {
            log.info("Syncing pull requests for {}", repository.getFullName());
            githubPullRequestService.syncPullRequests(
                    repository, user.getEncryptedGithubAccessToken());
        }
        return "Synced pull requests for " + repositories.size() + " repositories";
    }
}