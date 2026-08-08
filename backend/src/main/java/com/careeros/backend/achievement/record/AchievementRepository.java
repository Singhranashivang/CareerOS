package com.careeros.backend.achievement.record;

import com.careeros.backend.github.dto.RepositoryCountProjection;
import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AchievementRepository
        extends JpaRepository<AchievementEntity, Long> {

    List<AchievementEntity> findByUser(User user);

    Optional<AchievementEntity> findByIdAndUser(Long id, User user);

    List<AchievementEntity> findByUserOrderByGeneratedAtDesc(User user);

    List<AchievementEntity> findByRepositoryIdOrderByGeneratedAtDesc(Long repositoryId);

    long countByUser(User user);

    boolean existsByUserAndRepositoryNameAndTitle(
            User user, String repositoryName, String title);

    @Query("""
           select a.repository.id as repositoryId, count(a) as total
           from AchievementEntity a
           where a.user = :user and a.repository is not null
           group by a.repository.id
           """)
    List<RepositoryCountProjection> countPerRepository(@Param("user") User user);
}