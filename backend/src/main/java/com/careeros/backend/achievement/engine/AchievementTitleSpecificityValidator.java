package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Catches a gamification-badge title riding along with a genuinely grounded
 * achievement — "GitHub Committer" for real work syncing commit history
 * across repositories. GroundingValidator didn't catch it: it checks title +
 * every field together, and passes on one shared word anywhere in that set,
 * so a real title next to a garbage one never gets checked on its own.
 *
 * Deliberately a narrower vocabulary than GroundingValidator's, not the same
 * one reused: built only from changed-file names and named technologies, not
 * commit-message prose. Prose is where category words leak in — a commit
 * message like "Sync GitHub repos" puts "github" in GroundingValidator's
 * vocabulary even though it names no file, class, or method. A title is
 * required to name one of those, not just share a word with a sentence
 * describing them.
 *
 * Heuristic, not exhaustive: a repository whose own filenames share a prefix
 * with a generic word (this codebase's Github*.java files, for instance)
 * can still let a single-word title using just that prefix through, since
 * the rule only requires one matching token, not two from the same file.
 * Accepted deliberately — the stricter two-token version risked rejecting
 * good, descriptive titles ("Refactored Authentication Routes") that
 * paraphrase rather than quote an identifier verbatim.
 *
 * Matching is exact token equality, not substring. A one-direction substring
 * variant was tried (title token contains evidence token, or vice versa) to
 * catch "authentication" against a file naming "OAuth" — reverted, because
 * it didn't even fix that case ("authentication" is longer than "oauth" and
 * neither literally contains the other) while introducing a worse one:
 * "Committer" literally contains "commit", a common token in any repo about
 * processing git commits, so a real gamification title started passing
 * again. See AchievementTitleSpecificityValidatorTest's
 * aTitleWordThatWouldFalselyPassUnderSubstringMatching. The accepted
 * consequence of exact matching is that a real, related title can still be
 * rejected when it paraphrases rather than quotes an identifier — occasional
 * false positive here, retried once, beats a gamification title sliding
 * through.
 */
@Component
public class AchievementTitleSpecificityValidator {

    /**
     * Empty when the title names something specific. Present with a
     * human-readable reason when it doesn't.
     */
    public Optional<String> reasonTitleLacksSpecificity(String title, Evidence evidence) {

        Set<String> titleTokens = EvidenceTokenizer.tokenize(title);
        if (titleTokens.isEmpty()) {
            return Optional.of("the title has no meaningful words to check");
        }

        Set<String> vocabulary = specificVocabulary(evidence);

        for (String token : titleTokens) {
            if (vocabulary.contains(token)) {
                return Optional.empty();
            }
        }

        return Optional.of(
                "the title names no file, class, method, or technology from the evidence — "
                        + "it reads as a role or category, not a description of what was built");
    }

    private static Set<String> specificVocabulary(Evidence evidence) {
        Set<String> vocabulary = new HashSet<>();

        if (evidence == null) {
            return vocabulary;
        }

        if (evidence.getChangedFiles() != null) {
            evidence.getChangedFiles().forEach(file -> vocabulary.addAll(EvidenceTokenizer.tokenize(file)));
        }

        if (evidence.getTechnologies() != null) {
            evidence.getTechnologies().forEach(tech -> vocabulary.addAll(EvidenceTokenizer.tokenize(tech)));
        }

        return vocabulary;
    }
}
