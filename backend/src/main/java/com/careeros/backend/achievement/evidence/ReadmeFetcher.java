package com.careeros.backend.achievement.evidence;

import com.careeros.backend.github.GithubApiService;
import com.careeros.backend.github.GithubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReadmeFetcher {

    private final GithubApiService githubApiService;

    /**
     * The token is passed in rather than read from repository.getUser(): the
     * repository arrives detached from the controller (open-in-view is off), so
     * touching its lazy user throws LazyInitializationException.
     */
    public String fetch(GithubRepository repository, String accessToken) {

        String[] parts = repository.getFullName().split("/");

        return githubApiService.getFile(
                parts[0],
                parts[1],
                "README.md",
                accessToken
        );
    }
}