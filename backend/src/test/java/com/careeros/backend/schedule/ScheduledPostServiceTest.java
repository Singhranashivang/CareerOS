package com.careeros.backend.schedule;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostEntity;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.schedule.dto.CreateScheduledPostRequest;
import com.careeros.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledPostServiceTest {

    private static final String GENERATED = "The generated post, as the model wrote it.";

    private final ScheduledPostRepository repository = mock(ScheduledPostRepository.class);
    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final LinkedInPostPersistenceService linkedInPosts = mock(LinkedInPostPersistenceService.class);
    private final ScheduledPostService service =
            new ScheduledPostService(repository, achievementRepository, linkedInPosts);

    @Test
    void cancelRejectsAnInFlightPublish() {
        User user = new User();
        ScheduledPost post = ScheduledPost.builder()
                .user(user)
                .platform(PostPlatform.LINKEDIN)
                .body("body")
                .status(PostStatus.PUBLISHING)
                .build();
        when(repository.findByIdAndUser(1L, user)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.cancel(user, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PUBLISHING");

        // The worker still owns it; cancelling must not have touched the status.
        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHING);
    }

    @Test
    void schedulingAnEditedLinkedInPostStoresBothVersions() {
        User user = new User();
        AchievementEntity achievement = givenAchievementWithGeneratedPost(user);

        ScheduledPost saved = createLinkedInPost(user, achievement, "My own rewrite of it.");

        assertThat(saved.getGeneratedBody()).isEqualTo(GENERATED);
        assertThat(saved.getBody()).isEqualTo("My own rewrite of it.");
    }

    @Test
    void schedulingAnUneditedPostStoresTheSameTextOnBothSides() {
        User user = new User();
        AchievementEntity achievement = givenAchievementWithGeneratedPost(user);

        ScheduledPost saved = createLinkedInPost(user, achievement, GENERATED);

        // Both halves present, identical — findEditedPairs is what filters these out.
        assertThat(saved.getGeneratedBody()).isEqualTo(GENERATED);
        assertThat(saved.getBody()).isEqualTo(GENERATED);
    }

    @Test
    void aPostWithNoGeneratedLinkedInPostBehindItHasNoSnapshot() {
        User user = new User();
        AchievementEntity achievement = AchievementEntity.builder().id(7L).resumeBullet("Did a thing").build();
        when(achievementRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(achievement));
        when(linkedInPosts.findByAchievement(achievement)).thenReturn(Optional.empty());

        ScheduledPost saved = createLinkedInPost(user, achievement, "Written from scratch.");

        assertThat(saved.getGeneratedBody()).isNull();
    }

    @Test
    void aNonLinkedInPostIsNeverSnapshotted() {
        User user = new User();
        AchievementEntity achievement = givenAchievementWithGeneratedPost(user);
        when(repository.save(any(ScheduledPost.class))).thenAnswer(i -> i.getArgument(0));

        service.create(user, new CreateScheduledPostRequest(
                PostPlatform.X, "A tweet.", null, null, null, achievement.getId()));

        // The LinkedIn post cache says nothing about how this user writes on X.
        var saved = captureSaved();
        assertThat(saved.getGeneratedBody()).isNull();
    }

    private AchievementEntity givenAchievementWithGeneratedPost(User user) {
        AchievementEntity achievement = AchievementEntity.builder().id(7L).build();
        when(achievementRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(achievement));
        when(linkedInPosts.findByAchievement(achievement))
                .thenReturn(Optional.of(LinkedInPostEntity.builder().post(GENERATED).build()));
        return achievement;
    }

    private ScheduledPost createLinkedInPost(User user, AchievementEntity achievement, String body) {
        when(repository.save(any(ScheduledPost.class))).thenAnswer(i -> i.getArgument(0));
        service.create(user, new CreateScheduledPostRequest(
                PostPlatform.LINKEDIN, body, null, null, null, achievement.getId()));
        return captureSaved();
    }

    private ScheduledPost captureSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(ScheduledPost.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
