package com.careeros.backend.achievement.record;

import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AchievementRepository
        extends JpaRepository<AchievementEntity, Long> {

    List<AchievementEntity> findByUser(User user);

}
