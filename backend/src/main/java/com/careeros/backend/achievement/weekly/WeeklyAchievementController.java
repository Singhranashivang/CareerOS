package com.careeros.backend.achievement.weekly;

import com.careeros.backend.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/achievement")
@RequiredArgsConstructor
public class WeeklyAchievementController {

    private final WeeklyAchievementService weeklyAchievementService;
    private final CurrentUserService currentUserService;

    @GetMapping("/weekly")
    public WeeklySummary generateWeeklySummary() {
        return weeklyAchievementService.generate(currentUserService.require());
    }
}
