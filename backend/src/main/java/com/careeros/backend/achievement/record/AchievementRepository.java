package com.careeros.backend.achievement.record;

import com.careeros.backend.github.dto.RepositoryCountProjection;
import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AchievementRepository
        extends JpaRepository<AchievementEntity, Long> {

    List<AchievementEntity> findByUser(User user);

    Optional<AchievementEntity> findByIdAndUser(Long id, User user);

    /** Excludes dismissed achievements — the default list, per GET /api/achievements. */
    List<AchievementEntity> findByUserAndDismissedFalseOrderByGeneratedAtDesc(User user);

    List<AchievementEntity> findByRepositoryIdOrderByGeneratedAtDesc(Long repositoryId);

    long countByUser(User user);

    boolean existsByUserAndRepositoryNameAndTitle(
            User user, String repositoryName, String title);

    /** Dedup key for the per-cluster generator — the citedCommitShasJson column is always sorted before saving. */
    boolean existsByUserAndRepositoryNameAndCitedCommitShasJson(
            User user, String repositoryName, String citedCommitShasJson);

    /** The generator checks this before doing any evidence/LLM work, so a dismissed cluster is never regenerated. */
    boolean existsByUserAndRepositoryNameAndCitedCommitShasJsonAndDismissedTrue(
            User user, String repositoryName, String citedCommitShasJson);

    /** Backs GET /api/digest/latest — "this week's" achievements, since the latest digest run. Excludes dismissed. */
    List<AchievementEntity> findByUserAndDismissedFalseAndGeneratedAtAfterOrderByGeneratedAtDesc(
            User user, LocalDateTime after);

    /** Backs POST /api/achievements/linkedin/period — every non-dismissed achievement in [from, to]. */
    List<AchievementEntity> findByUserAndDismissedFalseAndGeneratedAtBetweenOrderByGeneratedAtDesc(
            User user, LocalDateTime from, LocalDateTime to);

    @Query("""
           select a.repository.id as repositoryId, count(a) as total
           from AchievementEntity a
           where a.user = :user and a.repository is not null
           group by a.repository.id
           """)
    List<RepositoryCountProjection> countPerRepository(@Param("user") User user);
}