package com.careeros.backend.onboarding;

import com.careeros.backend.onboarding.dto.OnboardingRunResponse;
import com.careeros.backend.onboarding.dto.OnboardingStartRequest;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import com.careeros.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private static final int DEFAULT_REPO_LIMIT = 5;
    private static final int MAX_REPO_LIMIT = 20;

    private final CurrentUserService currentUserService;
    private final OnboardingRunService onboardingRunService;
    private final OnboardingService onboardingService;
    private final UserService userService;

    /**
     * Chains repository sync, commit sync, PR sync, and analyzing the top N
     * repos by evidence into one background run. Returns immediately with a
     * run id — poll GET /api/onboarding/{id} for progress rather than waiting
     * on this request, since analyzing alone can take minutes.
     *
     * If the caller already has a run in progress, that run is returned
     * as-is instead of starting a second one.
     */
    @PostMapping("/start")
    public ResponseEntity<OnboardingRunResponse> start(
            @RequestBody(required = false) OnboardingStartRequest request) {

        User user = currentUserService.require();
        int repoLimit = clamp(request == null ? null : request.repoLimit());

        // Asked once — see UserService.setGoalIfUnset for why this is safe
        // to call unconditionally on every start.
        user = userService.setGoalIfUnset(user, request == null ? null : request.goal());

        var started = onboardingRunService.startOrJoin(user);
        if (started.created()) {
            onboardingService.run(started.run().getId(), user.getId(), repoLimit);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(OnboardingRunResponse.from(started.run()));
    }

    @GetMapping("/{id}")
    public OnboardingRunResponse status(@PathVariable Long id) {
        User user = currentUserService.require();
        return OnboardingRunResponse.from(onboardingRunService.requireOwned(user, id));
    }

    private static int clamp(Integer requested) {
        if (requested == null) {
            return DEFAULT_REPO_LIMIT;
        }
        return Math.max(1, Math.min(requested, MAX_REPO_LIMIT));
    }
}
