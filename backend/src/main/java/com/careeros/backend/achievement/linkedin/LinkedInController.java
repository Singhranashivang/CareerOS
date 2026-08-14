package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/achievement")
@RequiredArgsConstructor
public class LinkedInController {

    private final LinkedInPostService linkedInPostService;
    private final CurrentUserService currentUserService;

    @PostMapping("/linkedin/{achievementId}")
    public LinkedInPost generateLinkedInPost(
            @PathVariable Long achievementId,
            @RequestParam(defaultValue = "false") boolean regenerate) {

        User user = currentUserService.require();
        return linkedInPostService.generate(user, achievementId, regenerate);
    }
}
