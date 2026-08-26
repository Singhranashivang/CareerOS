package com.careeros.backend.user.dto;

import com.careeros.backend.user.UserGoal;
import jakarta.validation.constraints.NotNull;

public record UpdateGoalRequest(@NotNull UserGoal goal) {
}
