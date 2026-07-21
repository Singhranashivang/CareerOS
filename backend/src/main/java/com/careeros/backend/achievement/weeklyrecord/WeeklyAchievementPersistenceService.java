package com.careeros.backend.achievement.weeklyrecord;

import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WeeklyAchievementPersistenceService {

    private final WeeklyAchievementRepository weeklyAchievementRepository;

    public WeeklyAchievementEntity save(
            WeeklyAchievementEntity entity
    ) {
        return weeklyAchievementRepository.save(entity);
    }

    public Optional<WeeklyAchievementEntity> findLatest(
            User user
    ) {
        return weeklyAchievementRepository
                .findTopByUserOrderByGeneratedAtDesc(user);
    }

    public long count() {
        return weeklyAchievementRepository.count();
    }

    public void delete(
            WeeklyAchievementEntity entity
    ) {
        weeklyAchievementRepository.delete(entity);
    }
}