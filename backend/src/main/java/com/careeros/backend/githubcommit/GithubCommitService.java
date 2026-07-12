package com.careeros.backend.githubcommit;

import com.careeros.backend.github.GithubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GithubCommitService {

    private final GithubCommitRepository githubCommitRepository;
    private final GithubCommitApiService githubCommitApiService;

    public void syncCommits(
            GithubRepository repository,
            String accessToken
    ) {

        var commits = githubCommitApiService.getCommits(
                accessToken,
                repository.getFullName()
        );

        System.out.println("--------------------------------");
        System.out.println(repository.getFullName());

        commits.forEach(commit -> {

            if (githubCommitRepository
                    .findByGithubCommitSha(commit.getSha())
                    .isPresent()) {

                return;
            }

            GithubCommit entity = GithubCommit.builder()
                    .repository(repository)
                    .githubCommitSha(commit.getSha())
                    .message(commit.getCommit().getMessage())
                    .authorName(commit.getCommit().getAuthor().getName())
                    .authorEmail(commit.getCommit().getAuthor().getEmail())
                    .committedAt(
                            LocalDateTime.parse(
                                    commit.getCommit().getAuthor().getDate()
                                            .replace("Z", "")
                            )
                    )
                    .htmlUrl(commit.getHtmlUrl())
                    .syncedAt(LocalDateTime.now())
                    .build();

            githubCommitRepository.save(entity);

            System.out.println("Saved: " + entity.getMessage());
        });
    }
}