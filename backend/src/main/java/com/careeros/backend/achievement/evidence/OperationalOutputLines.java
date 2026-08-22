package com.careeros.backend.achievement.evidence;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Strips log/print calls out of a diff before it reaches the achievement
 * prompt. A diff line calling a logging or print method is the program
 * talking to its own console at runtime, not the developer describing their
 * work — left in, a model has been observed reading log.info("Synced {}
 * repositories", ...) and writing an achievement about the software syncing
 * repositories rather than about the code that was written.
 *
 * Deliberately narrow: this only removes lines that are unambiguously a
 * logging/print call, not every string literal a diff might contain (a
 * returned status string built by concatenation, an exception message,
 * etc.). Those are real code and might be genuine evidence of something —
 * removing them risks losing real signal for a heuristic gain. They're
 * covered instead by the explicit "raw source code, not prose" framing on
 * the prompt's Diffs section (see AchievementPromptBuilder).
 */
final class OperationalOutputLines {

    private static final Pattern LOG_CALL = Pattern.compile("^log\\.(info|warn|debug|error|trace)\\(");

    private OperationalOutputLines() {
    }

    /**
     * Multi-line calls only lose their first line; the remaining argument
     * lines are harmless fragments on their own, not full sentences a model
     * could paraphrase into an achievement.
     */
    static String strip(String patch) {
        if (patch == null || patch.isBlank()) {
            return patch;
        }
        return patch.lines()
                .filter(line -> !isOperationalOutputLine(line))
                .collect(Collectors.joining("\n"));
    }

    private static boolean isOperationalOutputLine(String diffLine) {
        if (diffLine.isEmpty()) {
            return false;
        }
        char marker = diffLine.charAt(0);
        String code = (marker == '+' || marker == '-' || marker == ' ')
                ? diffLine.substring(1).trim()
                : diffLine.trim();
        return LOG_CALL.matcher(code).find()
                || code.startsWith("System.out.print") || code.startsWith("System.err.print");
    }
}
