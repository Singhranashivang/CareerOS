package com.careeros.backend.audit;

public enum AuditAction {
    SIGN_IN,
    SIGN_OUT,
    REPOSITORY_SYNC,
    COMMIT_SYNC,
    PULL_REQUEST_SYNC,
    ACHIEVEMENT_ANALYZE,
    /** No endpoint calls achievementRepository.delete() yet — wire this in when one exists. */
    ACHIEVEMENT_DELETE,
    SCHEDULED_POST_CREATE,
    SCHEDULED_POST_UPDATE,
    SCHEDULED_POST_CANCEL,
    RATE_LIMIT_REJECTED,
    WEEKLY_DIGEST_RUN
}
