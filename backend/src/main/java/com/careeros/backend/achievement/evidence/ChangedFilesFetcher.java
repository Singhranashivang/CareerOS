package com.careeros.backend.achievement.evidence;

import com.careeros.backend.github.GithubApiService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.githubcommit.GithubCommit;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChangedFilesFetcher {

    private final GithubApiService githubApiService;
    private final GithubCommitRepository githubCommitRepository;

    /** Token passed in; the repository is detached and its user is lazy. */
    public List<String> fetch(GithubRepository repository, String accessToken) {

        String[] parts = repository.getFullName().split("/");

        String owner = parts[0];
        String repo = parts[1];
        String token = accessToken;

        return githubCommitRepository.findByRepository(repository)
                .stream()
                .flatMap(commit ->
                        githubApiService.getCommitFiles(
                                owner,
                                repo,
                                commit.getGithubCommitSha(),
                                token
                        ).stream()
                )
                .distinct()
                .sorted()
                .toList();
    }
}