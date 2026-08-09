package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.CodeStats;
import com.careeros.backend.achievement.evidence.Evidence;
import com.careeros.backend.achievement.extractor.Feature;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether there is enough work here to describe at all.
 *
 * Three independent ways to fail, because each signal is gameable on its own:
 *
 *  - the OR bars ask whether the work is big enough in any dimension;
 *  - the padding veto catches many commits that each change nothing, which is
 *    what a contribution-graph repo looks like (118 commits, 2.3 lines each);
 *  - the templating veto catches "commit 1".."commit 20" messages, which are
 *    padding even when the line count happens to clear a bar.
 *
 * A veto overrides a bar. Commit count is the weakest signal and the easiest to
 * manufacture, so it can no longer carry a repository by itself.
 */
@Component
public class EvidenceSufficiency {

    static final int MIN_COMMITS = 3;
    static final int MIN_FILES = 5;
    static final int MIN_LINES_CHANGED = 100;

    /** The commits bar needs real churn behind it; 3 commits of 2 lines is not work. */
    static final int MIN_LINES_FOR_COMMIT_BAR = 20;

    static final int PADDING_MIN_COMMITS = 20;
    static final int PADDING_MAX_LINES_PER_COMMIT = 5;

    static final int TEMPLATED_MIN_COMMITS = 5;
    static final double TEMPLATED_MAJORITY = 0.5;

    /** A lone commit has to say something; its message is the only narrative there is. */
    static final int DESCRIPTIVE_MIN_WORDS = 4;

    /**
     * Below this, every commit belongs to one sitting — an upload, not a
     * history. Above it, someone returned to the repository, and that counts as
     * work regardless of what the messages say.
     */
    static final int ITERATION_MIN_MINUTES = 60;

    /** "commit 7", "next commit 31", "update 3", "day 12". */
    private static final Pattern TEMPLATED =
            Pattern.compile("^[a-z]+( [a-z]+){0,3} ?[0-9]+$");

    private static final Set<String> SCAFFOLDING_MESSAGES = Set.of(
            "first commit", "initial commit", "init", "initial", "commit",
            "update", "add files via upload", "create react app",
            "initial commit from create react app",
            "initialize project using create react app",
            "initial commit from create next app");

    /** Empty when the evidence is good enough; otherwise why it is not. */
    public Optional<String> shortfall(Evidence evidence) {

        if (evidence == null) {
            return Optional.of("No evidence could be collected for this repository");
        }

        CodeStats stats = evidence.getCodeStats();

        int commits = stats == null ? 0 : stats.getCommitCount();
        if (commits == 0) {
            return Optional.of("No commits authored by you — nothing to describe");
        }

        // filesTouched and changedFiles come from separate GitHub calls; either
        // can come back empty on a rate limit, so take whichever answered.
        int files = Math.max(
                stats == null ? 0 : stats.getFilesTouched(),
                size(evidence.getChangedFiles()));

        int linesChanged = stats == null
                ? 0
                : stats.getLinesAdded() + stats.getLinesDeleted();

        List<String> messages = commitMessages(evidence);

        Optional<String> veto = veto(stats, files, linesChanged, commits, messages);
        if (veto.isPresent()) {
            return veto;
        }

        boolean clearsABar =
                (commits >= MIN_COMMITS && linesChanged >= MIN_LINES_FOR_COMMIT_BAR)
                        || files >= MIN_FILES
                        || linesChanged >= MIN_LINES_CHANGED;

        if (clearsABar) {
            return Optional.empty();
        }

        return Optional.of(
                "%d %s, %d %s, %d lines changed — below the threshold for a distinct achievement"
                        .formatted(commits, commits == 1 ? "commit" : "commits",
                                files, files == 1 ? "file" : "files",
                                linesChanged));
    }

    /** Reasons to reject regardless of how well the bars are cleared. */
    private static Optional<String> veto(CodeStats stats,
                                         int files,
                                         int linesChanged,
                                         int commits,
                                         List<String> messages) {

        int sampled = stats == null ? 0 : stats.getSampledCommits();

        // Padding: many commits, almost nothing in them. files > 0 proves the
        // stats call actually answered — without it a rate limit reads as padding.
        if (sampled >= PADDING_MIN_COMMITS
                && files > 0
                && (double) linesChanged / sampled < PADDING_MAX_LINES_PER_COMMIT) {

            return Optional.of(
                    "%d commits averaging %.1f changed lines each — this is commit padding, not engineering work"
                            .formatted(commits, (double) linesChanged / sampled));
        }

        // Templated messages: "commit 1".."commit 20" is a contribution graph.
        if (messages.size() >= TEMPLATED_MIN_COMMITS) {
            long templated = messages.stream().filter(EvidenceSufficiency::isTemplated).count();
            double ratio = (double) templated / messages.size();
            if (ratio > TEMPLATED_MAJORITY) {
                return Optional.of(
                        "%d of %d commit messages are sequentially numbered — this is commit padding, not engineering work"
                                .formatted(templated, messages.size()));
            }
        }

        // Scaffolding, but only where nothing suggests anyone came back to the
        // repository. Terse messages alone judge commit hygiene rather than
        // work — one repo here has 375 hand-written lines behind the message
        // "Signup". Coming back hours later is the signal that outranks it.
        long spanMinutes = stats == null ? 0 : stats.getSpanMinutes();
        boolean noIteration = commits <= 1 || spanMinutes < ITERATION_MIN_MINUTES;

        if (noIteration
                && !messages.isEmpty()
                && messages.stream().allMatch(EvidenceSufficiency::isScaffolding)) {

            return messages.size() == 1
                    ? Optional.of(
                            "A single commit named \"%s\" — project scaffolding rather than described work"
                                    .formatted(firstLine(messages.get(0))))
                    : Optional.of(
                            "All %d commits landed within %d minutes and none describes the work (\"%s\") — a code dump rather than a history"
                                    .formatted(messages.size(), spanMinutes,
                                            firstLine(messages.get(0))));
        }

        return Optional.empty();
    }

    /** FeatureExtractor groups the raw commit messages; flatten them back out. */
    private static List<String> commitMessages(Evidence evidence) {
        if (evidence.getFeatures() == null) {
            return List.of();
        }
        return evidence.getFeatures().stream()
                .map(Feature::getEvidence)
                .filter(java.util.Objects::nonNull)
                .flatMap(Collection::stream)
                .toList();
    }

    private static boolean isTemplated(String message) {
        return TEMPLATED.matcher(firstLine(message)).matches();
    }

    private static boolean isScaffolding(String message) {
        String normalised = firstLine(message).replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();

        if (normalised.isEmpty() || SCAFFOLDING_MESSAGES.contains(normalised)) {
            return true;
        }
        return normalised.split(" ").length < DESCRIPTIVE_MIN_WORDS;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        String line = newline < 0 ? message : message.substring(0, newline);
        return line.toLowerCase(Locale.ROOT).trim();
    }

    private static int size(Collection<?> values) {
        return values == null ? 0 : values.size();
    }
}
