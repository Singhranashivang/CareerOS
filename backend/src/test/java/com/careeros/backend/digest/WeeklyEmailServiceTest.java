package com.careeros.backend.digest;

import com.careeros.backend.achievement.timeline.AchievementTimelineService;
import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.observations.Observation;
import com.careeros.backend.observations.ObservationType;
import com.careeros.backend.observations.ObservationsService;
import com.careeros.backend.suggestions.SuggestionsService;
import com.careeros.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WeeklyEmailServiceTest {

    private final ObservationsService observationsService = mock(ObservationsService.class);
    private final AchievementTimelineService achievementTimelineService = mock(AchievementTimelineService.class);
    private final SuggestionsService suggestionsService = mock(SuggestionsService.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final WeeklyEmailService service = new WeeklyEmailService(
            observationsService, achievementTimelineService, suggestionsService, mailSender, auditLogService);

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").email("u@example.com").build();
    private static final LocalDateTime SINCE = LocalDateTime.now().minusDays(7);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://app");
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@careeros.app");
        when(observationsService.observationsFor(USER)).thenReturn(List.of());
        when(achievementTimelineService.since(USER, SINCE)).thenReturn(List.of());
        when(suggestionsService.repositoriesWithNoAchievements(USER)).thenReturn(List.of());
    }

    @Test
    void sendsNothingWhenAllThreeSourcesAreEmpty() {
        service.sendIfWorthSaying(USER, SINCE);

        verifyNoInteractions(mailSender, auditLogService);
    }

    @Test
    void sendsWhenThereIsAnObservation() {
        Observation observation = new Observation(ObservationType.SILENCE, "You have 7 unposted achievements.",
                List.of("evidence"), "Schedule a post.");
        when(observationsService.observationsFor(USER)).thenReturn(List.of(observation));

        service.sendIfWorthSaying(USER, SINCE);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertSubject(captor.getValue(), "You have 7 unposted achievements.");
        verify(auditLogService).record(USER, AuditAction.WEEKLY_DIGEST_EMAIL_SENT, "weekly", AuditOutcome.SUCCESS);
    }

    @Test
    void skipsWithNoAuditRecordWhenTheUserHasNoEmailOnFile() {
        User noEmail = User.builder().id(2L).githubId(2L).username("u2").email(null).build();
        when(observationsService.observationsFor(noEmail)).thenReturn(List.of(new Observation(
                ObservationType.SILENCE, "stmt", List.of(), "action")));
        when(achievementTimelineService.since(eq(noEmail), any())).thenReturn(List.of());
        when(suggestionsService.repositoriesWithNoAchievements(noEmail)).thenReturn(List.of());

        service.sendIfWorthSaying(noEmail, SINCE);

        verifyNoInteractions(mailSender, auditLogService);
    }

    @Test
    void aFailedSendIsAuditedAsFailureNotThrown() {
        Observation observation = new Observation(ObservationType.SILENCE, "stmt", List.of(), "action");
        when(observationsService.observationsFor(USER)).thenReturn(List.of(observation));
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        service.sendIfWorthSaying(USER, SINCE);

        verify(auditLogService).record(USER, AuditAction.WEEKLY_DIGEST_EMAIL_SENT, "weekly", AuditOutcome.FAILURE);
    }

    private static void assertSubject(SimpleMailMessage message, String expected) {
        org.assertj.core.api.Assertions.assertThat(message.getSubject()).isEqualTo(expected);
        org.assertj.core.api.Assertions.assertThat(message.getTo()).containsExactly("u@example.com");
    }
}
