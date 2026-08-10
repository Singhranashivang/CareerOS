package com.careeros.backend.onboarding;

import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingRunRepository extends JpaRepository<OnboardingRun, Long> {

    Optional<OnboardingRun> findByUserAndStatus(User user, OnboardingStatus status);

    Optional<OnboardingRun> findByIdAndUser(Long id, User user);
}
