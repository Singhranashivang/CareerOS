package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Catches an achievement the model wrote from thin air rather than from the
 * repository: title and resumeBullet are rejected if they share not one
 * meaningful word with the evidence actually supplied — filenames (which
 * carry class names too, since this codebase's languages name the file after
 * the type), and commit message terms.
 *
 * A heuristic word-overlap check, not semantic understanding: it catches
 * "invented from nothing" claims, not "plausible but subtly wrong" ones.
 */
@Component
public class GroundingValidator {

    /** Below this length a token is usually noise ("cpp", "the", "src"), not a claim-worthy term. */
    private static final int MIN_TOKEN_LENGTH = 4;

    private static final Set<String> STOPWORDS = Set.of(
            "this", "that", "with", "from", "into", "have", "been", "were",
            "will", "your", "which", "when", "while", "using", "used", "user",
            "code", "file", "files", "work", "week", "repository", "project",
            "added", "adding", "made", "make", "built", "build", "create",
            "created", "creating", "update", "updated", "updating", "change",
            "changed", "changes", "implement", "implemented", "improve",
            "improved", "developed", "development"
    );

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    /**
     * Empty when grounded. Present with a human-readable reason when the
     * claim shares no meaningful token with the evidence.
     */
    public Optional<String> ungroundedReason(String title, String resumeBullet, Evidence evidence) {

        Set<String> claimTokens = new HashSet<>();
        claimTokens.addAll(tokenize(title));
        claimTokens.addAll(tokenize(resumeBullet));

        if (claimTokens.isEmpty()) {
            // Nothing to check against — the blank-field check upstream
            // already rejects this case, but don't claim "grounded" for it.
            return Optional.of("title/resumeBullet had no meaningful words to check");
        }

        Set<String> vocabulary = evidenceVocabulary(evidence);

        for (String token : claimTokens) {
            if (vocabulary.contains(token)) {
                return Optional.empty();
            }
        }

        return Optional.of(
                "no word in the title/resumeBullet matches a filename or commit message term");
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
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        // "SpiralSearch.cpp" -> "Spiral Search.cpp" -> spiral / search / cpp
        String spaced = CAMEL_BOUNDARY.matcher(text).replaceAll("$1 $2");
        Set<String> tokens = new HashSet<>();
        for (String token : NON_WORD.split(spaced.toLowerCase())) {
            if (token.length() >= MIN_TOKEN_LENGTH && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
