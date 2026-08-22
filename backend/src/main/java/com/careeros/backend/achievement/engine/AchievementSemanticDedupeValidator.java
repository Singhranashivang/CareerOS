package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.record.AchievementEntity;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Catches what commit-set dedup can't: two clusters, months or weeks apart,
 * both landing on the same subject because they touch the same subsystem —
 * "AI-Powered Achievement Generation" and "Enhanced Achievement Generation
 * in Repository Knowledge Management" are near-paraphrases of each other,
 * but their commit sets share zero SHAs, so exact dedup never sees them as
 * related. This compares title/resumeBullet text against every existing
 * achievement for the repository instead.
 *
 * Overlap coefficient (intersection / smaller set's size) rather than
 * Jaccard: titles here vary a lot in length ("AI-Powered Achievement
 * Generation" vs "Enhanced Achievement Generation in Repository Knowledge
 * Management"), and Jaccard punishes that length gap even when the shorter
 * title's whole content is contained in the longer one. Threshold is a
 * tuned heuristic, like every other similarity check in this package —
 * revisit if it proves too loose or too strict against real data.
 */
@Component
public class AchievementSemanticDedupeValidator {

    private static final double OVERLAP_THRESHOLD = 0.4;

    private static final int MIN_TOKEN_LENGTH = 4;

    private static final Set<String> STOPWORDS = Set.of(
            "this", "that", "with", "from", "into", "have", "been", "were",
            "will", "your", "which", "when", "while", "using", "used", "user",
            "code", "file", "files", "work", "week", "repository", "project",
            "added", "adding", "made", "make", "built", "build", "create",
            "created", "creating", "update", "updated", "updating", "change",
            "changed", "changes", "implement", "implemented", "improve",
            "improved", "developed", "development", "system", "feature",
            "features", "based"
    );

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    /** Empty when no existing achievement is a substantial text match. */
    public Optional<AchievementEntity> duplicateOf(
            String title, String resumeBullet, List<AchievementEntity> existingForRepository) {

        Set<String> newTitle = tokenize(title);
        Set<String> newBullet = tokenize(resumeBullet);

        for (AchievementEntity existing : existingForRepository) {
            if (overlapCoefficient(newTitle, tokenize(existing.getTitle())) >= OVERLAP_THRESHOLD
                    || overlapCoefficient(newBullet, tokenize(existing.getResumeBullet())) >= OVERLAP_THRESHOLD) {
                return Optional.of(existing);
            }
        }

        return Optional.empty();
    }

    private static double overlapCoefficient(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / Math.min(a.size(), b.size());
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String spaced = CAMEL_BOUNDARY.matcher(text).replaceAll("$1 $2");
        Set<String> tokens = new HashSet<>();
        for (String token : NON_WORD.split(spaced.toLowerCase(Locale.ROOT))) {
            if (token.length() >= MIN_TOKEN_LENGTH && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
