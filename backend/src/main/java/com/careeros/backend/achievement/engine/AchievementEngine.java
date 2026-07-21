package com.careeros.backend.achievement.engine;

import com.careeros.backend.achievement.evidence.Evidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AchievementEngine {

    private final AchievementBuilder achievementBuilder;

    public List<Achievement> generate(Evidence evidence) {
        return achievementBuilder.build(evidence);
    }
}

