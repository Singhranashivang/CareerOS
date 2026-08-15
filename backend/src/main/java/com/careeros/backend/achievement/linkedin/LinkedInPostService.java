package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostEntity;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.llm.BannedVocabulary;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkedInPostService {

    private final AchievementRepository achievementRepository;
    private final LinkedInPromptBuilder linkedInPromptBuilder;
    private final LLMService llmService;
    private final LinkedInPostPersistenceService linkedInPostPersistenceService;

    private final ObjectMapper objectMapper;

    public LinkedInPost generate(User user, Long achievementId, boolean regenerate) {

        AchievementEntity achievement = achievementRepository
                .findByIdAndUser(achievementId, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Achievement not found"));

        var existing = linkedInPostPersistenceService.findByAchievement(achievement);

        if (!regenerate && existing.isPresent()) {
            return toPost(existing.get());
        }

        LinkedInPost post = generateGuarded(achievement);

        LinkedInPostEntity entity = existing.orElseGet(LinkedInPostEntity::new);
        entity.setUser(user);
        entity.setAchievement(achievement);
        entity.setHeadline(post.getHeadline());
        entity.setPost(post.getPost());
        entity.setConfidence(post.getConfidence());
        entity.setGeneratedAt(LocalDateTime.now());

        linkedInPostPersistenceService.save(entity);

        return post;
    }

    /**
     * Generates a post and checks it against the banned-word list. On a hit,
     * retries once with the offending words named explicitly. If the retry
     * still has violations, keeps whichever of the two attempts has fewer
     * and logs the remainder rather than failing the request.
     */
    private LinkedInPost generateGuarded(AchievementEntity achievement) {

        LinkedInPost first = callModel(linkedInPromptBuilder.build(achievement));
        List<String> firstViolations = violationsIn(first);

        if (firstViolations.isEmpty()) {
            return first;
        }

        LinkedInPost retry = callModel(linkedInPromptBuilder.buildRetry(achievement, firstViolations));
        List<String> retryViolations = violationsIn(retry);

        if (retryViolations.isEmpty()) {
            return retry;
        }

        boolean retryIsBetter = retryViolations.size() <= firstViolations.size();

        log.warn("LinkedIn post for achievement {} still contains banned words after retry: {}",
                achievement.getId(), retryIsBetter ? retryViolations : firstViolations);

        return retryIsBetter ? retry : first;
    }

    private LinkedInPost callModel(String prompt) {

        String response = llmService.generate(prompt);

        try {
            return objectMapper.readValue(response, LinkedInPost.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LinkedIn post", e);
        }
    }

    private static List<String> violationsIn(LinkedInPost post) {
        List<String> violations = new ArrayList<>(BannedVocabulary.violationsIn(post.getHeadline()));
        violations.addAll(BannedVocabulary.violationsIn(post.getPost()));
        return violations;
    }

    private static LinkedInPost toPost(LinkedInPostEntity entity) {
        return LinkedInPost.builder()
                .headline(entity.getHeadline())
                .post(entity.getPost())
                .confidence(entity.getConfidence())
                .build();
    }

}
