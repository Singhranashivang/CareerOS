package com.careeros.backend.profile;

import java.time.LocalDateTime;
import java.util.List;

/** dateRangeStart/End are null when the user has no (non-dismissed) achievements yet. */
public record ProfileTotalsResponse(
        int achievementCount,
        int repositoriesContributedTo,
        LocalDateTime dateRangeStart,
        LocalDateTime dateRangeEnd,
        List<String> technologies
) {
}
