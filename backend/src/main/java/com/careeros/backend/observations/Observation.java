package com.careeros.backend.observations;

import java.util.List;

/**
 * A single judgement about the user's history — a sentence, not a count. See
 * ObservationsService for how these are ordered ("strongest" first) and
 * WeeklyEmailContentBuilder for how one becomes an email subject line.
 */
public record Observation(
        ObservationType type,
        String statement,
        List<String> evidence,
        String suggestedAction
) {
}
