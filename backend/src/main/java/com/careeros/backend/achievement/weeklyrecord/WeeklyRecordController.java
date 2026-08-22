package com.careeros.backend.achievement.weeklyrecord;

import com.careeros.backend.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/weekly")
@RequiredArgsConstructor
public class WeeklyRecordController {

    private final WeeklyAchievementRepository weeklyAchievementRepository;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<WeeklyAchievementResponse> list() {
        return weeklyAchievementRepository
                .findByUserOrderByGeneratedAtDesc(currentUserService.require())
                .stream()
                .map(WeeklyAchievementResponse::from)
                .toList();
    }
}