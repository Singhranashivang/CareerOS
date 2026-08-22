package com.careeros.backend.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false, unique = true)
    private Long githubId;

    @Column(nullable = false)
    private String username;

    @Column
    private String name;

    @Column
    private String email;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** IANA zone id (e.g. "America/New_York"). Defaults to UTC — no settings UI to change it yet. */
    @Column(nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Encrypted at rest — see GithubTokenEncryptor. Null means the user needs to reconnect GitHub. */
    @Column(name = "github_access_token")
    @JsonIgnore
    private String githubAccessToken;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
