package com.careeros.backend.achievement.engine;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class Achievement {

    private String title;

    private String summary;

    private AchievementType type;

    private String repository;

    private List<String> technologies;

    private List<String> evidence;

    private double confidence;
}