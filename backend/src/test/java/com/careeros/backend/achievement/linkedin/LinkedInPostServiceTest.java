package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LinkedInPostServiceTest {

    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final LinkedInPromptBuilder linkedInPromptBuilder = mock(LinkedInPromptBuilder.class);
    private final LinkedInPostShapeValidator shapeValidator = mock(LinkedInPostShapeValidator.class);
    private final LinkedInPostSoloAuthorValidator soloAuthorValidator = mock(LinkedInPostSoloAuthorValidator.class);
    private final LLMService llmService = mock(LLMService.class);
    private final LinkedInPostPersistenceService linkedInPostPersistenceService =
            mock(LinkedInPostPersistenceService.class);

    // Mocked (defaults to "no violations", per Mockito's empty-list default) rather
    // than the real validators, so tests unrelated to shape/solo-author don't need
    // a placeholder post carefully worded to avoid tripping them. The dedicated
    // tests below stub these explicitly instead.
    private final LinkedInPostService service = new LinkedInPostService(
            achievementRepository, linkedInPromptBuilder, shapeValidator, soloAuthorValidator,
            llmService, linkedInPostPersistenceService,
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 1);

    @Test
    void rejectsAFromDateAfterTheToDate() {
        assertThatThrownBy(() -> service.generatePeriodSummary(USER, TO, FROM))
                .hasMessageContaining("from must not be after to");
        verifyNoInteractions(llmService);
    }

    @Test
    void rejectsAnEmptyRangeWithoutCallingTheModel() {
        when(achievementRepository.findByUserAndDismissedFalseAndGeneratedAtBetweenOrderByGeneratedAtDesc(
                eq(USER), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generatePeriodSummary(USER, FROM, TO))
                .hasMessageContaining("No achievements found");
        verifyNoInteractions(llmService);
    }

    @Test
    void generatesFromEveryAchievementInRangeAndDoesNotPersistAnything() {
        AchievementEntity a = AchievementEntity.builder().title("A").resumeBullet("Did A").build();
        when(achievementRepository.findByUserAndDismissedFalseAndGeneratedAtBetweenOrderByGeneratedAtDesc(
                eq(USER), eq(FROM.atStartOfDay()), eq(TO.atTime(23, 59, 59))))
                .thenReturn(List.of(a));
        when(linkedInPromptBuilder.buildPeriod(List.of(a), FROM, TO)).thenReturn("prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"post":"p","confidence":0.9}
                """);

        LinkedInPeriodPost result = service.generatePeriodSummary(USER, FROM, TO);

        assertThat(result.post()).isEqualTo("p");
        assertThat(result.confidence()).isEqualTo(0.9);
        verifyNoInteractions(linkedInPostPersistenceService);
    }

    @Test
    void retriesOnceOnABannedWordThenKeepsTheCleanerAttempt() {
        AchievementEntity a = AchievementEntity.builder().title("A").resumeBullet("Did A").build();
        when(achievementRepository.findByUserAndDismissedFalseAndGeneratedAtBetweenOrderByGeneratedAtDesc(
                eq(USER), any(), any())).thenReturn(List.of(a));
        when(linkedInPromptBuilder.buildPeriod(List.of(a), FROM, TO)).thenReturn("prompt");
        when(linkedInPromptBuilder.buildPeriodRetry(eq(List.of(a)), eq(FROM), eq(TO), any()))
                .thenReturn("retry-prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"post":"leveraged a robust approach","confidence":0.9}
                """);
        when(llmService.generate("retry-prompt")).thenReturn("""
                {"post":"used a plain approach","confidence":0.9}
                """);

        LinkedInPeriodPost result = service.generatePeriodSummary(USER, FROM, TO);

        assertThat(result.post()).isEqualTo("used a plain approach");
        verify(llmService).generate("prompt");
        verify(llmService).generate("retry-prompt");
    }

    @Test
    void retriesOnceOnAShapeViolationThenKeepsTheCleanerAttempt() {
        AchievementEntity a = AchievementEntity.builder().title("A").resumeBullet("Did A").build();
        when(achievementRepository.findByUserAndDismissedFalseAndGeneratedAtBetweenOrderByGeneratedAtDesc(
                eq(USER), any(), any())).thenReturn(List.of(a));
        when(linkedInPromptBuilder.buildPeriod(List.of(a), FROM, TO)).thenReturn("prompt");
        when(linkedInPromptBuilder.buildPeriodRetry(eq(List.of(a)), eq(FROM), eq(TO), any()))
                .thenReturn("retry-prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"post":"one short paragraph","confidence":0.9}
                """);
        when(llmService.generate("retry-prompt")).thenReturn("""
                {"post":"a properly shaped retry","confidence":0.9}
                """);
        // First attempt: one paragraph, no blank-line breaks. Retry: shaped correctly.
        when(shapeValidator.violationsIn("one short paragraph"))
                .thenReturn(List.of("only 0 paragraph breaks"));
        when(shapeValidator.violationsIn("a properly shaped retry")).thenReturn(List.of());

        LinkedInPeriodPost result = service.generatePeriodSummary(USER, FROM, TO);

        assertThat(result.post()).isEqualTo("a properly shaped retry");
        verify(llmService).generate("prompt");
        verify(llmService).generate("retry-prompt");
    }

    @Test
    void retriesOnceOnASoloAuthorViolationThenKeepsTheCleanerAttempt() {
        AchievementEntity a = AchievementEntity.builder().title("A").resumeBullet("Did A").build();
        when(achievementRepository.findByUserAndDismissedFalseAndGeneratedAtBetweenOrderByGeneratedAtDesc(
                eq(USER), any(), any())).thenReturn(List.of(a));
        when(linkedInPromptBuilder.buildPeriod(List.of(a), FROM, TO)).thenReturn("prompt");
        when(linkedInPromptBuilder.buildPeriodRetry(eq(List.of(a)), eq(FROM), eq(TO), any()))
                .thenReturn("retry-prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"post":"our team shipped this","confidence":0.9}
                """);
        when(llmService.generate("retry-prompt")).thenReturn("""
                {"post":"I shipped this alone","confidence":0.9}
                """);
        when(soloAuthorValidator.violationsIn("our team shipped this"))
                .thenReturn(List.of("our", "team"));
        when(soloAuthorValidator.violationsIn("I shipped this alone")).thenReturn(List.of());

        LinkedInPeriodPost result = service.generatePeriodSummary(USER, FROM, TO);

        assertThat(result.post()).isEqualTo("I shipped this alone");
        verify(llmService).generate("prompt");
        verify(llmService).generate("retry-prompt");
    }

    @Test
    void generateForOneAchievementStillWorksUnchanged() {
        AchievementEntity achievement = AchievementEntity.builder().id(5L).user(USER).title("A").build();
        when(achievementRepository.findByIdAndUser(5L, USER)).thenReturn(java.util.Optional.of(achievement));
        when(linkedInPostPersistenceService.findByAchievement(achievement)).thenReturn(java.util.Optional.empty());
        when(linkedInPromptBuilder.build(achievement)).thenReturn("prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"headline":"h","post":"p","confidence":0.9}
                """);

        LinkedInPost result = service.generate(USER, 5L, false);

        assertThat(result.getHeadline()).isEqualTo("h");
        verify(linkedInPostPersistenceService).save(any());
    }
}
