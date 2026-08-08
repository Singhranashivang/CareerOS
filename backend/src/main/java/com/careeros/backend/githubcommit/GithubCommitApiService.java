package com.careeros.backend.githubcommit;

import com.careeros.backend.github.GithubPaginator;
import com.careeros.backend.githubcommit.dto.GithubCommitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GithubCommitApiService {

    private final GithubPaginator githubPaginator;

    public List<GithubCommitResponse> getCommits(
            String accessToken,
            String fullRepositoryName
    ) {
        return githubPaginator.fetchAll(
                "https://api.github.com/repos/" + fullRepositoryName + "/commits",
                accessToken,
                new ParameterizedTypeReference<List<GithubCommitResponse>>() {},
                3);   // 300 most recent commits per repo
    }
}