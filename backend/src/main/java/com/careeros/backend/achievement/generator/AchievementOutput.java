package com.careeros.backend.achievement.generator;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementOutput {

    private String title;

    private String resumeBullet;

    private String starSituation;

    private String starTask;

    private String starAction;

    private String starResult;

    private double confidence;

    /** Set when the evidence did not support a grounded claim. Not an error. */
    private boolean insufficient;

    private String reason;
}