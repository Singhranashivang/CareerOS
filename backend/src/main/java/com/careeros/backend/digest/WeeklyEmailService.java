package com.careeros.backend.digest;

import com.careeros.backend.achievement.timeline.AchievementTimelineResponse;
import com.careeros.backend.achievement.timeline.AchievementTimelineService;
import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.observations.Observation;
import com.careeros.backend.observations.ObservationsService;
import com.careeros.backend.suggestions.SuggestionsService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The push side of the weekly digest — WeeklyDigestService calls this after
 * every scheduled run, whether or not that run produced a new achievement.
 * Sends only when there's something worth saying (an observation, a new
 * achievement, or a repository needing analysis); silently does nothing
 * otherwise, same as ObservationsService returning an empty list rather than
 * filler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyEmailService {

    private final ObservationsService observationsService;
    private final AchievementTimelineService achievementTimelineService;
    private final SuggestionsService suggestionsService;
    private final JavaMailSender mailSender;
    private final AuditLogService auditLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.digest-email.from}")
    private String fromAddress;

    public void sendIfWorthSaying(User user, LocalDateTime since) {

        List<Observation> observations = observationsService.observationsFor(user);
        List<AchievementTimelineResponse> newAchievements = achievementTimelineService.since(user, since);
        List<String> reposNeedingAnalysis = suggestionsService.repositoriesWithNoAchievements(user);

        var content = WeeklyEmailContentBuilder.build(observations, newAchievements, reposNeedingAnalysis, frontendUrl);
        if (content.isEmpty()) {
            log.debug("Nothing worth emailing user {} this week", user.getId());
            return;
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.info("Skipping weekly digest email for user {} — no email on file", user.getId());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject(content.get().subject());
            message.setText(content.get().body());
            mailSender.send(message);

            log.info("Sent weekly digest email to user {}", user.getId());
            auditLogService.record(user, AuditAction.WEEKLY_DIGEST_EMAIL_SENT, "weekly", AuditOutcome.SUCCESS);

        } catch (Exception e) {
            // SMTP not configured, credentials wrong, address rejected — never
            // let a failed email turn the digest run itself into a failure;
            // the sync/analyze work already succeeded and is already recorded.
            log.warn("Failed to send weekly digest email to user {}", user.getId(), e);
            auditLogService.record(user, AuditAction.WEEKLY_DIGEST_EMAIL_SENT, "weekly", AuditOutcome.FAILURE);
        }
    }
}
