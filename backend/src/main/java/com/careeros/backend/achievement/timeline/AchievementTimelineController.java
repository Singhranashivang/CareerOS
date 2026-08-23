package com.careeros.backend.achievement.timeline;

import com.careeros.backend.achievement.linkedin.LinkedInPeriodPost;
import com.careeros.backend.achievement.linkedin.LinkedInPostService;
import com.careeros.backend.achievement.record.AchievementEditRequest;
import com.careeros.backend.achievement.record.AchievementPersistenceService;
import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementTimelineController {

    private final AchievementTimelineService achievementTimelineService;
    private final AchievementPersistenceService achievementPersistenceService;
    private final LinkedInPostService linkedInPostService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<AchievementTimelineResponse> timeline() {
        return achievementTimelineService.timeline(currentUserService.require());
    }

    /** Falls through to RateLimitFilter's default READS tier — a DB-only write, no GitHub/LLM call. */
    @PatchMapping("/{id}")
    public AchievementTimelineResponse edit(@PathVariable Long id, @RequestBody AchievementEditRequest request) {
        User user = currentUserService.require();
        try {
            var entity = achievementPersistenceService.edit(user, id, request);
            auditLogService.record(user, AuditAction.ACHIEVEMENT_EDIT, id.toString(), AuditOutcome.SUCCESS);
            return AchievementTimelineResponse.from(entity);
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.ACHIEVEMENT_EDIT, id.toString(), AuditOutcome.FAILURE);
            throw e;
        }
    }

    /** Falls through to RateLimitFilter's default READS tier — a DB-only write, no GitHub/LLM call. */
    @PostMapping("/{id}/dismiss")
    public AchievementTimelineResponse dismiss(@PathVariable Long id) {
        User user = currentUserService.require();
        try {
            var entity = achievementPersistenceService.dismiss(user, id);
            auditLogService.record(user, AuditAction.ACHIEVEMENT_DISMISS, id.toString(), AuditOutcome.SUCCESS);
            return AchievementTimelineResponse.from(entity);
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.ACHIEVEMENT_DISMISS, id.toString(), AuditOutcome.FAILURE);
            throw e;
        }
    }

    /**
     * One post from every non-dismissed achievement in the range, not one
     * achievement — see LinkedInPostService.generatePeriodSummary. Mapped to
     * RateLimitTier.LINKEDIN_POST in RateLimitFilter, same as the
     * single-achievement /achievement/linkedin/{id} endpoint: one real LLM
     * call either way. Not audit-logged, matching that sibling endpoint,
     * which isn't either.
     */
    @PostMapping("/linkedin/period")
    public LinkedInPeriodPost linkedInPeriodSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return linkedInPostService.generatePeriodSummary(currentUserService.require(), from, to);
    }
}
