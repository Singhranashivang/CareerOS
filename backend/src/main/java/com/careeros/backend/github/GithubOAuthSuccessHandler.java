package com.careeros.backend.github;

import com.careeros.backend.user.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GithubOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        OAuth2AuthenticationToken oauthToken =
                (OAuth2AuthenticationToken) authentication;

        OAuth2User githubUser = oauthToken.getPrincipal();

        Number githubId = githubUser.getAttribute("id");

        OAuth2AuthorizedClient client =
                authorizedClientService.loadAuthorizedClient(
                        oauthToken.getAuthorizedClientRegistrationId(),
                        oauthToken.getName()
                );

        String accessToken = client.getAccessToken().getTokenValue();

        userService.createOrUpdateGitHubUser(
                githubId.longValue(),
                githubUser.getAttribute("login"),
                githubUser.getAttribute("name"),
                githubUser.getAttribute("email"),
                githubUser.getAttribute("avatar_url"),
                accessToken
        );

        new org.springframework.security.web.savedrequest.HttpSessionRequestCache()
                .getRequest(request, response);

        response.sendRedirect("/github/repos");
    }
}