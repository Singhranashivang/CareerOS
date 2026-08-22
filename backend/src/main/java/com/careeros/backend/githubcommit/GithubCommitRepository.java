package com.careeros.backend.githubcommit;

import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.dto.RepositoryCountProjection;
import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GithubCommitRepository
        extends JpaRepository<GithubCommit, Long> {

    Optional<GithubCommit> findByGithubCommitSha(String githubCommitSha);

    List<GithubCommit> findByRepository(GithubRepository repository);

    long countByRepository(GithubRepository repository);

    /** Every stored commit is already owner-authored — see GithubCommitService.authoredBy. */
    boolean existsByRepositoryAndCommittedAtAfter(GithubRepository repository, LocalDateTime since);

    @Query("""
           select c.repository.id as repositoryId, count(c) as total
           from GithubCommit c
           where c.repository.user = :user
           group by c.repository.id
           """)
    List<RepositoryCountProjection> countPerRepository(@Param("user") User user);
}