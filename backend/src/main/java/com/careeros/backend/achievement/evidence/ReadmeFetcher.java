package com.careeros.backend.achievement.evidence;

import com.careeros.backend.github.GithubApiService;
import com.careeros.backend.github.GithubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReadmeFetcher {

    private final GithubApiService githubApiService;

    public String fetch(GithubRepository repository) {

        String[] parts = repository.getFullName().split("/");

        return githubApiService.getFile(
                parts[0],
                parts[1],
                "README.md",
                repository.getUser().getEncryptedGithubAccessToken()
        );
    }
}