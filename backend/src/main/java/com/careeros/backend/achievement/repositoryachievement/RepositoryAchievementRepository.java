package com.careeros.backend.achievement.repositoryachievement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepositoryAchievementRepository
        extends JpaRepository<RepositoryAchievementEntity, Long> {

    List<RepositoryAchievementEntity> findByRepositoryId(Long repositoryId);

}