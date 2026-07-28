package com.careeros.backend.achievement.star;

import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/achievement")
@RequiredArgsConstructor
public class StarStoryController {

    private final StarStoryService starStoryService;
    private final CurrentUserService currentUserService;

    @GetMapping("/star")
    public StarStory generate() {
        User user = currentUserService.require();
        return starStoryService.generate(user);
    }
}