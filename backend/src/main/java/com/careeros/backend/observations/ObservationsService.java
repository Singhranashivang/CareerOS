package com.careeros.backend.observations;

import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Runs every detector and concatenates, in a fixed rule-priority order —
 * "strongest" for a caller that only wants one (WeeklyEmailService's subject
 * line) is simply the first element. Thinness/Silence lead because they're
 * about the user's own output stalling, most urgent to act on this week;
 * Staleness/Concentration are backlog/coverage signals; Drift is the
 * softest, nice-to-know signal. Each detector already orders its own
 * multiple results most-severe-first.
 */
@Service
@RequiredArgsConstructor
public class ObservationsService {

    private final ThinnessDetector thinnessDetector;
    private final SilenceDetector silenceDetector;
    private final StalenessDetector stalenessDetector;
    private final ConcentrationDetector concentrationDetector;
    private final DriftDetector driftDetector;

    @Transactional(readOnly = true)
    public List<Observation> observationsFor(User user) {
        return List.of(thinnessDetector, silenceDetector, stalenessDetector, concentrationDetector, driftDetector)
                .stream()
                .flatMap(detector -> detector.detect(user).stream())
                .toList();
    }
}
