package com.careeros.backend.onboarding.dto;

import com.careeros.backend.user.UserGoal;

/**
 * repoLimit is optional — null means the default in OnboardingController.
 * goal is asked once here; null (not answered, or already answered on an
 * earlier onboarding run) leaves the user's existing goal untouched — see
 * OnboardingController.start.
 */
public record OnboardingStartRequest(Integer repoLimit, UserGoal goal) {
}
