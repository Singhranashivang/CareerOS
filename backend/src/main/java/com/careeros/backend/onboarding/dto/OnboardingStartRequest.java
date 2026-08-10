package com.careeros.backend.onboarding.dto;

/** repoLimit is optional — null means the default in OnboardingController. */
public record OnboardingStartRequest(Integer repoLimit) {
}
