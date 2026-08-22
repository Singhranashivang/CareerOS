package com.careeros.backend.github;

import com.careeros.backend.github.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
@Service
@RequiredArgsConstructor
@Slf4j
public class GithubApiService {

    private final RestClient restClient;
    private final GithubPaginator githubPaginator;

    public List<GithubRepositoryResponse> getRepositories(String accessToken) {
        return githubPaginator.fetchAll(
                "https://api.github.com/user/repos",
                accessToken,
                new ParameterizedTypeReference<List<GithubRepositoryResponse>>() {},
                10);   // up to 1000 repos
    }

    public String getFile(
            String owner,
            String repository,
            String path,
            String accessToken
    ) {

        try {

            GithubContentResponse response = restClient.get()
                    .uri(
                            "https://api.github.com/repos/{owner}/{repo}/contents/{path}",
                            owner,
                            repository,
                            path
                    )
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubContentResponse.class);

            if (response == null || response.getContent() == null) {
                return "";
            }

            return new String(
                    Base64.getDecoder()
                            .decode(response.getContent().replace("\n", "")),
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            return "";

        }

    }

    public List<String> getRepositoryTree(
            String owner,
            String repository,
            String branch,
            String accessToken
    ) {

        try {

            GithubTreeResponse response = restClient.get()
                    .uri(
                            "https://api.github.com/repos/{owner}/{repo}/git/trees/{branch}?recursive=1",
                            owner,
                            repository,
                            branch
                    )
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubTreeResponse.class);

            if (response == null || response.getTree() == null) {
                return List.of();
            }

            return response.getTree()
                    .stream()
                    .map(TreeNode::getPath)
                    .toList();

        } catch (Exception e) {

            return List.of();

        }

    }

    public List<String> getCommitFiles(
            String owner,
            String repository,
            String sha,
            String accessToken
    ) {

        try {

            GithubCommitDetailsResponse response = restClient.get()
                    .uri(
                            "https://api.github.com/repos/{owner}/{repo}/commits/{sha}",
                            owner,
                            repository,
                            sha
                    )
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubCommitDetailsResponse.class);

            if (response == null || response.getFiles() == null) {
                return List.of();
            }

            return response.getFiles()
                    .stream()
                    .map(GithubCommitFileResponse::getFilename)
                    .distinct()
                    .toList();

        } catch (Exception e) {

            return List.of();

        }

    }

    public List<GithubCommitFileResponse> getCommitFileStats(
            String owner, String repo, String sha, String accessToken) {
        try {
            GithubCommitDetailsResponse response = restClient.get()
                    .uri("https://api.github.com/repos/" + owner + "/" + repo + "/commits/" + sha)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubCommitDetailsResponse.class);

            return response == null || response.getFiles() == null
                    ? List.of()
                    : response.getFiles();
        } catch (Exception e) {
            log.warn("Could not fetch commit {} for {}/{}: {}", sha, owner, repo, e.getMessage());
            return List.of();
        }
    }

    /** Empty when the commit isn't part of any pull request — the normal case for most commits. */
    public List<GithubPullRequestSummary> getPullRequestsForCommit(
            String owner, String repo, String sha, String accessToken) {
        try {
            List<GithubPullRequestSummary> response = restClient.get()
                    .uri("https://api.github.com/repos/{owner}/{repo}/commits/{sha}/pulls",
                            owner, repo, sha)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GithubPullRequestSummary>>() {});

            return response == null ? List.of() : response;
        } catch (Exception e) {
            log.warn("Could not fetch pull requests for commit {} in {}/{}: {}",
                    sha, owner, repo, e.getMessage());
            return List.of();
        }
    }

}