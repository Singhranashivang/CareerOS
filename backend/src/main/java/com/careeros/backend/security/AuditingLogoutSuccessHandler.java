package com.careeros.backend.security;

import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * LogoutFilter invokes the LogoutSuccessHandler after SecurityContextLogoutHandler
 * has already cleared SecurityContextHolder, so CurrentUserService can't resolve
 * the user here — but the Authentication that was active is still passed in as a
 * parameter, which is enough to look the user up directly.
 */
@Component
@RequiredArgsConstructor
public class AuditingLogoutSuccessHandler implements LogoutSuccessHandler {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication auth) {

        if (auth != null && auth.getPrincipal() instanceof OAuth2User principal) {
            Number githubId = principal.getAttribute("id");
            if (githubId != null) {
                userRepository.findByGithubId(githubId.longValue())
                        .ifPresent(user -> auditLogService.record(
                                user, AuditAction.SIGN_OUT, null, AuditOutcome.SUCCESS));
            }
        }

        response.setStatus(HttpStatus.NO_CONTENT.value());
    }
}
