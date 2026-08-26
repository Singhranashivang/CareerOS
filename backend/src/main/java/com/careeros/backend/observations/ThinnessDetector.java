package com.careeros.backend.observations;

import com.careeros.backend.github.AnalysisOutcome;
import com.careeros.backend.github.GithubRepository;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fires when every repository analysed in the last N weeks came back
 * INSUFFICIENT — nothing shipped in that window cleared the confidence gate.
 * analysisOutcome/lastAnalyzedAt hold only the most recent run per
 * repository, not a full attempt log, so this is necessarily a snapshot: a
 * repo re-analysed later in the window that then passed is correctly not
 * counted (its stored state is no longer INSUFFICIENT).
 */
@Component
@RequiredArgsConstructor
public class ThinnessDetector implements ObservationDetector {

    private final GithubRepositoryRepository githubRepositoryRepository;

    @Value("${app.observations.thinness.weeks:2}")
    private int windowWeeks;

    @Override
    public List<Observation> detect(User user) {
        LocalDateTime since = LocalDateTime.now().minusWeeks(windowWeeks);

        List<GithubRepository> analyzedInWindow = githubRepositoryRepository.findByUser(user).stream()
                .filter(r -> r.getLastAnalyzedAt() != null && r.getLastAnalyzedAt().toLocalDateTime().isAfter(since))
                .toList();

        boolean allInsufficient = !analyzedInWindow.isEmpty() && analyzedInWindow.stream()
                .allMatch(r -> r.getAnalysisOutcome() == AnalysisOutcome.INSUFFICIENT);

        if (!allInsufficient) {
            return List.of();
        }

        List<String> evidence = analyzedInWindow.stream()
                .map(r -> r.getFullName() + ": " + (r.getAnalysisReason() == null ? "no reason recorded" : r.getAnalysisReason()))
                .toList();

        String statement = "Nothing you shipped in the last %d week%s cleared the bar for an achievement — %d repositor%s analyzed, all below the confidence threshold."
                .formatted(windowWeeks, windowWeeks == 1 ? "" : "s",
                        analyzedInWindow.size(), analyzedInWindow.size() == 1 ? "y" : "ies");

        return List.of(new Observation(ObservationType.THINNESS, statement, evidence,
                "Check whether the work was genuinely thin, or the generator needs more signal."));
    }
}
