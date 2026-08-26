package com.careeros.backend.achievement.timeline;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserGoal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AchievementTimelineService {

    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true)
    public List<AchievementTimelineResponse> timeline(User user) {

        List<AchievementEntity> achievements =
                achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(user);

        if (user.getGoal() == UserGoal.PERFORMANCE_REVIEW) {
            achievements = groupedByQuarter(achievements);
        }

        return achievements.stream()
                .map(AchievementTimelineResponse::from)
                .toList();
    }

    /**
     * PERFORMANCE_REVIEW: most recent quarter first, and within a quarter the
     * highest-confidence achievement first — confidence already tracks
     * change magnitude/scope (see AchievementConfidenceCalculator), the same
     * signal this goal favours. Same rows as the default order, regrouped —
     * nothing here changes which achievements are included.
     */
    private static List<AchievementEntity> groupedByQuarter(List<AchievementEntity> achievements) {
        return achievements.stream()
                .sorted(Comparator
                        .comparing((AchievementEntity a) -> AchievementTimelineResponse.quarterOf(a.getGeneratedAt()),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AchievementEntity::getConfidence, Comparator.reverseOrder()))
                .toList();
    }

    /** Backs GET /api/digest/latest — achievements generated since a given point (the last digest run). */
    @Transactional(readOnly = true)
    public List<AchievementTimelineResponse> since(User user, LocalDateTime after) {
        return achievementRepository.findByUserAndDismissedFalseAndGeneratedAtAfterOrderByGeneratedAtDesc(user, after)
                .stream()
                .map(AchievementTimelineResponse::from)
                .toList();
    }
}
