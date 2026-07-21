package com.careeros.backend.dashboard;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    @GetMapping
    public DashboardResponse getDashboard(
            @AuthenticationPrincipal OAuth2User oauthUser
    ) {

        Number githubId = oauthUser.getAttribute("id");

        User user = userService.findByGithubId(
                githubId.longValue()
        );

        return dashboardService.getDashboard(user);
    }
}