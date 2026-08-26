package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.record.DismissedClusterSignal;
import com.careeros.backend.achievement.record.DismissedClusterSignalRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checked before evidence-building or an LLM call — see
 * AchievementGeneratorService.analyseCluster. A single dismissal doesn't
 * prove the user rejected the AREA rather than that one cluster's phrasing;
 * a second one, substantially overlapping the same files, does.
 */
@Component
@RequiredArgsConstructor
public class DismissedAreaOverlapGate {

    private final DismissedClusterSignalRepository dismissedClusterSignalRepository;
    private final ObjectMapper objectMapper;

    /** Fraction of the new cluster's paths that must already appear in a prior dismissal to count as overlapping it. */
    @Value("${app.achievement.dismissal.overlap-threshold:0.5}")
    private double overlapThreshold;

    /** How many separately-dismissed clusters must overlap before a new one is skipped outright. */
    @Value("${app.achievement.dismissal.min-repeat-dismissals:2}")
    private int minRepeatDismissals;

    /** Empty when the cluster should proceed to generation. Present with a loggable reason when it should be skipped. */
    public Optional<String> reasonToSkip(User user, String repositoryName, Set<String> newPaths) {

        if (newPaths.isEmpty()) {
            return Optional.empty();
        }

        List<DismissedClusterSignal> priorDismissals =
                dismissedClusterSignalRepository.findByUserAndRepositoryName(user, repositoryName);

        int overlapping = 0;
        for (DismissedClusterSignal signal : priorDismissals) {
            Set<String> dismissedPaths = parsePaths(signal.getFilePathsJson());
            if (dismissedPaths.isEmpty()) {
                continue;
            }

            long shared = newPaths.stream().filter(dismissedPaths::contains).count();
            double overlapRatio = shared / (double) newPaths.size();

            if (overlapRatio >= overlapThreshold) {
                overlapping++;
            }
        }

        if (overlapping >= minRepeatDismissals) {
            return Optional.of(("substantially overlaps %d previously dismissed clusters in this repository "
                    + "(at least %.0f%% of its files each)").formatted(overlapping, overlapThreshold * 100));
        }

        return Optional.empty();
    }

    private Set<String> parsePaths(String filePathsJson) {
        if (filePathsJson == null || filePathsJson.isBlank()) {
            return Set.of();
        }
        try {
            return Set.copyOf(objectMapper.readValue(filePathsJson, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            return Set.of();
        }
    }
}
