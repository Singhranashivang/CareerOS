package com.careeros.backend.observations;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.schedule.PostStatus;
import com.careeros.backend.schedule.ScheduledPost;
import com.careeros.backend.schedule.ScheduledPostRepository;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserGoal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Fires when unposted achievements pile up — see ScheduledPost.wasActuallyPublished for the "posted" definition. */
@Component
@RequiredArgsConstructor
public class SilenceDetector implements ObservationDetector {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d");

    private final AchievementRepository achievementRepository;
    private final ScheduledPostRepository scheduledPostRepository;

    @Value("${app.observations.silence.min-unposted:5}")
    private int minUnposted;

    /** AUDIENCE_BUILDING pushes posting harder: fires on a smaller pile than the default. */
    @Value("${app.observations.silence.min-unposted-audience-building:2}")
    private int minUnpostedAudienceBuilding;

    @Override
    public List<Observation> detect(User user) {
        List<ScheduledPost> posted = scheduledPostRepository.findByUserAndStatus(user, PostStatus.POSTED).stream()
                .filter(ScheduledPost::wasActuallyPublished)
                .toList();
        Set<Long> postedAchievementIds = posted.stream()
                .map(ScheduledPost::getAchievement)
                .filter(a -> a != null)
                .map(AchievementEntity::getId)
                .collect(Collectors.toSet());

        List<AchievementEntity> unposted = achievementRepository
                .findByUserAndDismissedFalseOrderByGeneratedAtDesc(user).stream()
                .filter(a -> !postedAchievementIds.contains(a.getId()))
                .toList();

        int threshold = user.getGoal() == UserGoal.AUDIENCE_BUILDING ? minUnpostedAudienceBuilding : minUnposted;
        if (unposted.size() < threshold) {
            return List.of();
        }

        AchievementEntity oldest = unposted.get(unposted.size() - 1);
        OffsetDateTime lastPostedAt = posted.stream()
                .map(ScheduledPost::getPostedAt)
                .filter(p -> p != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        String statement = lastPostedAt == null
                ? "You have %d unposted achievements and have never posted anything.".formatted(unposted.size())
                : "You have %d unposted achievements, the oldest from %s. Nothing posted since %s.".formatted(
                        unposted.size(), oldest.getGeneratedAt().format(DATE), lastPostedAt.format(DATE));

        List<String> evidence = List.of(
                "%d non-dismissed achievements have no posted scheduled post".formatted(unposted.size()),
                "Oldest unposted: \"%s\" (%s)".formatted(oldest.getTitle(), oldest.getGeneratedAt().format(DATE)));

        return List.of(new Observation(ObservationType.SILENCE, statement, evidence,
                "Schedule a post from your suggestions."));
    }
}
