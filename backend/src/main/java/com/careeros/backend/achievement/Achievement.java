package com.careeros.backend.achievement;

import com.careeros.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "achievements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    private String title;


    @Column(columnDefinition = "TEXT")
    private String description;


    @Column(columnDefinition = "TEXT")
    private String evidence;


    private Double confidence;


    private String status;


    @Column(name = "created_at")
    private LocalDateTime createdAt;
}