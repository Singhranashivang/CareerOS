package com.careeros.backend.achievement.record;

import com.careeros.backend.achievement.engine.Achievement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import com.careeros.backend.user.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AchievementPersistenceServiceTest {

    private final AchievementRepository achievementRepository = mock(AchievementRepository.class);
    private final DismissedClusterSignalRepository dismissedClusterSignalRepository =
            mock(DismissedClusterSignalRepository.class);
    private final AchievementPersistenceService service =
            new AchievementPersistenceService(achievementRepository, dismissedClusterSignalRepository, new ObjectMapper());

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
    void dismissRecordsTheClustersTechnologiesAndFilePathsAsASignal() {
        AchievementEntity entity = AchievementEntity.builder().id(5L).user(USER).title("Keep me")
                .repositoryName("repo")
                .technologiesJson("[\"Java\"]")
                .filePathsJson("[\"src/Foo.java\"]")
                .build();
        when(achievementRepository.findByIdAndUser(5L, USER)).thenReturn(Optional.of(entity));

        service.dismiss(USER, 5L);

        ArgumentCaptor<DismissedClusterSignal> captor = ArgumentCaptor.forClass(DismissedClusterSignal.class);
        verify(dismissedClusterSignalRepository).save(captor.capture());
        DismissedClusterSignal signal = captor.getValue();
        assertThat(signal.getUser()).isEqualTo(USER);
        assertThat(signal.getRepositoryName()).isEqualTo("repo");
        assertThat(signal.getTechnologiesJson()).isEqualTo("[\"Java\"]");
        assertThat(signal.getFilePathsJson()).isEqualTo("[\"src/Foo.java\"]");
        assertThat(signal.getDismissedAt()).isNotNull();
    }

    @Test
    void dismissOnAnAchievementOwnedByAnotherUserIsDenied() {
        when(achievementRepository.findByIdAndUser(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismiss(OTHER_USER, 5L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void saveEntityScrubsBannedVocabularyFromAllSevenFieldsBeforePersisting() {
        AchievementEntity entity = AchievementEntity.builder()
                .title("Enhanced Achievement Engine")
                .resumeBullet("I leveraged the new engine.")
                .summary("This streamlined the process.")
                .starSituation("The old engine was robust.")
                .starTask("To enhance the system's scalability.")
                .starAction("I utilized a new approach.")
                .starResult("This enhanced reliability.")
                .build();
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AchievementEntity result = service.saveEntity(entity);

        assertThat(result.getTitle()).isEqualTo("Changed Achievement Engine");
        assertThat(result.getResumeBullet()).isEqualTo("I used the new engine.");
        assertThat(result.getSummary()).isEqualTo("This simplified the process.");
        assertThat(result.getStarSituation()).isEqualTo("The old engine was.");
        assertThat(result.getStarTask()).isEqualTo("To change the system's scalability.");
        assertThat(result.getStarAction()).isEqualTo("I used a new approach.");
        assertThat(result.getStarResult()).isEqualTo("This changed reliability.");
    }

    @Test
    void saveScrubsTitleAndSummaryForTheWeeklyPipelinePath() {
        Achievement achievement = Achievement.builder()
                .title("Enhanced Hacktoberfest Progress")
                .summary("I leveraged several algorithm solutions this week.")
                .repository("Hacktoberfest2025")
                .confidence(0.8)
                .build();
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.save(USER, achievement);

        var captor = ArgumentCaptor.forClass(AchievementEntity.class);
        verify(achievementRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Changed Hacktoberfest Progress");
        assertThat(captor.getValue().getSummary())
                .isEqualTo("I used several algorithm solutions this week.");
    }

    @Test
    void scrubbingNeverBlanksAFieldEvenWhenTheWholeTextIsAnUnknownInflectionMatch() {
        // "Enhancement" (a noun) has no clean inflected substitution, so the
        // scrubber's fallback is to drop the containing sentence — the whole
        // title here, since there's no period. A banned word left in place
        // beats a blank title.
        AchievementEntity entity = AchievementEntity.builder()
                .title("Achievement Engine Enhancement")
                .build();
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AchievementEntity result = service.saveEntity(entity);

        assertThat(result.getTitle()).isEqualTo("Achievement Engine Enhancement");
    }

    @Test
    void editIsNotScrubbedAUsersOwnEditIsTheirOwnVoice() {
        AchievementEntity entity = AchievementEntity.builder().id(5L).user(USER).title("Old").build();
        when(achievementRepository.findByIdAndUser(5L, USER)).thenReturn(Optional.of(entity));
        var request = new AchievementEditRequest(
                "I leveraged a robust approach", "bullet", null, null, null, null);

        AchievementEntity result = service.edit(USER, 5L, request);

        assertThat(result.getTitle()).isEqualTo("I leveraged a robust approach");
    }
}
