package com.careeros.backend.github;

import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    Optional<GithubRepository> findByGithubRepositoryId(Long githubRepositoryId);

    List<GithubRepository> findByUser(User user);
}