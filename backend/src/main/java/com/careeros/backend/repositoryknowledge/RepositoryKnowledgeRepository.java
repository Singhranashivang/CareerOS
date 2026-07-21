package com.careeros.backend.repositoryknowledge;

import com.careeros.backend.github.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoryKnowledgeRepository
        extends JpaRepository<RepositoryKnowledgeEntity, Long> {

    Optional<RepositoryKnowledgeEntity> findByRepository(
            GithubRepository repository
    );

    Optional<RepositoryKnowledgeEntity> findByRepositoryId(Long repositoryId);

}