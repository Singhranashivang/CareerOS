package com.careeros.backend.githubpullrequest;

import com.careeros.backend.github.GithubPaginator;
import com.careeros.backend.githubpullrequest.dto.GithubPullRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GithubPullRequestApiService {

    private final GithubPaginator githubPaginator;

    public List<GithubPullRequestResponse> getPullRequests(
            String accessToken,
            String fullRepositoryName
    ) {
        return githubPaginator.fetchAll(
                "https://api.github.com/repos/" + fullRepositoryName + "/pulls?state=all",
                accessToken,
                new ParameterizedTypeReference<List<GithubPullRequestResponse>>() {},
                3);
    }
}