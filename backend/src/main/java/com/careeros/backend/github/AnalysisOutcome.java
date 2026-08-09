package com.careeros.backend.github;

/**
 * Result of the last analyze attempt. Null (no value) means never analysed —
 * a distinct third state, which is why this is not a boolean.
 */
public enum AnalysisOutcome {

    /** An achievement was produced. */
    ACHIEVEMENT,

    /** Analysed successfully; the evidence did not support a claim. */
    INSUFFICIENT,

    /** The attempt failed. analysisReason carries the failure. */
    ERROR
}
