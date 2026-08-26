package com.careeros.backend.observations;

import com.careeros.backend.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservationsServiceTest {

    private final ThinnessDetector thinnessDetector = mock(ThinnessDetector.class);
    private final SilenceDetector silenceDetector = mock(SilenceDetector.class);
    private final StalenessDetector stalenessDetector = mock(StalenessDetector.class);
    private final ConcentrationDetector concentrationDetector = mock(ConcentrationDetector.class);
    private final DriftDetector driftDetector = mock(DriftDetector.class);

    private final ObservationsService service = new ObservationsService(
            thinnessDetector, silenceDetector, stalenessDetector, concentrationDetector, driftDetector);

    private static final User USER = User.builder().id(1L).githubId(1L).username("u").build();

    @Test
    void returnsAnEmptyListWhenNoDetectorFires() {
        stubAll(List.of());

        assertThat(service.observationsFor(USER)).isEmpty();
    }

    @Test
    void ordersResultsThinnessFirstThenSilenceStalenessConcentrationDrift() {
        Observation thinness = observation(ObservationType.THINNESS);
        Observation silence = observation(ObservationType.SILENCE);
        Observation staleness = observation(ObservationType.STALENESS);
        Observation concentration = observation(ObservationType.CONCENTRATION);
        Observation drift = observation(ObservationType.DRIFT);

        when(thinnessDetector.detect(USER)).thenReturn(List.of(thinness));
        when(silenceDetector.detect(USER)).thenReturn(List.of(silence));
        when(stalenessDetector.detect(USER)).thenReturn(List.of(staleness));
        when(concentrationDetector.detect(USER)).thenReturn(List.of(concentration));
        when(driftDetector.detect(USER)).thenReturn(List.of(drift));

        List<Observation> result = service.observationsFor(USER);

        assertThat(result).containsExactly(thinness, silence, staleness, concentration, drift);
    }

    @Test
    void skipsDetectorsThatFoundNothingWithoutBreakingOrder() {
        Observation silence = observation(ObservationType.SILENCE);
        Observation drift = observation(ObservationType.DRIFT);

        when(thinnessDetector.detect(USER)).thenReturn(List.of());
        when(silenceDetector.detect(USER)).thenReturn(List.of(silence));
        when(stalenessDetector.detect(USER)).thenReturn(List.of());
        when(concentrationDetector.detect(USER)).thenReturn(List.of());
        when(driftDetector.detect(USER)).thenReturn(List.of(drift));

        assertThat(service.observationsFor(USER)).containsExactly(silence, drift);
    }

    private void stubAll(List<Observation> empty) {
        when(thinnessDetector.detect(USER)).thenReturn(empty);
        when(silenceDetector.detect(USER)).thenReturn(empty);
        when(stalenessDetector.detect(USER)).thenReturn(empty);
        when(concentrationDetector.detect(USER)).thenReturn(empty);
        when(driftDetector.detect(USER)).thenReturn(empty);
    }

    private static Observation observation(ObservationType type) {
        return new Observation(type, type + " statement", List.of(), "action");
    }
}
