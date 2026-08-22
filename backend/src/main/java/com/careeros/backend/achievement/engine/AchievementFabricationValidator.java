package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.generator.AchievementOutput;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Catches the two most common ways generated text invents rather than
 * reports: claiming the achievement was "our" work (the evidence is one
 * author's git history — it has no team to speak for), and naming a
 * technology the evidence never mentions.
 *
 * The technology list is closed and curated, not exhaustive — this checks
 * "does the text name one of the usual hallucinated suspects that isn't in
 * the evidence", not "does the text contain any noun that could be a
 * technology". Extend the list as new hallucinations are observed.
 */
@Component
public class AchievementFabricationValidator {

    private static final Pattern FIRST_PERSON_PLURAL =
            Pattern.compile("\\b(we|us|our|ours|ourselves)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Matched as whole words against the output text; compared against
     * evidence.getTechnologies() case-insensitively. Framework/library/
     * platform names, not generic terms (e.g. not "database" or "API" —
     * those aren't a fabricated claim on their own).
     */
    private static final List<String> KNOWN_TECHNOLOGIES = List.of(
            "kubernetes", "docker", "aws", "azure", "gcp", "terraform", "helm", "jenkins",
            "react", "angular", "vue", "next.js", "svelte", "redux",
            "spring", "django", "flask", "express", "rails", "laravel", ".net",
            "redis", "kafka", "rabbitmq", "mongodb", "postgresql", "mysql", "elasticsearch", "graphql",
            "typescript", "javascript", "python", "golang", "rust", "kotlin", "swift",
            "oauth", "jwt", "grpc", "websocket", "nginx", "firebase", "supabase", "stripe",
            "tensorflow", "pytorch", "microservices"
    );

    /** Empty when clean. Present with a human-readable reason otherwise. */
    public Optional<String> fabricationReason(AchievementOutput output, Evidence evidence) {

        String text = combinedText(output);

        if (FIRST_PERSON_PLURAL.matcher(text).find()) {
            return Optional.of("uses a first-person plural pronoun (\"we\"/\"our\"/\"us\") — "
                    + "the evidence is one author's commit history, not a team's account of itself");
        }

        Set<String> evidenceTech = evidenceVocabulary(evidence);
        String lower = text.toLowerCase(Locale.ROOT);

        for (String tech : KNOWN_TECHNOLOGIES) {
            if (containsWholeWord(lower, tech) && !evidenceTech.contains(tech)) {
                return Optional.of("names \"" + tech + "\", which does not appear in the "
                        + "dependency files, imports, or Technologies list");
            }
        }

        return Optional.empty();
    }

    private static boolean containsWholeWord(String haystack, String needle) {
        return Pattern.compile("\\b" + Pattern.quote(needle) + "\\b").matcher(haystack).find();
    }

    private static Set<String> evidenceVocabulary(Evidence evidence) {
        if (evidence == null || evidence.getTechnologies() == null) {
            return Set.of();
        }
        return evidence.getTechnologies().stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static String combinedText(AchievementOutput output) {
        return String.join(" ",
                nullToEmpty(output.getTitle()),
                nullToEmpty(output.getResumeBullet()),
                nullToEmpty(output.getStarSituation()),
                nullToEmpty(output.getStarTask()),
                nullToEmpty(output.getStarAction()),
                nullToEmpty(output.getStarResult()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
