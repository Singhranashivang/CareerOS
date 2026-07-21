package com.careeros.backend.achievement.weeklyrecord;

import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeeklyAchievementRepository
        extends JpaRepository<WeeklyAchievementEntity, Long> {

    Optional<WeeklyAchievementEntity> findTopByUserOrderByGeneratedAtDesc(User user);

}