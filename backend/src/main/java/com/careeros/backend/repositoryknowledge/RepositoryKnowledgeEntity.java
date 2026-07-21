package com.careeros.backend.repositoryknowledge;

import com.careeros.backend.github.GithubRepository;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "repository_knowledge")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryKnowledgeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    @JsonIgnore
    private GithubRepository repository;
    private String projectType;

    private String domain;

    @Column(columnDefinition = "TEXT")
    private String technologiesJson;

    @Column(columnDefinition = "TEXT")
    private String architectureJson;

    @Column(columnDefinition = "TEXT")
    private String featuresJson;

    @Column(columnDefinition = "TEXT")
    private String developerContributionsJson;

    private Double confidence;

    private LocalDateTime generatedAt;

}