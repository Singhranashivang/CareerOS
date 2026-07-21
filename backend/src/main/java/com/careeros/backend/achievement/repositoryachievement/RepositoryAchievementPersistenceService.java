package com.careeros.backend.achievement.repositoryachievement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RepositoryAchievementPersistenceService {

    private final RepositoryAchievementRepository repositoryAchievementRepository;

    public RepositoryAchievementEntity save(
            RepositoryAchievementEntity entity
    ) {
        return repositoryAchievementRepository.save(entity);
    }

    public List<RepositoryAchievementEntity> findByRepositoryId(
            Long repositoryId
    ) {
        return repositoryAchievementRepository.findByRepositoryId(repositoryId);
    }

    public void delete(
            RepositoryAchievementEntity entity
    ) {
        repositoryAchievementRepository.delete(entity);
    }
}