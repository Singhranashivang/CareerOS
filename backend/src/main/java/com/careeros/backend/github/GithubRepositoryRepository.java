package com.careeros.backend.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    Optional<GithubRepository> findByGithubRepositoryId(Long githubRepositoryId);

}