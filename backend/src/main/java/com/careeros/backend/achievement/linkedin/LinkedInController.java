package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/achievement")
@RequiredArgsConstructor
public class LinkedInController {

    private final LinkedInPostService linkedInPostService;
    private final CurrentUserService currentUserService;

    @GetMapping("/linkedin")
    public LinkedInPost generateLinkedInPost() {
        User user = currentUserService.require();
        return linkedInPostService.generate(user);
    }
}