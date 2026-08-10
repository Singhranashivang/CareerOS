package com.careeros.backend.achievement.generator;

/**
 * The model's response either wasn't valid JSON or didn't match our schema —
 * distinct from a grounding failure (well-formed, evidence just doesn't back
 * it) so AchievementGeneratorService can retry only this case with a
 * shortened prompt before giving up and recording ERROR.
 */
class SchemaDriftException extends RuntimeException {
    SchemaDriftException(String message) {
        super(message);
    }

    SchemaDriftException(String message, Throwable cause) {
        super(message, cause);
    }
}
