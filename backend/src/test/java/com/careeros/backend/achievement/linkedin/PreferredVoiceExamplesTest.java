package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.schedule.ScheduledPost;
import com.careeros.backend.schedule.ScheduledPostRepository;
import com.careeros.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreferredVoiceExamplesTest {

    private static final User USER = User.builder().id(1L).build();

    private final ScheduledPostRepository repository = mock(ScheduledPostRepository.class);
    private final PreferredVoiceExamples examples = new PreferredVoiceExamples(repository);

    @BeforeEach
    void setDefaults() {
        setThresholds(3, 3, 1500);
    }

    private void setThresholds(int minPairs, int maxExamples, int maxSampleChars) {
        ReflectionTestUtils.setField(examples, "minPairs", minPairs);
        ReflectionTestUtils.setField(examples, "maxExamples", maxExamples);
        ReflectionTestUtils.setField(examples, "maxSampleChars", maxSampleChars);
    }

    private void givenPairs(String... editedBodies) {
        List<ScheduledPost> pairs = IntStream.range(0, editedBodies.length)
                .mapToObj(i -> ScheduledPost.builder()
                        .body(editedBodies[i])
                        .generatedBody("generated " + i)
                        .build())
                .toList();
        when(repository.findEditedPairs(eq(USER), anyInt())).thenReturn(pairs);
    }

    @Test
    void twoEditedPairsAreNotEnough() {
        givenPairs("first rewrite", "second rewrite");

        assertThat(examples.forUser(USER)).isEmpty();
    }

    @Test
    void theThirdEditedPairTurnsSamplesOn() {
        givenPairs("first rewrite", "second rewrite", "third rewrite");

        assertThat(examples.forUser(USER))
                .containsExactly("first rewrite", "second rewrite", "third rewrite");
    }

    @Test
    void theThresholdIsConfigurable() {
        setThresholds(2, 3, 1500);
        givenPairs("first rewrite", "second rewrite");

        assertThat(examples.forUser(USER)).hasSize(2);
    }

    @Test
    void anImpossiblyHighThresholdSwitchesTheFeatureOff() {
        setThresholds(Integer.MAX_VALUE, 3, 1500);
        givenPairs("first rewrite", "second rewrite", "third rewrite");

        assertThat(examples.forUser(USER)).isEmpty();
    }

    @Test
    void noEditedPairsAtAllYieldsNoSamples() {
        givenPairs();

        assertThat(examples.forUser(USER)).isEmpty();
    }

    @Test
    void maxExamplesCapsHowManyReachThePromptWithoutCappingTheThreshold() {
        setThresholds(3, 2, 1500);
        givenPairs("first rewrite", "second rewrite", "third rewrite");

        assertThat(examples.forUser(USER)).containsExactly("first rewrite", "second rewrite");
    }

    @Test
    void anOverlongRewriteIsSkippedAsASampleButStillCountsTowardTheThreshold() {
        String wall = "x".repeat(200);
        setThresholds(3, 3, 100);
        givenPairs(wall, "short rewrite", "another short rewrite");

        // Threshold met by 3 pairs; only the two usable ones become samples.
        assertThat(examples.forUser(USER)).containsExactly("short rewrite", "another short rewrite");
    }
}
