package com.careeros.backend.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final GithubTokenEncryptor githubTokenEncryptor = mock(GithubTokenEncryptor.class);
    private final UserService service = new UserService(userRepository, githubTokenEncryptor);

    @Test
    void updateGoalAlwaysOverwrites() {
        User user = User.builder().id(1L).goal(UserGoal.JOB_HUNTING).build();
        when(userRepository.save(user)).thenReturn(user);

        User updated = service.updateGoal(user, UserGoal.PERFORMANCE_REVIEW);

        assertThat(updated.getGoal()).isEqualTo(UserGoal.PERFORMANCE_REVIEW);
    }

    @Test
    void setGoalIfUnsetFillsAGoalTheUserHasNeverAnswered() {
        User user = User.builder().id(1L).goal(null).build();
        when(userRepository.save(user)).thenReturn(user);

        User result = service.setGoalIfUnset(user, UserGoal.AUDIENCE_BUILDING);

        assertThat(result.getGoal()).isEqualTo(UserGoal.AUDIENCE_BUILDING);
    }

    @Test
    void setGoalIfUnsetNeverOverwritesAnAlreadyAnsweredGoal() {
        User user = User.builder().id(1L).goal(UserGoal.JOB_HUNTING).build();

        User result = service.setGoalIfUnset(user, UserGoal.AUDIENCE_BUILDING);

        assertThat(result.getGoal()).isEqualTo(UserGoal.JOB_HUNTING);
        verifyNoInteractions(userRepository);
    }

    @Test
    void setGoalIfUnsetIsANoOpForANullGoal() {
        User user = User.builder().id(1L).goal(null).build();

        User result = service.setGoalIfUnset(user, null);

        assertThat(result.getGoal()).isNull();
        verifyNoInteractions(userRepository);
    }
}
