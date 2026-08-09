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

    private int commitCount;
    private int filesTouched;
    private int linesAdded;
    private int linesDeleted;

    private int testFilesTouched;
    private int newFilesCreated;

    /** Top-level source areas, e.g. "security", "api", "migration". */
    private List<String> areasTouched;

    /** Days between first and last commit. */
    private long spanDays;

    private String firstCommit;
    private String lastCommit;
}