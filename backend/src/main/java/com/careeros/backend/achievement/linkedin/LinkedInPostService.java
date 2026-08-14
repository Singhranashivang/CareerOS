package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostEntity;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
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

            LinkedInPostEntity entity = existing.get();

            return LinkedInPost.builder()
                    .headline(entity.getHeadline())
                    .post(entity.getPost())
                    .confidence(entity.getConfidence())
                    .build();
        }

        String prompt =
                linkedInPromptBuilder.build(achievement);

        String response =
                llmService.generate(prompt);

        try {

            LinkedInPost post = objectMapper.readValue(
                    response,
                    LinkedInPost.class
            );

            LinkedInPostEntity entity = existing.orElseGet(LinkedInPostEntity::new);
            entity.setUser(user);
            entity.setAchievement(achievement);
            entity.setHeadline(post.getHeadline());
            entity.setPost(post.getPost());
            entity.setConfidence(post.getConfidence());
            entity.setGeneratedAt(LocalDateTime.now());

            linkedInPostPersistenceService.save(entity);

            return post;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse LinkedIn post",
                    e
            );

        }

    }

}
