package com.careeros.backend.achievement.linkedinrecord;

import com.careeros.backend.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class LinkedInRecordController {

    private final LinkedInPostRepository linkedInPostRepository;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<LinkedInPostResponse> list() {
        return linkedInPostRepository
                .findByUserOrderByGeneratedAtDesc(currentUserService.require())
                .stream()
                .map(LinkedInPostResponse::from)
                .toList();
    }
}