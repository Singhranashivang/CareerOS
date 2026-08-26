package com.careeros.backend.suggestions;

import com.careeros.backend.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Falls through to RateLimitFilter's default READS tier — a handful of DB reads, no GitHub/LLM call. */
@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
public class SuggestionsController {

    private final SuggestionsService suggestionsService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public SuggestionsResponse suggestions() {
        return suggestionsService.suggestionsFor(currentUserService.require());
    }
}
