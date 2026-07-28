package com.careeros.backend.achievement.repositoryachievement;

import com.careeros.backend.github.GithubRepository;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
@Table(name = "repository_achievements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryAchievementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    @JsonIgnore
    private GithubRepository repository;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String resumeBullet;

    @Column(columnDefinition = "TEXT")
    private String starSituation;

    @Column(columnDefinition = "TEXT")
    private String starTask;

    @Column(columnDefinition = "TEXT")
    private String starAction;

    @Column(columnDefinition = "TEXT")
    private String starResult;

    private Double confidence;

    private LocalDateTime generatedAt;

}