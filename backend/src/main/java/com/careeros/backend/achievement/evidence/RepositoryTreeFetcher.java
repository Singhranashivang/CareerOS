package com.careeros.backend.achievement.evidence;

import com.careeros.backend.github.GithubApiService;
import com.careeros.backend.github.GithubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RepositoryTreeFetcher {

    private final GithubApiService githubApiService;

    public List<String> fetch(GithubRepository repository) {

        String[] parts = repository.getFullName().split("/");

        return githubApiService.getRepositoryTree(
                parts[0],
                parts[1],
                repository.getDefaultBranch(),
                repository.getUser().getEncryptedGithubAccessToken()
        );

    }

}