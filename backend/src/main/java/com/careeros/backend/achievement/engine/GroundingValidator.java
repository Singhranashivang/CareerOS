package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.generator.AchievementOutput;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Catches an achievement the model wrote from thin air rather than from the
 * repository: title and resumeBullet are rejected if they share not one
 * meaningful word with the evidence actually supplied — filenames (which
 * carry class names too, since this codebase's languages name the file after
 * the type), and commit message terms.
 *
 * A heuristic word-overlap check, not semantic understanding: it catches
 * "invented from nothing" claims, not "plausible but subtly wrong" ones. It
 * also checks the claim as a whole (title + every field together), so a
 * garbage title riding along with genuinely grounded substance elsewhere
 * still passes — see AchievementTitleSpecificityValidator for the
 * title-only check that catches that case.
 */
@Component
public class GroundingValidator {

    /**
     * Empty when grounded. Present with a human-readable reason when the
     * claim shares no meaningful token with the evidence.
     */
    public Optional<String> ungroundedReason(String title, String resumeBullet, Evidence evidence) {
        Set<String> claimTokens = new HashSet<>();
        claimTokens.addAll(tokenize(title));
        claimTokens.addAll(tokenize(resumeBullet));
        return ungroundedReason(claimTokens, evidence);
    }

    /**
     * Tokenizes every non-blank field, not just title/resumeBullet — since
     * STAR fields are now individually optional, the only grounded content
     * on a given achievement might live entirely in starAction or
     * starResult, and title+resumeBullet alone would look ungrounded even
     * though the achievement is fine.
     */
    public Optional<String> ungroundedReason(AchievementOutput output, Evidence evidence) {
        Set<String> claimTokens = new HashSet<>();
        claimTokens.addAll(tokenize(output.getTitle()));
        claimTokens.addAll(tokenize(output.getResumeBullet()));
        claimTokens.addAll(tokenize(output.getStarSituation()));
        claimTokens.addAll(tokenize(output.getStarTask()));
        claimTokens.addAll(tokenize(output.getStarAction()));
        claimTokens.addAll(tokenize(output.getStarResult()));
        return ungroundedReason(claimTokens, evidence);
    }

    private Optional<String> ungroundedReason(Set<String> claimTokens, Evidence evidence) {

        if (claimTokens.isEmpty()) {
            // Nothing to check against — the blank-field check upstream
            // already rejects this case, but don't claim "grounded" for it.
            return Optional.of("no field had any meaningful words to check");
        }

        Set<String> vocabulary = evidenceVocabulary(evidence);

        for (String token : claimTokens) {
            if (vocabulary.contains(token)) {
                return Optional.empty();
            }
        }

        return Optional.of(
                "no word in any field matches a filename or commit message term");
    }

    private static Set<String> evidenceVocabulary(Evidence evidence) {
        Set<String> vocabulary = new HashSet<>();

        if (evidence == null) {
            return vocabulary;
        }

        if (evidence.getChangedFiles() != null) {
            evidence.getChangedFiles().forEach(file -> vocabulary.addAll(tokenize(file)));
        }

        if (evidence.getFeatures() != null) {
            evidence.getFeatures().forEach(feature -> {
                if (feature.getEvidence() != null) {
                    feature.getEvidence().forEach(message -> vocabulary.addAll(tokenize(message)));
                }
            });
        }

        return vocabulary;
    }

    private static Set<String> tokenize(String text) {
        return EvidenceTokenizer.tokenize(text);
    }
}
