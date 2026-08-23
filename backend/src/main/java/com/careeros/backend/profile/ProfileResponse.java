package com.careeros.backend.profile;

import java.util.List;

public record ProfileResponse(
        List<ProfileAchievementResponse> achievements,
        ProfileTotalsResponse totals
) {
}
