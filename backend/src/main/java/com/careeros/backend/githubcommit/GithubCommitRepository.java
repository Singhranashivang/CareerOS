package com.careeros.backend.githubcommit;

import com.careeros.backend.github.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GithubCommitRepository
        extends JpaRepository<GithubCommit, Long> {

    Optional<GithubCommit> findByGithubCommitSha(String githubCommitSha);

    List<GithubCommit> findByRepository(GithubRepository repository);
}