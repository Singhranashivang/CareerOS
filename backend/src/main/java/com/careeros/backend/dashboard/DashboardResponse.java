package com.careeros.backend.dashboard;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostEntity;
import com.careeros.backend.achievement.recommendation.RepositoryRecommendation;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private RepositoryRecommendation recommendedRepository;

    private WeeklyAchievementEntity weeklySummary;

    private List<AchievementEntity> achievements;

    private LinkedInPostEntity linkedInPost;

    private DashboardStats stats;

}