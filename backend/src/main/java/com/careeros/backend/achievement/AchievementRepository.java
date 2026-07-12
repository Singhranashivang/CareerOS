package com.careeros.backend.achievement;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementRepository
        extends JpaRepository<Achievement, Long> {

}