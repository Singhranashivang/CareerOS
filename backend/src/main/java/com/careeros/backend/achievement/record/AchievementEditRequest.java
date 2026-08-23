package com.careeros.backend.achievement.record;

/** PATCH /api/achievements/{id} body — full replace of the six editable fields, not partial. */
public record AchievementEditRequest(
        String title,
        String resumeBullet,
        String starSituation,
        String starTask,
        String starAction,
        String starResult
) {
}
