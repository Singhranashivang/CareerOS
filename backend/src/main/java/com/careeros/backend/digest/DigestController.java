package com.careeros.backend.digest;

import com.careeros.backend.achievement.timeline.AchievementTimelineResponse;
import com.careeros.backend.achievement.timeline.AchievementTimelineService;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/digest")
@RequiredArgsConstructor
public class DigestController {

    private final CurrentUserService currentUserService;
    private final WeeklyDigestRunService weeklyDigestRunService;
    private final AchievementTimelineService achievementTimelineService;

    /** DB-only reads (no GitHub/LLM calls), so this falls through to RateLimitFilter's default READS tier. */
    @GetMapping("/latest")
    public DigestResponse latest() {
        User user = currentUserService.require();

        var run = weeklyDigestRunService.findByUser(user);

        DigestSummaryResponse summary = run.map(DigestSummaryResponse::from).orElse(null);

        List<AchievementTimelineResponse> achievements = run
                .map(r -> achievementTimelineService.since(user, r.getRunAt()))
                .orElse(List.of());

        return new DigestResponse(summary, achievements);
    }
}
