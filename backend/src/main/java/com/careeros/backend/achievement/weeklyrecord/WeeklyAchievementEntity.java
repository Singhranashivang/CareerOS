package com.careeros.backend.achievement.weeklyrecord;

import com.careeros.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "weekly_achievements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyAchievementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "highlights_json", columnDefinition = "TEXT")
    private String highlightsJson;

    @Column(name = "technologies_json", columnDefinition = "TEXT")
    private String technologiesJson;

    private Double confidence;

    private LocalDateTime generatedAt;
}