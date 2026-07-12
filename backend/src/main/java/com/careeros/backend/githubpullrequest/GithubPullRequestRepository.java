package com.careeros.backend.githubpullrequest;

import com.careeros.backend.github.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GithubPullRequestRepository
        extends JpaRepository<GithubPullRequest, Long> {


    Optional<GithubPullRequest> findByGithubPullRequestId(Long githubPullRequestId);

    List<GithubPullRequest> findByRepository(GithubRepository repository);
}