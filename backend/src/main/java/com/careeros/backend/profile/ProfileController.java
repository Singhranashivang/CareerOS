package com.careeros.backend.profile;

import com.careeros.backend.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUserService currentUserService;

    /** DB-only read; falls through to RateLimitFilter's default READS tier. */
    @GetMapping
    public ProfileResponse profile() {
        return profileService.getProfile(currentUserService.require());
    }

    /** DB-only read; falls through to RateLimitFilter's default READS tier. */
    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam String format) {
        if (!"markdown".equalsIgnoreCase(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported format: " + format + " (only \"markdown\" is supported)");
        }

        String markdown = profileService.exportMarkdown(currentUserService.require());

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/markdown"))
                .body(markdown);
    }
}
