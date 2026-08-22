package com.careeros.backend.digest;

public enum WeeklyDigestOutcome {

    /** Ran end to end, whether or not any achievement came out of it. */
    COMPLETED,

    /** Not attempted — no GitHub token, or it couldn't be decrypted. reason carries which. */
    SKIPPED,

    /** Threw before completing. reason carries the message. */
    ERROR
}
