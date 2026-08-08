package com.careeros.backend.user;

import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.dto.CurrentUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserService currentUserService;

    @GetMapping
    public CurrentUserResponse me() {
        return CurrentUserResponse.from(currentUserService.require());
    }
}