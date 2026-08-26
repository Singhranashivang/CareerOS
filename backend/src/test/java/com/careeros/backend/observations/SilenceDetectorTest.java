package com.careeros.backend.observations;

import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.schedule.PostStatus;
import com.careeros.backend.schedule.ScheduledPost;
import com.careeros.backend.schedule.ScheduledPostRepository;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserGoal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SilenceDetectorTest {

    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final ScheduledPostRepository scheduledPostRepository = mock(ScheduledPostRepository.class);
    private final SilenceDetector detector = new SilenceDetector(achievementRepository, scheduledPostRepository);

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();

    @Test
    void firesWhenUnpostedAchievementsMeetTheThreshold() {
        List<AchievementEntity> achievements = List.of(
                achievement(1L, "a"), achievement(2L, "b"), achievement(3L, "c"));
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(achievements);
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of());
        ReflectionTestUtils.setField(detector, "minUnposted", 3);

        List<Observation> observations = detector.detect(USER);

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).statement()).contains("never posted anything");
    }

    @Test
    void excludesAchievementsThatHaveARealPostedScheduledPost() {
        AchievementEntity posted = achievement(1L, "a");
        List<AchievementEntity> achievements = List.of(posted, achievement(2L, "b"), achievement(3L, "c"));
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(achievements);
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of(
                ScheduledPost.builder().achievement(posted).externalPostId("li-123")
                        .postedAt(java.time.OffsetDateTime.now()).build()));
        ReflectionTestUtils.setField(detector, "minUnposted", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }

    @Test
    void aNoOpPublishedPostDoesNotCountAsPosted() {
        AchievementEntity noOpPosted = achievement(1L, "a");
        List<AchievementEntity> achievements = List.of(noOpPosted, achievement(2L, "b"), achievement(3L, "c"));
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER)).thenReturn(achievements);
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of(
                ScheduledPost.builder().achievement(noOpPosted).externalPostId("noop-abc")
                        .postedAt(java.time.OffsetDateTime.now()).build()));
        ReflectionTestUtils.setField(detector, "minUnposted", 3);

        assertThat(detector.detect(USER)).hasSize(1);
    }

    @Test
    void doesNotFireBelowTheThreshold() {
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(achievement(1L, "a")));
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of());
        ReflectionTestUtils.setField(detector, "minUnposted", 3);

        assertThat(detector.detect(USER)).isEmpty();
    }

    @Test
    void audienceBuildingFiresOnASmallerPileThanTheDefaultThreshold() {
        User audienceBuildingUser = User.builder().id(2L).githubId(2L).username("u2")
                .goal(UserGoal.AUDIENCE_BUILDING).build();
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(audienceBuildingUser))
                .thenReturn(List.of(achievement(1L, "a"), achievement(2L, "b")));
        when(scheduledPostRepository.findByUserAndStatus(audienceBuildingUser, PostStatus.POSTED))
                .thenReturn(List.of());
        when(achievementRepository.findByUserAndDismissedFalseOrderByGeneratedAtDesc(USER))
                .thenReturn(List.of(achievement(1L, "a"), achievement(2L, "b")));
        when(scheduledPostRepository.findByUserAndStatus(USER, PostStatus.POSTED)).thenReturn(List.of());
        ReflectionTestUtils.setField(detector, "minUnposted", 5);
        ReflectionTestUtils.setField(detector, "minUnpostedAudienceBuilding", 2);

        // Below the default (5) threshold, but the goal pushes posting harder.
        assertThat(detector.detect(audienceBuildingUser)).hasSize(1);
        // The same size pile, no goal, stays below the default threshold.
        assertThat(detector.detect(USER)).isEmpty();
    }

    private static AchievementEntity achievement(Long id, String title) {
        return AchievementEntity.builder().id(id).title(title).generatedAt(LocalDateTime.now()).build();
    }
}
