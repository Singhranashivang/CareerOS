package com.careeros.backend.dashboard;

import com.careeros.backend.achievement.knowledge.RepositoryKnowledgeService;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostRepository;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostResponse;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementRepository;
import com.careeros.backend.achievement.weeklyrecord.WeeklyAchievementResponse;
import com.careeros.backend.github.GithubRepositoryRepository;
import com.careeros.backend.repositoryknowledge.RepositoryKnowledgePersistenceService;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Reproduces the exact failure mode of the three GET /dashboard breakages:
 * each real Spring MVC response is built inside a @Transactional service
 * method, then serialized by Jackson AFTER that method returns and the
 * Hibernate session has closed. A test that builds the response and
 * serializes it in the SAME transaction would not have caught any of the
 * three — none of these test methods are @Transactional, so each service
 * call below closes its own session before ObjectMapper ever sees the
 * result, same as the real request path.
 *
 * ControllerEntityLeakTest catches this class of bug independent of data;
 * this one proves the current, real dashboard response — built from actual
 * rows in the dev DB — serializes cleanly.
 */
@SpringBootTest
class DashboardResponseSerializationTest {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WeeklyAchievementRepository weeklyAchievementRepository;
    @Autowired
    private LinkedInPostRepository linkedInPostRepository;
    @Autowired
    private RepositoryKnowledgePersistenceService repositoryKnowledgePersistenceService;
    @Autowired
    private RepositoryKnowledgeService repositoryKnowledgeService;
    @Autowired
    private GithubRepositoryRepository githubRepositoryRepository;

    @Test
    void dashboardResponseSerializesOutsideATransaction() {
        User user = requireUser();

        DashboardResponse response = dashboardService.getDashboard(user);

        assertThatCode(() -> objectMapper.writeValueAsString(response))
                .doesNotThrowAnyException();
    }

    @Test
    void weeklyAchievementListSerializesOutsideATransaction() {
        User user = requireUser();

        var list = weeklyAchievementRepository.findByUserOrderByGeneratedAtDesc(user)
                .stream().map(WeeklyAchievementResponse::from).toList();

        assertThatCode(() -> objectMapper.writeValueAsString(list))
                .doesNotThrowAnyException();
    }

    @Test
    void linkedInPostListSerializesOutsideATransaction() {
        User user = requireUser();

        var list = linkedInPostRepository.findByUserOrderByGeneratedAtDesc(user)
                .stream().map(LinkedInPostResponse::from).toList();

        assertThatCode(() -> objectMapper.writeValueAsString(list))
                .doesNotThrowAnyException();
    }

    @Test
    void repositoryKnowledgeSerializesOutsideATransaction() {
        User user = requireUser();

        // Not every synced repository has been analysed for knowledge yet —
        // pick one that actually has a row rather than assuming the first.
        var repositoryWithKnowledge = githubRepositoryRepository.findByUser(user).stream()
                .map(repository -> repositoryKnowledgePersistenceService.findByRepository(repository)
                        .map(entity -> Map.entry(repository, entity)))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow();

        var knowledge = repositoryKnowledgeService.toKnowledge(
                repositoryWithKnowledge.getKey(), repositoryWithKnowledge.getValue());

        assertThatCode(() -> objectMapper.writeValueAsString(knowledge))
                .doesNotThrowAnyException();
    }

    private User requireUser() {
        return userRepository.findByUsername("Singhranashivang").orElseThrow();
    }
}
