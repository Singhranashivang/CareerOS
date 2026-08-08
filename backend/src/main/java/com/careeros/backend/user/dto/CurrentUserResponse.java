package com.careeros.backend.user.dto;

import com.careeros.backend.user.User;

import java.time.LocalDateTime;

public record CurrentUserResponse(
        Long id,
        Long githubId,
        String username,
        String name,
        String email,
        String avatarUrl,
        LocalDateTime createdAt
) {
    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getGithubId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getCreatedAt()
        );
    }
}