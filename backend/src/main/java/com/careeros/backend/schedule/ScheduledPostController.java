package com.careeros.backend.schedule;

import com.careeros.backend.schedule.dto.CreateScheduledPostRequest;
import com.careeros.backend.schedule.dto.ScheduledPostResponse;
import com.careeros.backend.schedule.dto.UpdateScheduledPostRequest;
import com.careeros.backend.security.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scheduled-posts")
@RequiredArgsConstructor
public class ScheduledPostController {

    private final ScheduledPostService scheduledPostService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<ScheduledPostResponse> list() {
        return scheduledPostService.listForUser(currentUserService.require());
    }

    @PostMapping
    public ScheduledPostResponse create(@Valid @RequestBody CreateScheduledPostRequest request) {
        return scheduledPostService.create(currentUserService.require(), request);
    }

    @PatchMapping("/{id}")
    public ScheduledPostResponse update(@PathVariable Long id,
                                        @RequestBody UpdateScheduledPostRequest request) {
        return scheduledPostService.update(currentUserService.require(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        scheduledPostService.cancel(currentUserService.require(), id);
        return ResponseEntity.noContent().build();
    }
}
