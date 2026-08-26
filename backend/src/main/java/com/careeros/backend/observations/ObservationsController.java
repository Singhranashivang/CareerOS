package com.careeros.backend.observations;

import com.careeros.backend.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** DB-only reads (no GitHub/LLM calls), so this falls through to RateLimitFilter's default READS tier. */
@RestController
@RequestMapping("/api/observations")
@RequiredArgsConstructor
public class ObservationsController {

    private final ObservationsService observationsService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<Observation> observations() {
        return observationsService.observationsFor(currentUserService.require());
    }
}
