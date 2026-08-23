package com.careeros.backend.security;

import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Per-user request budgets, enforced before the request reaches a
 * controller. Runs after Spring Security has restored the session's
 * Authentication (see where this is wired into the filter chain in
 * SecurityConfig) so the GitHub id is already available without a DB call.
 *
 * Unauthenticated requests pass straight through — authorizeHttpRequests /
 * exceptionHandling already turns those into a 401, and rate limiting an
 * anonymous caller would need a different key (IP) and a different threat
 * model than "one user looping their own account".
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Checked in order, most specific first. Anything authenticated that
     * doesn't match one of these falls through to READS — new endpoints are
     * safe-by-default cheap, not safe-by-default expensive, so a heavy one
     * added later must be added here explicitly or it silently gets the
     * loose tier.
     */
    private static final List<RateLimitRule> RULES = List.of(
            rule(HttpMethod.POST, "/api/repositories/sync", RateLimitTier.SYNC),
            rule(HttpMethod.POST, "/api/repositories/sync/commits", RateLimitTier.SYNC),
            rule(HttpMethod.POST, "/api/repositories/sync/pull-requests", RateLimitTier.SYNC),
            rule(HttpMethod.POST, "/api/repositories/*/analyze", RateLimitTier.ANALYZE),
            rule(HttpMethod.GET, "/achievement/weekly", RateLimitTier.ANALYZE),
            rule(HttpMethod.GET, "/knowledge/generate", RateLimitTier.ANALYZE),
            rule(HttpMethod.GET, "/achievement/star", RateLimitTier.ANALYZE),
            rule(HttpMethod.POST, "/achievement/linkedin/*", RateLimitTier.LINKEDIN_POST),
            rule(HttpMethod.POST, "/api/achievements/linkedin/period", RateLimitTier.LINKEDIN_POST),
            rule(HttpMethod.POST, "/api/onboarding/start", RateLimitTier.ANALYZE)
    );

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {

        Long githubId = authenticatedGithubId();
        if (githubId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitTier tier = classify(request);
        ConsumptionProbe probe = rateLimiter.tryConsume(githubId, tier);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, Math.ceilDiv(probe.getNanosToWaitForRefill(), 1_000_000_000L));

        // Only resolved here, on the rejection path — every allowed request stays
        // free of this DB lookup, same reasoning as using the raw githubId as the
        // rate-limit key above instead of the full User.
        userRepository.findByGithubId(githubId).ifPresent(user ->
                auditLogService.record(user, AuditAction.RATE_LIMIT_REJECTED,
                        request.getRequestURI(), AuditOutcome.FAILURE));

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "rate_limit_exceeded",
                "tier", tier.name(),
                "retryAfterSeconds", retryAfterSeconds
        )));
    }

    private static Long authenticatedGithubId() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof OAuth2User principal)) {
            return null;
        }

        Number githubId = principal.getAttribute("id");
        return githubId == null ? null : githubId.longValue();
    }

    private static RateLimitTier classify(HttpServletRequest request) {
        for (RateLimitRule rule : RULES) {
            if (rule.matches(request)) {
                return rule.tier();
            }
        }
        return RateLimitTier.READS;
    }

    private static RateLimitRule rule(HttpMethod method, String pattern, RateLimitTier tier) {
        return new RateLimitRule(new AntPathRequestMatcher(pattern, method.name()), tier);
    }

    private record RateLimitRule(AntPathRequestMatcher matcher, RateLimitTier tier) {
        boolean matches(HttpServletRequest request) {
            return matcher.matches(request);
        }
    }
}
