package com.careeros.backend.onboarding;

import com.careeros.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnboardingRunServiceTest {

    private final OnboardingRunRepository repository = mock(OnboardingRunRepository.class);
    private final OnboardingRunService service = new OnboardingRunService(repository);

    @Test
    void startOrJoinCreatesWhenNoneRunning() {
        User user = new User();
        when(repository.findByUserAndStatus(user, OnboardingStatus.RUNNING))
                .thenReturn(Optional.empty());
        OnboardingRun saved = OnboardingRun.builder().user(user).build();
        when(repository.save(any())).thenReturn(saved);

        var result = service.startOrJoin(user);

        assertThat(result.created()).isTrue();
        assertThat(result.run()).isSameAs(saved);
    }

    @Test
    void startOrJoinJoinsAnAlreadyRunningRunInsteadOfStartingASecondOne() {
        User user = new User();
        OnboardingRun existing = OnboardingRun.builder().user(user).build();
        when(repository.findByUserAndStatus(user, OnboardingStatus.RUNNING))
                .thenReturn(Optional.of(existing));

        var result = service.startOrJoin(user);

        assertThat(result.created()).isFalse();
        assertThat(result.run()).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    /**
     * Two concurrent start requests can both pass the findByUserAndStatus
     * check before either commits — the DB's partial unique index is the
     * real guard, raising here. The second caller must fall back to the
     * row the first one just created, not surface a 500.
     */
    @Test
    void startOrJoinFallsBackToExistingRunOnUniqueViolationRace() {
        User user = new User();
        OnboardingRun winner = OnboardingRun.builder().user(user).build();
        when(repository.findByUserAndStatus(user, OnboardingStatus.RUNNING))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        var result = service.startOrJoin(user);

        assertThat(result.created()).isFalse();
        assertThat(result.run()).isSameAs(winner);
    }
}
