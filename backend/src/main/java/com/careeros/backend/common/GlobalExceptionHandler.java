package com.careeros.backend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every exception Spring MVC resolves — every named framework exception
 * (validation failures, malformed JSON, wrong method, etc.), every
 * ResponseStatusException, and anything else uncaught — funnels through
 * handleExceptionInternal exactly once (see ResponseEntityExceptionHandler).
 * That's the only override needed: log the real exception here, server-side,
 * every time, and only replace the response body for 5xx. 4xx keeps
 * whatever Spring already built (field validation messages, a
 * ResponseStatusException's "achievement not found" reason, etc.) — those
 * are safe, developer-authored text meant for the client; only a 500's
 * message/stack trace is the kind of thing that shouldn't leave this method.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        if (statusCode.is5xxServerError()) {
            log.error("Unhandled exception on {}", request.getDescription(false), ex);

            ProblemDetail generic = ProblemDetail.forStatus(statusCode);
            generic.setDetail("An unexpected error occurred");
            return ResponseEntity.status(statusCode).headers(headers).body(generic);
        }

        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
