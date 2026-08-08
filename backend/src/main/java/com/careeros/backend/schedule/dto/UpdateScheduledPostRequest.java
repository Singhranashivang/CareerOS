package com.careeros.backend.schedule.dto;

import java.time.OffsetDateTime;

/** Null field means "leave unchanged". */
public record UpdateScheduledPostRequest(
        String body,
        OffsetDateTime scheduledFor,
        String userTimezone
) {
}
