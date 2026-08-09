package com.careeros.backend.schedule;

import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledPostServiceTest {

    private final ScheduledPostRepository repository = mock(ScheduledPostRepository.class);
    private final ScheduledPostService service =
            new ScheduledPostService(repository, mock(AchievementRepository.class));

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
}
