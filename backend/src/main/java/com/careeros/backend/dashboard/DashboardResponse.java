package com.careeros.backend.dashboard;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostResponse;
import com.careeros.backend.achievement.recommendation.RecommendedRepositoryDto;
import com.careeros.backend.achievement.timeline.AchievementTimelineResponse;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Every field here must be a DTO or a primitive — no entity, no collection
 * of entities, no type that transitively holds one. This response is built
 * inside a @Transactional service method and serialized by Spring MVC after
 * that transaction (and its Hibernate session) has closed; an entity field
 * reachable from here fails with LazyInitializationException the first time
 * a lazy relation on it gets touched, which is exactly what happened with
 * GithubRepository.user (via RepositoryRecommendation) and then again with
 * AchievementEntity's HibernateProxy. See DashboardResponseSerializationTest.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private RecommendedRepositoryDto recommendedRepository;

    private WeeklyAchievementResponse weeklySummary;

    private List<AchievementTimelineResponse> achievements;

    private LinkedInPostResponse linkedInPost;

    private DashboardStats stats;

}
