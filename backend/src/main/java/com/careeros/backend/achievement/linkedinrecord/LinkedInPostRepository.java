package com.careeros.backend.achievement.linkedinrecord;

import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LinkedInPostRepository
        extends JpaRepository<LinkedInPostEntity, Long> {

    Optional<LinkedInPostEntity> findByUser(User user);

    List<LinkedInPostEntity> findByUserOrderByGeneratedAtDesc(User user);
}