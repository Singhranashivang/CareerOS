package com.careeros.backend.achievement.recommendation;

import com.careeros.backend.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/achievement")
public class RepositoryRecommendationController {

    private final RepositoryRecommendationService repositoryRecommendationService;
    private final CurrentUserService currentUserService;

    @GetMapping("/recommendations")
    public List<RepositoryRecommendation> recommendations() {
        return repositoryRecommendationService.recommend(currentUserService.require());
    }
}
