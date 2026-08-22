package com.careeros.backend.security;

import com.careeros.backend.github.GithubOAuthSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final GithubOAuthSuccessHandler githubOAuthSuccessHandler;
    private final RateLimitFilter rateLimitFilter;
    private final MaxRequestBodySizeFilter maxRequestBodySizeFilter;
    private final AuditingLogoutSuccessHandler auditingLogoutSuccessHandler;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Wraps the request stream before anything else in the chain has a
                // chance to read it, so a request over the cap is cut off during
                // reading rather than after some other filter already buffered it.
                .addFilterBefore(maxRequestBodySizeFilter, CsrfFilter.class)
                // Cookie-based sessions + CORS allowCredentials(true) is exactly the
                // configuration CSRF protection exists for: any origin can point a
                // browser at an authenticated POST/PATCH/DELETE and the session
                // cookie rides along automatically. withHttpOnlyFalse() so the
                // frontend's JS can read the token and echo it back as a header —
                // GET/HEAD/OPTIONS stay exempt by Spring Security's own default
                // matcher, so this only gates state-changing requests.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Spring Security's default handler (XorCsrfTokenRequestAttributeHandler)
                        // masks the token for BREACH protection, expecting the caller to echo
                        // back the same masked value it rendered into a server-side view. There
                        // is no such view here — the frontend reads the raw value straight out
                        // of the cookie — so the default handler would reject every request. The
                        // plain handler compares against the raw cookie value instead.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // CookieCsrfTokenRepository defers generating the token until
                // something actually resolves it — normally a server-rendered view
                // reading ${_csrf.token}. A pure REST API has no such view, so
                // without this filter the XSRF-TOKEN cookie is never written and
                // the frontend has nothing to read. Forces resolution on every
                // request instead.
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // After CsrfCookieFilter, so the security context (and with it,
                // the authenticated principal RateLimitFilter reads) is already
                // restored from the session by this point in the chain.
                .addFilterAfter(rateLimitFilter, CsrfCookieFilter.class)
                // Adds to HeadersConfigurer's existing defaults (X-Content-Type-Options,
                // X-Frame-Options, Cache-Control, X-XSS-Protection, and HSTS — already
                // registered even though .headers() was never called before this) rather
                // than replacing them; each .xxx(...) call configures the one shared
                // HeadersConfigurer instance HttpSecurity applies automatically.
                .headers(headers -> headers
                        // This API renders almost no HTML of its own — Spring Security's
                        // auto-generated /login page (no custom one is configured) and the
                        // default Whitelabel error page are the only two pages this default-src
                        // 'none' actually affects. The real login flow is expected to link the
                        // frontend straight to /oauth2/authorization/github, bypassing /login
                        // entirely, so it losing its inline <style> costs nothing real.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // This app never asks for camera/microphone/geolocation from any
                        // browser context it controls — deny all three outright.
                        // permissionsPolicyHeader (not permissionsPolicy) is the overload that
                        // returns HeadersConfigurer for chaining — an API quirk, not a typo.
                        .permissionsPolicyHeader(pp -> pp.policy("camera=(), microphone=(), geolocation=()"))
                        // Already active by default whenever a request is HTTPS (HstsHeaderWriter
                        // self-gates on request.isSecure()) — made explicit rather than left as
                        // an inherited default, same as the values below already match Spring's
                        // own defaults.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/oauth2/**", "/login/**").permitAll()
                        .anyRequest().authenticated()
                )
                // API calls get 401 rather than a redirect to github.com, which
                // would surface in the browser as an opaque CORS failure.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**")
                ))
                .oauth2Login(oauth -> oauth
                        .successHandler(githubOAuthSuccessHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler(auditingLogoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Response headers are opt-in under CORS regardless of allowedHeaders (that
        // list governs the request side). Retry-After is the only custom response
        // header this API sets anywhere (RateLimitFilter) — without this, the
        // browser silently drops it and the frontend reads null.
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** See the comment where this is registered — forces the deferred CsrfToken to resolve. */
    private static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
        ) throws ServletException, IOException {
            var csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}