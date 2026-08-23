package com.careeros.backend.achievement.record;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import com.careeros.backend.user.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AchievementPersistenceServiceTest {

    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final AchievementPersistenceService service =
            new AchievementPersistenceService(achievementRepository, new ObjectMapper());

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();
    private static final User OTHER_USER = User.builder().id(2L).githubId(2L).username("v").build();

    private static AchievementEditRequest fullRequest() {
        return new AchievementEditRequest("New Title", "New bullet", "sit", "task", "action", "result");
    }

    @Test
    void editOverwritesAllSixFieldsAndFlagsUserEdited() {
        AchievementEntity entity = AchievementEntity.builder().id(5L).user(USER).title("Old").build();
        when(achievementRepository.findByIdAndUser(5L, USER)).thenReturn(Optional.of(entity));

        AchievementEntity result = service.edit(USER, 5L, fullRequest());

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getResumeBullet()).isEqualTo("New bullet");
        assertThat(result.getStarSituation()).isEqualTo("sit");
        assertThat(result.getStarTask()).isEqualTo("task");
        assertThat(result.getStarAction()).isEqualTo("action");
        assertThat(result.getStarResult()).isEqualTo("result");
        assertThat(result.isUserEdited()).isTrue();
    }

    @Test
    void editRejectsABlankTitleBeforeTouchingTheRepository() {
        var request = new AchievementEditRequest("  ", "bullet", null, null, null, null);

        assertThatThrownBy(() -> service.edit(USER, 5L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("title");
    }

    @Test
    void editOnAnAchievementOwnedByAnotherUserIsDenied() {
        when(achievementRepository.findByIdAndUser(5L, OTHER_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(OTHER_USER, 5L, fullRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void dismissSetsTheFlagAndLeavesOtherFieldsAlone() {
        AchievementEntity entity = AchievementEntity.builder().id(5L).user(USER).title("Keep me").build();
        when(achievementRepository.findByIdAndUser(5L, USER)).thenReturn(Optional.of(entity));

        AchievementEntity result = service.dismiss(USER, 5L);

        assertThat(result.isDismissed()).isTrue();
        assertThat(result.getTitle()).isEqualTo("Keep me");
    }

    @Test
    void dismissOnAnAchievementOwnedByAnotherUserIsDenied() {
        when(achievementRepository.findByIdAndUser(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismiss(OTHER_USER, 5L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
