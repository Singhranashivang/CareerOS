package com.careeros.backend.achievement.evidence;

import com.careeros.backend.achievement.extractor.Feature;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evidence {

    private String repositoryName;

    private String description;

    private String language;

    private String readme;

    private CodeStats codeStats;

    // Defaulted so a partially built Evidence cannot NPE a prompt builder.
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();

    @Builder.Default
    private List<Feature> features = new ArrayList<>();

    @Builder.Default
    private List<String> pullRequestTitles = new ArrayList<>();

    @Builder.Default
    private List<String> technologies = new ArrayList<>();

    @Builder.Default
    private List<String> repositoryTree = new ArrayList<>();

    @Builder.Default
    private List<String> changedFiles = new ArrayList<>();

    @Builder.Default
    private List<String> repositoryFeatures = new ArrayList<>();

    @Builder.Default
    private List<String> changedFileInsights = new ArrayList<>();

    // Cluster-scoped fields, populated only by EvidenceBuilder.buildForCluster —
    // null/empty for the repository-scoped build() path used by weekly/star/knowledge.

    /** The cluster's own PR, if any commit in it belongs to one — the author's own description, highest signal. */
    private String pullRequestTitle;

    private String pullRequestBody;

    @Builder.Default
    private List<String> deletedFiles = new ArrayList<>();

    @Builder.Default
    private List<String> addedTestFiles = new ArrayList<>();

    /** Unified diffs, one entry per changed file: "filename:\n<patch>". */
    @Builder.Default
    private List<String> diffs = new ArrayList<>();

}