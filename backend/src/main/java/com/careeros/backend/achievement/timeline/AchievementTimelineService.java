package com.careeros.backend.achievement.timeline;

import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AchievementTimelineService {

    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true)
    public List<AchievementTimelineResponse> timeline(User user) {

        return achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(user)
                .stream()
                .map(AchievementTimelineResponse::from)
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
