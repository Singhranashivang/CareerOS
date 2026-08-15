package com.careeros.backend.achievement.weekly;

import com.careeros.backend.achievement.engine.AchievementConfidenceCalculator;
import com.careeros.backend.achievement.engine.AchievementConfidenceGate;
import com.careeros.backend.achievement.engine.AchievementEngine;
import com.careeros.backend.achievement.engine.EvidenceSufficiency;
import com.careeros.backend.achievement.engine.GroundingValidator;
import com.careeros.backend.achievement.evidence.CodeStats;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.evidence.EvidenceBuilder;
import com.careeros.backend.achievement.extractor.Feature;
import com.careeros.backend.achievement.filter.CommitFilter;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendation;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendationService;
import com.careeros.backend.achievement.record.AchievementPersistenceService;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementPersistenceService;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.githubcommit.GithubCommitRepository;
import com.careeros.backend.githubpullrequest.GithubPullRequestRepository;
import com.careeros.backend.user.GithubTokenEncryptor;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the two gates WeeklyAchievementService's own summary path was
 * missing relative to the per-repo generator (EvidenceSufficiency and
 * GroundingValidator) — see AchievementGeneratorServiceTest /
 * AchievementBuilderTest for the equivalent coverage on that path.
 */
class WeeklyAchievementServiceTest {

    private final CommitFilter commitFilter = mock(CommitFilter.class);
    private final EvidenceBuilder evidenceBuilder = mock(EvidenceBuilder.class);
    private final AchievementEngine achievementEngine = mock(AchievementEngine.class);
    private final AchievementPersistenceService achievementPersistenceService = mock(AchievementPersistenceService.class);
    private final WeeklyPromptBuilder weeklyPromptBuilder = mock(WeeklyPromptBuilder.class);
    private final LLMService llmService = mock(LLMService.class);
    private final WeeklyAchievementPersistenceService weeklyAchievementPersistenceService =
            mock(WeeklyAchievementPersistenceService.class);
    private final RepositoryRecommendationService repositoryRecommendationService =
            mock(RepositoryRecommendationService.class);
    private final GithubTokenEncryptor githubTokenEncryptor = mock(GithubTokenEncryptor.class);
    private final AchievementConfidenceCalculator confidenceCalculator = mock(AchievementConfidenceCalculator.class);
    private final AchievementConfidenceGate confidenceGate = mock(AchievementConfidenceGate.class);

    private final WeeklyAchievementService service = new WeeklyAchievementService(
            mock(GithubCommitRepository.class),
            mock(GithubPullRequestRepository.class),
            commitFilter,
            evidenceBuilder,
            achievementEngine,
            achievementPersistenceService,
            weeklyPromptBuilder,
            llmService,
            weeklyAchievementPersistenceService,
            repositoryRecommendationService,
            githubTokenEncryptor,
            confidenceCalculator,
            confidenceGate,
            new EvidenceSufficiency(), // pure logic, no reason to mock it
            new GroundingValidator(),  // pure logic, no reason to mock it
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

    private static final User USER = User.builder().id(1L).githubId(99L).build();
    private static final GithubRepository REPO = GithubRepository.builder()
            .id(4L).name("CareerOS").fullName("Singhranashivang/CareerOS").build();

    private static Evidence thinEvidence() {
        return Evidence.builder()
                .repositoryName("CareerOS")
                .features(List.of())
                .changedFiles(List.of())
                .pullRequestTitles(List.of())
                .technologies(List.of())
                .repositoryFeatures(List.of())
                .codeStats(CodeStats.builder().commitCount(1).filesTouched(1).linesAdded(5).build())
                .build();
    }

    /** GroundingValidator needs actual filenames/messages, not a mock. */
    private static Evidence groundedEvidence() {
        return Evidence.builder()
                .repositoryName("CareerOS")
                .features(List.of(Feature.builder()
                        .name("Commit Sync")
                        .evidence(List.of("Added author filtering to commit sync pipeline"))
                        .build()))
                .changedFiles(List.of())
                .pullRequestTitles(List.of())
                .technologies(List.of())
                .repositoryFeatures(List.of())
                .codeStats(CodeStats.builder().commitCount(10).filesTouched(10).linesAdded(400).build())
                .build();
    }

    private void stubUpTo(Evidence evidence) {
        when(repositoryRecommendationService.recommend(USER)).thenReturn(
                List.of(RepositoryRecommendation.builder().repository(REPO).score(1).reasons(List.of()).build()));
        when(commitFilter.filter(any())).thenReturn(List.of());
        when(evidenceBuilder.build(any(), any(), any(), any())).thenReturn(evidence);
        when(achievementEngine.generate(evidence)).thenReturn(List.of());
        when(weeklyPromptBuilder.build(evidence)).thenReturn("prompt");
    }

    @Test
    void thinEvidenceNeverCallsTheModelForASummary() {
        stubUpTo(thinEvidence());

        WeeklySummary summary = service.generate(USER);

        assertThat(summary.isInsufficient()).isTrue();
        verify(llmService, never()).generate(anyString());
        verifyNoInteractions(weeklyAchievementPersistenceService);
    }

    @Test
    void aWellFormedSummaryThatMatchesNoEvidenceIsRejectedAsUngrounded() {
        stubUpTo(groundedEvidence());
        when(llmService.generate("prompt")).thenReturn("""
                {"title":"Cloud Infrastructure Migration",
                 "summary":"Migrated production workloads to Kubernetes clusters",
                 "highlights":[],"technologies":[],"confidence":0.95}
                """);

        WeeklySummary summary = service.generate(USER);

        assertThat(summary.isInsufficient()).isTrue();
        assertThat(summary.getReason()).contains("not grounded");
        // Rejected before it was ever scored.
        verifyNoInteractions(confidenceCalculator);
        verifyNoInteractions(weeklyAchievementPersistenceService);
    }

    @Test
    void aGroundedSummaryAboveTheFloorIsPersistedWithComputedConfidence() {
        stubUpTo(groundedEvidence());
        when(llmService.generate("prompt")).thenReturn("""
                {"title":"Commit Sync Progress",
                 "summary":"Added author filtering to commit sync.",
                 "highlights":[],"technologies":[],"confidence":0.95}
                """);
        when(confidenceCalculator.calculate(any())).thenReturn(0.8);
        when(confidenceGate.passes(0.8)).thenReturn(true);

        WeeklySummary summary = service.generate(USER);

        assertThat(summary.isInsufficient()).isFalse();
        assertThat(summary.getTitle()).isEqualTo("Commit Sync Progress");
        // Computed, not the model's self-reported 0.95.
        assertThat(summary.getConfidence()).isEqualTo(0.8);
        verify(weeklyAchievementPersistenceService).save(any());
    }
}
