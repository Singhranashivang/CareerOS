package com.careeros.backend.digest;

import com.careeros.backend.achievement.timeline.AchievementTimelineResponse;
import com.careeros.backend.observations.Observation;

import java.util.List;
import java.util.Optional;

/**
 * Turns this week's observations/new achievements/needs-analysis repos into
 * an email subject + body. Pure — no I/O — so the "skip when all three are
 * empty" gate and the subject fallback are unit-testable without a database
 * or a mail server.
 */
public final class WeeklyEmailContentBuilder {

    public record EmailContent(String subject, String body) {
    }

    private WeeklyEmailContentBuilder() {
    }

    public static Optional<EmailContent> build(
            List<Observation> observations,
            List<AchievementTimelineResponse> newAchievements,
            List<String> reposNeedingAnalysis,
            String frontendUrl
    ) {
        if (observations.isEmpty() && newAchievements.isEmpty() && reposNeedingAnalysis.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new EmailContent(
                subject(observations, newAchievements, reposNeedingAnalysis),
                body(observations, newAchievements, reposNeedingAnalysis, frontendUrl)));
    }

    /**
     * The observation, not "Your weekly summary" — only falls back to the
     * next-strongest signal (a new achievement, then the needs-analysis
     * backlog) when there is no observation to lead with at all.
     */
    private static String subject(
            List<Observation> observations,
            List<AchievementTimelineResponse> newAchievements,
            List<String> reposNeedingAnalysis
    ) {
        if (!observations.isEmpty()) {
            return observations.get(0).statement();
        }
        if (!newAchievements.isEmpty()) {
            return "New achievement: " + newAchievements.get(0).getTitle();
        }
        int others = reposNeedingAnalysis.size() - 1;
        return others == 0
                ? reposNeedingAnalysis.get(0) + " is ready to analyze"
                : "%s and %d other repositor%s are ready to analyze"
                        .formatted(reposNeedingAnalysis.get(0), others, others == 1 ? "y" : "ies");
    }

    /** Leads with the strongest observation, then new achievements, then the needs-analysis backlog. */
    private static String body(
            List<Observation> observations,
            List<AchievementTimelineResponse> newAchievements,
            List<String> reposNeedingAnalysis,
            String frontendUrl
    ) {
        StringBuilder body = new StringBuilder();

        if (!observations.isEmpty()) {
            Observation strongest = observations.get(0);
            body.append(strongest.statement()).append("\n\n");
            for (String fact : strongest.evidence()) {
                body.append("- ").append(fact).append("\n");
            }
            body.append("\nSuggested: ").append(strongest.suggestedAction()).append("\n\n");

            if (observations.size() > 1) {
                body.append("Also worth noting:\n");
                for (Observation other : observations.subList(1, observations.size())) {
                    body.append("- ").append(other.statement()).append("\n");
                }
                body.append("\n");
            }
        }

        if (!newAchievements.isEmpty()) {
            body.append("New achievements this week:\n");
            for (AchievementTimelineResponse achievement : newAchievements) {
                body.append("- ").append(achievement.getTitle());
                if (achievement.getRepository() != null) {
                    body.append(" (").append(achievement.getRepository()).append(")");
                }
                body.append("\n");
            }
            body.append("\n");
        } else if (observations.isEmpty()) {
            // Only the needs-analysis backlog triggered this email — say so plainly.
            body.append("No achievements were generated this week.\n\n");
        }

        if (!reposNeedingAnalysis.isEmpty()) {
            body.append("Repositories that need analysis:\n");
            for (String repo : reposNeedingAnalysis) {
                body.append("- ").append(repo).append("\n");
            }
            body.append("\n");
        }

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            body.append(frontendUrl).append("\n");
        }

        return body.toString();
    }
}
