package com.careeros.backend.achievement.evidence;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Quantitative facts about the work, extracted from commit diffs.
 *
 * Without these the LLM has only commit messages and a README, so it fills
 * the gap with adjectives — "enhanced", "leveraged", "robust". Numbers are
 * what let it write a claim that can be checked.
 */
@Getter
@Builder
public class CodeStats {

    /** Every commit in the repository. */
    private int commitCount;

    /**
     * How many commits the line figures below actually cover. Capped at 40, so
     * dividing linesAdded by commitCount understates churn on busy repositories
     * — any per-commit average must use this instead.
     */
    private int sampledCommits;

    // Authored only: generated and vendored files are excluded, see
    // GeneratedFilePaths. A lockfile is not evidence of work.
    private int filesTouched;
    private int linesAdded;
    private int linesDeleted;

    /** Files skipped as generated. Kept for the log line, not for scoring. */
    private int generatedFilesSkipped;

    private int testFilesTouched;
    private int newFilesCreated;

    /** Top-level source areas, e.g. "security", "api", "migration". */
    private List<String> areasTouched;

    /** Days between first and last commit. */
    private long spanDays;

    /**
     * Minutes between first and last commit. Everything short lives here:
     * spanDays truncates a seven-hour working session and a nine-minute upload
     * to the same 0, and those two mean opposite things.
     */
    private long spanMinutes;

    private String firstCommit;
    private String lastCommit;
}