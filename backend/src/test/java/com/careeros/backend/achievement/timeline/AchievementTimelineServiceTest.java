package com.careeros.backend.achievement.timeline;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserGoal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AchievementTimelineServiceTest {

    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final AchievementTimelineService service = new AchievementTimelineService(achievementRepository);

    private static AchievementEntity achievement(Long id, double confidence, LocalDateTime generatedAt) {
        return AchievementEntity.builder().id(id).confidence(confidence).generatedAt(generatedAt).build();
    }

    @Test
    void everyEntryCarriesItsQuarterRegardlessOfGoal() {
        User user = User.builder().id(1L).goal(null).build();
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(user)).thenReturn(List.of(
                achievement(1L, 0.5, LocalDateTime.of(2026, 2, 10, 0, 0))));

        var result = service.timeline(user);

        assertThat(result.get(0).getQuarter()).isEqualTo("2026-Q1");
    }

    @Test
    void withoutAGoalTheRepositoryOrderIsPreservedUnchanged() {
        User user = User.builder().id(1L).goal(null).build();
        AchievementEntity newer = achievement(1L, 0.2, LocalDateTime.of(2026, 6, 1, 0, 0));
        AchievementEntity older = achievement(2L, 0.9, LocalDateTime.of(2026, 1, 1, 0, 0));
        // Repository already orders generatedAt desc — service must not reorder when goal isn't PERFORMANCE_REVIEW.
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(user))
                .thenReturn(List.of(newer, older));

        var result = service.timeline(user);

        assertThat(result).extracting(AchievementTimelineResponse::getId).containsExactly(1L, 2L);
    }

    @Test
    void performanceReviewGroupsByQuarterMostRecentFirst() {
        User user = User.builder().id(1L).goal(UserGoal.PERFORMANCE_REVIEW).build();
        AchievementEntity q1 = achievement(1L, 0.9, LocalDateTime.of(2026, 2, 1, 0, 0));
        AchievementEntity q2 = achievement(2L, 0.5, LocalDateTime.of(2026, 5, 1, 0, 0));
        // Repository order deliberately not already grouped by quarter.
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(user))
                .thenReturn(List.of(q1, q2));

        var result = service.timeline(user);

        assertThat(result).extracting(AchievementTimelineResponse::getId).containsExactly(2L, 1L);
        assertThat(result.get(0).getQuarter()).isEqualTo("2026-Q2");
        assertThat(result.get(1).getQuarter()).isEqualTo("2026-Q1");
    }

    @Test
    void performanceReviewOrdersByConfidenceDescendingWithinAQuarter() {
        User user = User.builder().id(1L).goal(UserGoal.PERFORMANCE_REVIEW).build();
        AchievementEntity lowConfidence = achievement(1L, 0.2, LocalDateTime.of(2026, 3, 1, 0, 0));
        AchievementEntity highConfidence = achievement(2L, 0.9, LocalDateTime.of(2026, 3, 20, 0, 0));
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(user))
                .thenReturn(List.of(lowConfidence, highConfidence));

        var result = service.timeline(user);

        assertThat(result).extracting(AchievementTimelineResponse::getId).containsExactly(2L, 1L);
    }
}
