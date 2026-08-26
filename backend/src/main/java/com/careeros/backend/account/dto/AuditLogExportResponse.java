package com.careeros.backend.account.dto;

import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogEntity;
import com.careeros.backend.audit.AuditOutcome;

import java.time.LocalDateTime;

/**
 * AuditLogEntity.user is already @JsonIgnore, so the entity itself wouldn't
 * throw if serialized directly — DTO anyway, to keep every section of the
 * export response built the same way rather than special-casing the one
 * entity that happens to already be safe.
 */
public record AuditLogExportResponse(
        Long id,
        LocalDateTime occurredAt,
        AuditAction action,
        String target,
        AuditOutcome outcome
) {
    public static AuditLogExportResponse from(AuditLogEntity e) {
        return new AuditLogExportResponse(e.getId(), e.getOccurredAt(), e.getAction(), e.getTarget(), e.getOutcome());
    }
}
