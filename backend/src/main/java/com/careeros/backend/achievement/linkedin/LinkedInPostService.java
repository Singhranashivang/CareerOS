package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostEntity;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.llm.LLMService;
import com.careeros.backend.achievement.weekly.WeeklyAchievementService;
import com.careeros.backend.achievement.weekly.WeeklySummary;
import com.careeros.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LinkedInPostService {

    private final WeeklyAchievementService weeklyAchievementService;
    private final LinkedInPromptBuilder linkedInPromptBuilder;
    private final LLMService llmService;
    private final LinkedInPostPersistenceService linkedInPostPersistenceService;

    private final ObjectMapper objectMapper;

    public LinkedInPost generate(User user) {

        // Return cached LinkedIn post if it already exists
        var existing = linkedInPostPersistenceService.findByUser(user);

        if (existing.isPresent()) {

            LinkedInPostEntity entity = existing.get();

            return LinkedInPost.builder()
                    .headline(entity.getHeadline())
                    .post(entity.getPost())
                    .confidence(entity.getConfidence())
                    .build();
        }

        WeeklySummary summary =
                weeklyAchievementService.generate(user);

        String prompt =
                linkedInPromptBuilder.build(summary);

        System.out.println("\n========== LINKEDIN PROMPT ==========");
        System.out.println(prompt);
        System.out.println("=====================================\n");

        String response =
                llmService.generate(prompt);

        System.out.println("\n========== LINKEDIN RESPONSE ==========");
        System.out.println(response);
        System.out.println("=======================================\n");

        try {

            LinkedInPost post = objectMapper.readValue(
                    response,
                    LinkedInPost.class
            );

            LinkedInPostEntity entity =
                    LinkedInPostEntity.builder()
                            .user(user)
                            .headline(post.getHeadline())
                            .post(post.getPost())
                            .confidence(post.getConfidence())
                            .generatedAt(LocalDateTime.now())
                            .build();

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