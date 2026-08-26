package com.careeros.backend.user;

import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.dto.CurrentUserResponse;
import com.careeros.backend.user.dto.UpdateGoalRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserService currentUserService;
    private final UserService userService;

    @GetMapping
    public CurrentUserResponse me() {
        return CurrentUserResponse.from(currentUserService.require());
    }

    /** Settings — the goal asked once during onboarding is changeable here at any time. */
    @PatchMapping("/goal")
    public CurrentUserResponse updateGoal(@Valid @RequestBody UpdateGoalRequest request) {
        User user = userService.updateGoal(currentUserService.require(), request.goal());
        return CurrentUserResponse.from(user);
    }
}
