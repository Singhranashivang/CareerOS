package com.careeros.backend.github;

import com.careeros.backend.github.dto.GithubRepositoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GithubApiService {

    private final RestClient restClient;

    public List<GithubRepositoryResponse> getRepositories(String accessToken) {

        return restClient.get()
                .uri("https://api.github.com/user/repos")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GithubRepositoryResponse>>() {});
    }
}