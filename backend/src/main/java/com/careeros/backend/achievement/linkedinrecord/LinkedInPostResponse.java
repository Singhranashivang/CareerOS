package com.careeros.backend.achievement.linkedinrecord;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** DTO for {@link LinkedInPostEntity} — no entity reference (user/achievement). */
@Getter
@Builder
public class LinkedInPostResponse {

    private Long id;

    private String headline;

    private String post;

    private Double confidence;

    private LocalDateTime generatedAt;

    public static LinkedInPostResponse from(LinkedInPostEntity entity) {
        return LinkedInPostResponse.builder()
                .id(entity.getId())
                .headline(entity.getHeadline())
                .post(entity.getPost())
                .confidence(entity.getConfidence())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }
}
