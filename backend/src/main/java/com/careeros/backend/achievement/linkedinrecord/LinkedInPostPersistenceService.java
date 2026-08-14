package com.careeros.backend.achievement.linkedinrecord;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
@Transactional
public class LinkedInPostPersistenceService {

    private final LinkedInPostRepository repository;

    public Optional<LinkedInPostEntity> findByAchievement(AchievementEntity achievement) {
        return repository.findByAchievement(achievement);
    }

    /** Most recently generated post across all of the user's achievements, for the dashboard summary. */
    public Optional<LinkedInPostEntity> findLatestByUser(User user) {
        return repository.findFirstByUserOrderByGeneratedAtDesc(user);
    }

    public LinkedInPostEntity save(LinkedInPostEntity entity) {
        return repository.save(entity);
    }
}