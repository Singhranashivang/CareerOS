package com.careeros.backend.repositoryknowledge;

import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.dto.RepositoryCountProjection;
import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface RepositoryKnowledgeRepository
        extends JpaRepository<RepositoryKnowledgeEntity, Long> {

    Optional<RepositoryKnowledgeEntity> findByRepository(
            GithubRepository repository
    );

    Optional<RepositoryKnowledgeEntity> findByRepositoryId(Long repositoryId);

    @Query("""
           select k.repository.id
           from RepositoryKnowledgeEntity k
           where k.repository.user = :user
           """)
    Set<Long> findAnalyzedRepositoryIds(@Param("user") User user);
}