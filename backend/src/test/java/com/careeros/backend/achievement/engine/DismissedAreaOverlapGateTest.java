package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.record.DismissedClusterSignal;
import com.careeros.backend.achievement.record.DismissedClusterSignalRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DismissedAreaOverlapGateTest {

    private final DismissedClusterSignalRepository repository = mock(DismissedClusterSignalRepository.class);
    private final DismissedAreaOverlapGate gate = new DismissedAreaOverlapGate(repository, new ObjectMapper());

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();

    {
        // @Value fields, only set by Spring's container normally.
        ReflectionTestUtils.setField(gate, "overlapThreshold", 0.5);
        ReflectionTestUtils.setField(gate, "minRepeatDismissals", 2);
    }

    private static DismissedClusterSignal signal(String... paths) throws Exception {
        return DismissedClusterSignal.builder()
                .filePathsJson(new ObjectMapper().writeValueAsString(List.of(paths)))
                .build();
    }

    @Test
    void proceedsWhenNoPriorDismissalsExist() {
        when(repository.findByUserAndRepositoryName(USER, "repo")).thenReturn(List.of());

        assertThat(gate.reasonToSkip(USER, "repo", Set.of("a.java"))).isEmpty();
    }

    @Test
    void proceedsWhenOnlyOnePriorDismissalOverlaps() throws Exception {
        when(repository.findByUserAndRepositoryName(USER, "repo"))
                .thenReturn(List.of(signal("a.java", "b.java")));

        assertThat(gate.reasonToSkip(USER, "repo", Set.of("a.java"))).isEmpty();
    }

    @Test
    void skipsWhenTwoPriorDismissalsSubstantiallyOverlap() throws Exception {
        // Both dismissed clusters share 100% of the new cluster's one file.
        when(repository.findByUserAndRepositoryName(USER, "repo")).thenReturn(List.of(
                signal("a.java", "x.java"),
                signal("a.java", "y.java")));

        assertThat(gate.reasonToSkip(USER, "repo", Set.of("a.java")))
                .isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("2 previously dismissed clusters");
    }

    @Test
    void aDismissalBelowTheOverlapThresholdDoesNotCount() throws Exception {
        // Shares only 1 of the new cluster's 3 files (33%) — below the 50% threshold.
        when(repository.findByUserAndRepositoryName(USER, "repo")).thenReturn(List.of(
                signal("a.java"),
                signal("a.java")));

        assertThat(gate.reasonToSkip(USER, "repo", Set.of("a.java", "b.java", "c.java"))).isEmpty();
    }

    @Test
    void aSignalWithNoRecordedPathsIsIgnored() {
        when(repository.findByUserAndRepositoryName(USER, "repo")).thenReturn(List.of(
                DismissedClusterSignal.builder().filePathsJson(null).build(),
                DismissedClusterSignal.builder().filePathsJson("").build()));

        assertThat(gate.reasonToSkip(USER, "repo", Set.of("a.java"))).isEmpty();
    }

    @Test
    void anEmptyNewClusterAlwaysProceedsWithoutQueryingPriorDismissals() {
        assertThat(gate.reasonToSkip(USER, "repo", Set.of())).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(repository);
    }
}
