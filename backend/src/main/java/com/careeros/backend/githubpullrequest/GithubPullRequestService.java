package com.careeros.backend.githubpullrequest;

import com.careeros.backend.github.GithubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GithubPullRequestService {

    private final GithubPullRequestRepository githubPullRequestRepository;
    private final GithubPullRequestApiService githubPullRequestApiService;


    public void syncPullRequests(
            GithubRepository repository,
            String accessToken
    ) {

        var pullRequests = githubPullRequestApiService.getPullRequests(
                accessToken,
                repository.getFullName()
        );


        System.out.println("--------------------------------");
        System.out.println(repository.getFullName());
        System.out.println("Pull Requests: " + pullRequests.size());


        pullRequests.forEach(pr -> {


            if (githubPullRequestRepository
                    .findByGithubPullRequestId(pr.getId())
                    .isPresent()) {

                return;
            }


            GithubPullRequest entity = GithubPullRequest.builder()
                    .repository(repository)
                    .githubPullRequestId(pr.getId())
                    .title(pr.getTitle())
                    .body(pr.getBody())
                    .state(pr.getState())
                    .htmlUrl(pr.getHtmlUrl())
                    .authorLogin(pr.getUser().getLogin())
                    .merged(pr.isMerged())

                    .createdAtGithub(
                            LocalDateTime.parse(
                                    pr.getCreatedAt()
                                            .replace("Z", "")
                            )
                    )

                    .updatedAtGithub(
                            LocalDateTime.parse(
                                    pr.getUpdatedAt()
                                            .replace("Z", "")
                            )
                    )

                    .mergedAtGithub(
                            pr.getMergedAt() == null
                                    ? null
                                    : LocalDateTime.parse(
                                    pr.getMergedAt()
                                            .replace("Z", "")
                            )
                    )

                    .syncedAt(LocalDateTime.now())

                    .build();


            githubPullRequestRepository.save(entity);


            System.out.println(
                    "Saved PR: " + entity.getTitle()
            );
        });
    }
}