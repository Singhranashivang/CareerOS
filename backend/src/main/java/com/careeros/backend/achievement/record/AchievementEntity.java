package com.careeros.backend.achievement.record;

import com.careeros.backend.achievement.engine.AchievementType;
import com.careeros.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "achievements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    private String repository;

    @Enumerated(EnumType.STRING)
    private AchievementType type;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(columnDefinition = "TEXT")
    private String technologiesJson;

    private double confidence;

    private LocalDateTime generatedAt;
}