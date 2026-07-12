package com.careeros.backend.githubpullrequest;

import com.careeros.backend.githubpullrequest.dto.GithubPullRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;


@Service
@RequiredArgsConstructor
public class GithubPullRequestApiService {


    private final RestClient restClient;


    public List<GithubPullRequestResponse> getPullRequests(
            String accessToken,
            String fullRepositoryName
    ){

        return restClient.get()
                .uri(
                        "https://api.github.com/repos/"
                                + fullRepositoryName
                                + "/pulls?state=all"
                )
                .header(
                        "Authorization",
                        "Bearer " + accessToken
                )
                .header(
                        "Accept",
                        "application/vnd.github+json"
                )
                .retrieve()
                .body(
                        new ParameterizedTypeReference<
                                List<GithubPullRequestResponse>>() {}
                );
    }
}