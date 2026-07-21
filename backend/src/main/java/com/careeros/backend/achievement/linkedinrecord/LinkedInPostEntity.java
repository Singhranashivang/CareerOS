package com.careeros.backend.achievement.linkedinrecord;

import com.careeros.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "linkedin_posts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedInPostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String headline;

    @Column(columnDefinition = "TEXT")
    private String post;

    private Double confidence;

    private LocalDateTime generatedAt;
}