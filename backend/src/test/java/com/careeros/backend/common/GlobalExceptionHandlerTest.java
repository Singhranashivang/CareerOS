package com.careeros.backend.common;

import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real embedded server (RANDOM_PORT), not MockMvc — MockMvc
 * doesn't route uncaught exceptions through the container's actual error
 * dispatch the way a browser-facing request does, which would give a false
 * pass/fail here regardless of what GlobalExceptionHandler actually does.
 *
 * The scratch controller/security override below only exist to reach an
 * unauthenticated endpoint without fighting the app's real OAuth2 login —
 * GlobalExceptionHandler itself is exercised completely unmodified.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GlobalExceptionHandlerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    record ScratchRequest(@NotNull String requiredField) {
    }

    @RestController
    static class ScratchController {

        @PostMapping("/__scratch/boom")
        public String boom() {
            throw new RuntimeException("SENSITIVE: db password is hunter2");
        }

        @PostMapping("/__scratch/not-found")
        public String notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "widget 42 not found");
        }

        @PostMapping("/__scratch/validate")
        public String validate(@jakarta.validation.Valid @RequestBody ScratchRequest request) {
            return "ok";
        }
    }

    @TestConfiguration
    static class ScratchConfig {

        @Bean
        ScratchController scratchController() {
            return new ScratchController();
        }

        // Only to reach the scratch endpoints without a real GitHub login —
        // permits exactly the three test paths, leaves everything else to
        // the app's real SecurityConfig-defined chain.
        @Bean
        @Order(1)
        SecurityFilterChain scratchOpenChain(HttpSecurity http) throws Exception {
            http.securityMatcher("/__scratch/**")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void unexpectedExceptionReturnsGenericBodyAndLogsServerSide() {
        ResponseEntity<String> response = rest.postForEntity(url("/__scratch/boom"), null, String.class);

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        assertThat(response.getBody()).doesNotContain("SENSITIVE").doesNotContain("hunter2");
        assertThat(response.getBody()).doesNotContain("RuntimeException"); // no class name, no stack frame
    }

    @Test
    void deliberateResponseStatusExceptionKeepsItsReason() {
        ResponseEntity<String> response = rest.postForEntity(url("/__scratch/not-found"), null, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        // Developer-authored, safe, client-facing text — must survive untouched.
        assertThat(response.getBody()).contains("widget 42 not found");
    }

    @Test
    void validationFailureStillReturns400WithFieldDetailNotGenericised() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                url("/__scratch/validate"), new HttpEntity<>("{}", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        // Proves GlobalExceptionHandler didn't swallow this into the generic 500 branch.
        assertThat(response.getBody()).doesNotContain("An unexpected error occurred");
    }
}
