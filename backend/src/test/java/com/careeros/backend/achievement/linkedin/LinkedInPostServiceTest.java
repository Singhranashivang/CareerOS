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
                {"paragraphs":["p"],"confidence":0.9}
                """);

        LinkedInPeriodPost result = service.generatePeriodSummary(USER, FROM, TO);

        assertThat(result.post()).isEqualTo("p");
        assertThat(result.confidence()).isEqualTo(0.9);
        verifyNoInteractions(linkedInPostPersistenceService);
    }

    @Test
    void periodPostJoinsTheParagraphsArrayWithBlankLines() {
        // The core of this shape change: paragraphs come back as a JSON
        // array, not one string with "\n\n" embedded — joining is
        // LinkedInPostService's job, not the model's.
        AchievementEntity a = AchievementEntity.builder().title("A").resumeBullet("Did A").build();
        when(achievementRepository.findByUserAndDismissedFalseAndGeneratedAtBetweenOrderByGeneratedAtDesc(
                eq(USER), any(), any())).thenReturn(List.of(a));
        when(linkedInPromptBuilder.buildPeriod(List.of(a), FROM, TO)).thenReturn("prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"paragraphs":["First.","Second.","Third."],"confidence":0.9}
                """);

        LinkedInPeriodPost result = service.generatePeriodSummary(USER, FROM, TO);

        assertThat(result.post()).isEqualTo("First.\n\nSecond.\n\nThird.");
    }

    @Test
    void bannedVocabularyInAPeriodPostIsPostProcessedNotRetried() {
        // Banned words no longer trigger a retry (see LinkedInPostService.
        // scrubBannedVocabulary) — deterministic substitution instead, in a
        // single LLM call.
        AchievementEntity a = AchievementEntity.builder().title("A").resumeBullet("Did A").build();
        when(achievementRepository.findByUserAndDismissedFalseAndGeneratedAtBetweenOrderByGeneratedAtDesc(
                eq(USER), any(), any())).thenReturn(List.of(a));
        when(linkedInPromptBuilder.buildPeriod(List.of(a), FROM, TO)).thenReturn("prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"paragraphs":["I leveraged the plan and enhanced results."],"confidence":0.9}
                """);

        LinkedInPeriodPost result = service.generatePeriodSummary(USER, FROM, TO);

        assertThat(result.post()).isEqualTo("I used the plan and changed results.");
        verify(llmService, times(1)).generate(any());
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
                {"paragraphs":["one short paragraph"],"confidence":0.9}
                """);
        when(llmService.generate("retry-prompt")).thenReturn("""
                {"paragraphs":["a properly shaped retry"],"confidence":0.9}
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
                {"paragraphs":["our team shipped this"],"confidence":0.9}
                """);
        when(llmService.generate("retry-prompt")).thenReturn("""
                {"paragraphs":["I shipped this alone"],"confidence":0.9}
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
    void combinedRejectsANullOrEmptyIdListWithoutCallingTheModel() {
        assertThatThrownBy(() -> service.generateCombined(USER, null))
                .hasMessageContaining("At least one achievement id is required");
        assertThatThrownBy(() -> service.generateCombined(USER, List.of()))
                .hasMessageContaining("At least one achievement id is required");
        verifyNoInteractions(llmService);
    }

    @Test
    void combinedRejectsIfAnyIdIsNotFoundNotOwnedOrDismissed() {
        // The repository query already scopes to user + dismissed=false, so
        // one id silently missing from its result is exactly that case —
        // asking for 2 ids but only 1 comes back.
        when(achievementRepository.findByIdInAndUserAndDismissedFalseOrderByGeneratedAtDesc(
                List.of(1L, 2L), USER)).thenReturn(List.of(
                        AchievementEntity.builder().id(1L).title("A").generatedAt(LocalDateTime.of(2026, 7, 1, 0, 0)).build()));

        assertThatThrownBy(() -> service.generateCombined(USER, List.of(1L, 2L)))
                .hasMessageContaining("not found, not yours, or dismissed");
        verifyNoInteractions(llmService);
    }

    @Test
    void combinedGeneratesFromExactlyTheGivenIdsAndDoesNotPersistAnything() {
        AchievementEntity a = AchievementEntity.builder().id(1L).title("A").resumeBullet("Did A")
                .generatedAt(LocalDateTime.of(2026, 7, 1, 0, 0)).build();
        AchievementEntity b = AchievementEntity.builder().id(2L).title("B").resumeBullet("Did B")
                .generatedAt(LocalDateTime.of(2026, 7, 15, 0, 0)).build();
        // Query returns newest-first, same convention as the period path.
        when(achievementRepository.findByIdInAndUserAndDismissedFalseOrderByGeneratedAtDesc(
                List.of(1L, 2L), USER)).thenReturn(List.of(b, a));
        when(linkedInPromptBuilder.buildPeriod(List.of(b, a), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)))
                .thenReturn("prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"paragraphs":["p"],"confidence":0.9}
                """);

        LinkedInPeriodPost result = service.generateCombined(USER, List.of(1L, 2L));

        assertThat(result.post()).isEqualTo("p");
        verifyNoInteractions(linkedInPostPersistenceService);
    }

    @Test
    void combinedDedupesRepeatedIdsBeforeQuerying() {
        AchievementEntity a = AchievementEntity.builder().id(1L).title("A").resumeBullet("Did A")
                .generatedAt(LocalDateTime.of(2026, 7, 1, 0, 0)).build();
        when(achievementRepository.findByIdInAndUserAndDismissedFalseOrderByGeneratedAtDesc(
                List.of(1L), USER)).thenReturn(List.of(a));
        when(linkedInPromptBuilder.buildPeriod(List.of(a), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)))
                .thenReturn("prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"paragraphs":["p"],"confidence":0.9}
                """);

        LinkedInPeriodPost result = service.generateCombined(USER, List.of(1L, 1L));

        assertThat(result.post()).isEqualTo("p");
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

    @Test
    void bannedVocabularyInASingleAchievementPostIsPostProcessedNotRetried() {
        AchievementEntity achievement = AchievementEntity.builder().id(5L).user(USER).title("A").build();
        when(achievementRepository.findByIdAndUser(5L, USER)).thenReturn(java.util.Optional.of(achievement));
        when(linkedInPostPersistenceService.findByAchievement(achievement)).thenReturn(java.util.Optional.empty());
        when(linkedInPromptBuilder.build(achievement)).thenReturn("prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"headline":"h","post":"I leveraged the plan and enhanced results.","confidence":0.9}
                """);

        LinkedInPost result = service.generate(USER, 5L, false);

        assertThat(result.getPost()).isEqualTo("I used the plan and changed results.");
        verify(llmService, times(1)).generate(any());
    }

    @Test
    void singleAchievementGenerationNeverConsultsTheShapeValidator() {
        // A single achievement rarely has 3+ paragraphs of honest material —
        // the shape (paragraph-break) check only applies to period posts.
        AchievementEntity achievement = AchievementEntity.builder().id(5L).user(USER).title("A").build();
        when(achievementRepository.findByIdAndUser(5L, USER)).thenReturn(java.util.Optional.of(achievement));
        when(linkedInPostPersistenceService.findByAchievement(achievement)).thenReturn(java.util.Optional.empty());
        when(linkedInPromptBuilder.build(achievement)).thenReturn("prompt");
        when(llmService.generate("prompt")).thenReturn("""
                {"headline":"h","post":"one short paragraph, no breaks at all","confidence":0.9}
                """);

        LinkedInPost result = service.generate(USER, 5L, false);

        assertThat(result.getPost()).isEqualTo("one short paragraph, no breaks at all");
        verifyNoInteractions(shapeValidator);
        verify(llmService, times(1)).generate(any());
    }
}
