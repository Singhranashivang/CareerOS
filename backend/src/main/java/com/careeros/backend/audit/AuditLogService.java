package com.careeros.backend.audit;

import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Security audit trail — sign-in/out, sync, analyze, scheduled post
 * mutations, rate-limit rejections. Never pass tokens, request bodies, or
 * generated content as target: an id or short identifier only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * REQUIRES_NEW so a FAILURE row survives even when the action it's
     * recording rolled back its own transaction — that's exactly the case an
     * audit trail exists to capture. Never throws: a broken audit write must
     * not turn into a broken request for the user who triggered it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user, AuditAction action, String target, AuditOutcome outcome) {
        try {
            auditLogRepository.save(AuditLogEntity.builder()
                    .user(user)
                    .action(action)
                    .target(target)
                    .outcome(outcome)
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to write audit log entry: user={} action={} target={} outcome={}",
                    user == null ? null : user.getId(), action, target, outcome, e);
        }
    }
}
