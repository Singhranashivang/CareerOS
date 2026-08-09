package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.CodeStats;
import com.careeros.backend.achievement.evidence.Evidence;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;

/**
 * Decides whether there is enough work here to describe at all.
 *
 * Once commits are filtered to the repository owner, a Hacktoberfest repo can
 * arrive with one "Add files via upload". The engine has no way to say "nothing
 * here" on its own — it is asked for exactly one achievement and will produce
 * one — so the floor has to be applied before the model is ever called.
 *
 * Clearing ANY one bar is enough. An AND rule would reject a single large
 * initial commit, which is what a genuine solo project looks like.
 */
@Component
public class EvidenceSufficiency {

    static final int MIN_COMMITS = 3;
    static final int MIN_FILES = 5;
    static final int MIN_LINES_CHANGED = 100;

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

        if (commits >= MIN_COMMITS
                || files >= MIN_FILES
                || linesChanged >= MIN_LINES_CHANGED) {
            return Optional.empty();
        }

        // Shown verbatim in the UI, so it reads as a sentence and states the
        // actual numbers rather than naming a rule the reader cannot see.
        return Optional.of(
                "%d %s, %d %s, %d lines changed — below the threshold for a distinct achievement"
                        .formatted(commits, commits == 1 ? "commit" : "commits",
                                files, files == 1 ? "file" : "files",
                                linesChanged));
    }

    private static int size(Collection<?> values) {
        return values == null ? 0 : values.size();
    }
}
