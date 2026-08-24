package com.careeros.backend.security;

import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RateLimitFilter filter =
            new RateLimitFilter(rateLimiter, new ObjectMapper(), userRepository, auditLogService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(long githubId) {
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttribute("id")).thenReturn((int) githubId);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(principal);

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static ConsumptionProbe allowed() {
        return ConsumptionProbe.consumed(1, 0);
    }

    /**
     * MockHttpServletRequest doesn't populate getServletPath() from the URI
     * the way a real servlet container does — AntPathRequestMatcher matches
     * against servletPath+pathInfo, not requestURI, so tests have to set it
     * explicitly. Production code is unaffected: DispatcherServlet is
     * mapped to "/", so a real request's servletPath is already the full path.
     */
    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        return request;
    }

    private void assertPassesThrough(String method, String uri, RateLimitTier expectedTier) throws Exception {
        authenticateAs(7L);
        when(rateLimiter.tryConsume(eq(7L), eq(expectedTier))).thenReturn(allowed());

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(method, uri), response, chain);

        assertThat(chain.getRequest()).as("request reached the rest of the chain").isNotNull();
        verify(rateLimiter).tryConsume(7L, expectedTier);
    }

    @Test
    void unauthenticatedRequestsPassThroughWithoutConsultingTheRateLimiter() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/dashboard"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void plainReadEndpointUsesTheReadsTier() throws Exception {
        assertPassesThrough("GET", "/dashboard", RateLimitTier.READS);
    }

    @Test
    void repositorySyncUsesTheSyncTier() throws Exception {
        assertPassesThrough("POST", "/api/repositories/sync", RateLimitTier.SYNC);
    }

    @Test
    void commitSyncUsesTheSyncTier() throws Exception {
        assertPassesThrough("POST", "/api/repositories/sync/commits", RateLimitTier.SYNC);
    }

    @Test
    void pullRequestSyncUsesTheSyncTier() throws Exception {
        assertPassesThrough("POST", "/api/repositories/sync/pull-requests", RateLimitTier.SYNC);
    }

    @Test
    void analyzeUsesTheAnalyzeTier() throws Exception {
        assertPassesThrough("POST", "/api/repositories/123/analyze", RateLimitTier.ANALYZE);
    }

    @Test
    void weeklySummaryUsesTheAnalyzeTier() throws Exception {
        assertPassesThrough("GET", "/achievement/weekly", RateLimitTier.ANALYZE);
    }

    @Test
    void repositoryKnowledgeGenerateUsesTheAnalyzeTier() throws Exception {
        assertPassesThrough("GET", "/knowledge/generate", RateLimitTier.ANALYZE);
    }

    @Test
    void starStoryUsesTheAnalyzeTier() throws Exception {
        assertPassesThrough("GET", "/achievement/star", RateLimitTier.ANALYZE);
    }

    @Test
    void linkedinPostGenerationUsesItsOwnCheaperTier() throws Exception {
        assertPassesThrough("POST", "/achievement/linkedin/45", RateLimitTier.LINKEDIN_POST);
    }

    @Test
    void linkedinPeriodSummaryUsesTheSameTierAsSingleAchievementGeneration() throws Exception {
        assertPassesThrough("POST", "/api/achievements/linkedin/period", RateLimitTier.LINKEDIN_POST);
    }

    @Test
    void linkedinCombinedUsesTheSameTierAsThePeriodPath() throws Exception {
        assertPassesThrough("POST", "/api/achievements/linkedin/combined", RateLimitTier.LINKEDIN_POST);
    }

    @Test
    void onboardingStartUsesTheAnalyzeTier() throws Exception {
        assertPassesThrough("POST", "/api/onboarding/start", RateLimitTier.ANALYZE);
    }

    /** GET on the same base path as the sync endpoints must not accidentally match a POST-only rule. */
    @Test
    void listingRepositoriesIsNotMistakenForASyncCall() throws Exception {
        assertPassesThrough("GET", "/api/repositories", RateLimitTier.READS);
    }

    @Test
    void rejectedRequestReturns429WithRetryAfterAndNeverReachesTheChain() throws Exception {
        authenticateAs(9L);
        when(rateLimiter.tryConsume(9L, RateLimitTier.ANALYZE))
                .thenReturn(ConsumptionProbe.rejected(0, TimeUnit.SECONDS.toNanos(42), 0));
        User user = User.builder().id(1L).githubId(9L).build();
        when(userRepository.findByGithubId(9L)).thenReturn(Optional.of(user));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", "/api/repositories/5/analyze"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        assertThat(response.getContentAsString())
                .contains("rate_limit_exceeded")
                .contains("ANALYZE");
        assertThat(chain.getRequest()).as("the controller must never see a rejected request").isNull();
        verify(auditLogService).record(user, AuditAction.RATE_LIMIT_REJECTED,
                "/api/repositories/5/analyze", AuditOutcome.FAILURE);
    }
}
