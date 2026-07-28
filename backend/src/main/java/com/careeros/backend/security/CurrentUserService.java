package com.careeros.backend.security;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Resolves the authenticated User for the current request.
 *
 * Single seam for authentication. If the app later moves from OAuth2
 * session cookies to JWT, this class changes and no controller does.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User require() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("No authenticated user");
        }

        if (!(auth.getPrincipal() instanceof OAuth2User principal)) {
            throw new AccessDeniedException("Unsupported principal type");
        }

        Number githubId = principal.getAttribute("id");
        if (githubId == null) {
            throw new AccessDeniedException("GitHub id missing from principal");
        }

        return userRepository.findByGithubId(githubId.longValue())
                .orElseThrow(() -> new AccessDeniedException("User not provisioned"));
    }
}