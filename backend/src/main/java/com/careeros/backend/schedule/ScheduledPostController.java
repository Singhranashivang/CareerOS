package com.careeros.backend.schedule;

import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.schedule.dto.CreateScheduledPostRequest;
import com.careeros.backend.schedule.dto.ScheduledPostResponse;
import com.careeros.backend.schedule.dto.UpdateScheduledPostRequest;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scheduled-posts")
@RequiredArgsConstructor
public class ScheduledPostController {

    private final ScheduledPostService scheduledPostService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<ScheduledPostResponse> list() {
        return scheduledPostService.listForUser(currentUserService.require());
    }

    @PostMapping
    public ScheduledPostResponse create(@Valid @RequestBody CreateScheduledPostRequest request) {
        User user = currentUserService.require();
        try {
            ScheduledPostResponse response = scheduledPostService.create(user, request);
            // Only known after a successful save — nothing to target on failure.
            auditLogService.record(user, AuditAction.SCHEDULED_POST_CREATE,
                    response.id().toString(), AuditOutcome.SUCCESS);
            return response;
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.SCHEDULED_POST_CREATE, null, AuditOutcome.FAILURE);
            throw e;
        }
    }

    @PatchMapping("/{id}")
    public ScheduledPostResponse update(@PathVariable Long id,
                                        @RequestBody UpdateScheduledPostRequest request) {
        User user = currentUserService.require();
        try {
            ScheduledPostResponse response = scheduledPostService.update(user, id, request);
            auditLogService.record(user, AuditAction.SCHEDULED_POST_UPDATE, id.toString(), AuditOutcome.SUCCESS);
            return response;
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.SCHEDULED_POST_UPDATE, id.toString(), AuditOutcome.FAILURE);
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        User user = currentUserService.require();
        try {
            scheduledPostService.cancel(user, id);
            auditLogService.record(user, AuditAction.SCHEDULED_POST_CANCEL, id.toString(), AuditOutcome.SUCCESS);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.SCHEDULED_POST_CANCEL, id.toString(), AuditOutcome.FAILURE);
            throw e;
        }
    }
}
