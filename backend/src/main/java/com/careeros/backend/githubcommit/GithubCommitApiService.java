package com.careeros.backend.githubcommit;

import com.careeros.backend.githubcommit.dto.GithubCommitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GithubCommitApiService {

    private final RestClient restClient;

    public List<GithubCommitResponse> getCommits(
            String accessToken,
            String fullRepositoryName
    ) {

        return restClient.get()
                .uri("https://api.github.com/repos/" + fullRepositoryName + "/commits")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GithubCommitResponse>>() {});
    }
}