package com.careeros.backend.achievement.recommendation;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/achievement")
public class RepositoryRecommendationController {

    private final RepositoryRecommendationService repositoryRecommendationService;
    private final UserService userService;

    @GetMapping("/recommendations")
    public List<RepositoryRecommendation> recommendations(
            @AuthenticationPrincipal OAuth2User oauthUser
    ) {

        Number githubId = oauthUser.getAttribute("id");

        User user = userService.findByGithubId(githubId.longValue());

        return repositoryRecommendationService.recommend(user);
    }

}