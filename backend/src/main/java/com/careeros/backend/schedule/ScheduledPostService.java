package com.careeros.backend.schedule;

import com.careeros.backend.achievement.linkedinrecord.LinkedInPostEntity;
import com.careeros.backend.achievement.linkedinrecord.LinkedInPostPersistenceService;
import com.careeros.backend.achievement.record.AchievementEntity;
import com.careeros.backend.achievement.record.AchievementRepository;
import com.careeros.backend.schedule.dto.CreateScheduledPostRequest;
import com.careeros.backend.schedule.dto.ScheduledPostResponse;
import com.careeros.backend.schedule.dto.UpdateScheduledPostRequest;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledPostService {

    /** Anything already published or in flight is off limits to the editor. */
    private static final Set<PostStatus> EDITABLE =
            EnumSet.of(PostStatus.DRAFT, PostStatus.SCHEDULED);

    private final ScheduledPostRepository scheduledPostRepository;
    private final AchievementRepository achievementRepository;
    private final LinkedInPostPersistenceService linkedInPostPersistenceService;


    /**
     * Called from PostPublisher only. Must not be invoked from within this class —
     * self-invocation bypasses the Spring proxy, so readOnly would silently not apply.
     */

    @Transactional(readOnly = true)
    public List<ScheduledPostResponse> listForUser(User user) {
        return scheduledPostRepository.findByUserOrderByScheduledForDesc(user)
                .stream()
                .map(ScheduledPostResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduledPost requireOwned(User user, Long id) {
        return scheduledPostRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new AccessDeniedException("Scheduled post not found"));
    }

    @Transactional
    public ScheduledPostResponse create(User user, CreateScheduledPostRequest request) {

        AchievementEntity achievement = null;
        if (request.achievementId() != null) {
            achievement = achievementRepository
                    .findByIdAndUser(request.achievementId(), user)
                    .orElseThrow(() -> new AccessDeniedException("Achievement not found"));
        }

        String body = request.body() != null && !request.body().isBlank()
                ? request.body()
                : achievement == null ? null : achievement.getResumeBullet();

        if (body == null || body.isBlank()) {
            throw badRequest("body is required when no achievement is supplied");
        }

        PostStatus status = request.status() == null ? PostStatus.DRAFT : request.status();
        if (!EDITABLE.contains(status)) {
            throw badRequest("A post can only be created as DRAFT or SCHEDULED");
        }
        requireValidSchedule(status, request.scheduledFor());

        ScheduledPost post = ScheduledPost.builder()
                .user(user)
                .achievement(achievement)
                .platform(request.platform())
                .body(body)
                .generatedBody(generatedBodyFor(request.platform(), achievement))
                .status(status)
                .scheduledFor(request.scheduledFor())
                .userTimezone(normalizeZone(request.userTimezone()))
                .build();

        return ScheduledPostResponse.from(scheduledPostRepository.save(post));
    }

    /**
     * The generated text this post started from, snapshotted at creation so the
     * pair survives a later regenerate (which overwrites linkedin_posts.post).
     *
     * Captured here rather than trusting the client to send it: the request's
     * body is already the edited text whenever the user changed it in the
     * editor before scheduling, so comparing the two at create time is what
     * catches an edit made before the post was ever persisted. An edit made
     * afterwards, via PATCH, lands on body and leaves this snapshot alone —
     * both routes end up as the same pair.
     *
     * Null unless this is a LinkedIn post generated from an achievement; see
     * ScheduledPost.generatedBody for why null is not "unedited".
     */
    private String generatedBodyFor(PostPlatform platform, AchievementEntity achievement) {
        if (platform != PostPlatform.LINKEDIN || achievement == null) {
            return null;
        }
        return linkedInPostPersistenceService.findByAchievement(achievement)
                .map(LinkedInPostEntity::getPost)
                .orElse(null);
    }

    @Transactional
    public ScheduledPostResponse update(User user, Long id, UpdateScheduledPostRequest request) {

        ScheduledPost post = requireOwned(user, id);
        if (!EDITABLE.contains(post.getStatus())) {
            throw badRequest("Cannot edit a post that is " + post.getStatus());
        }

        if (request.body() != null) {
            if (request.body().isBlank()) {
                throw badRequest("body cannot be blank");
            }
            post.setBody(request.body());
        }
        if (request.userTimezone() != null) {
            post.setUserTimezone(normalizeZone(request.userTimezone()));
        }
        if (request.scheduledFor() != null) {
            post.setScheduledFor(request.scheduledFor());
            // Giving a draft a time is how you schedule it; there is no other verb.
            post.setStatus(PostStatus.SCHEDULED);
        }

        // Only when the caller is actually changing the time. Validating always
        // would reject a body-only edit on a post whose slot has already passed.
        if (request.scheduledFor() != null) {
            requireValidSchedule(post.getStatus(), post.getScheduledFor());
        }
        return ScheduledPostResponse.from(post);
    }

    @Transactional
    public void cancel(User user, Long id) {
        ScheduledPost post = requireOwned(user, id);
        // PUBLISHING is mid-flight: the worker holds it and will post regardless,
        // so accepting the cancel would be a lie.
        if (post.getStatus() == PostStatus.POSTED
                || post.getStatus() == PostStatus.PUBLISHING) {
            throw badRequest("Cannot cancel a post that is " + post.getStatus());
        }
        post.setStatus(PostStatus.CANCELLED);
    }

    // ---------------------------------------------------------------
    // Publisher-facing. Each runs in its own transaction so a post that
    // blows up cannot roll back its own claim or its siblings' results.
    // ---------------------------------------------------------------

    @Transactional
    public List<Long> claimDue(String workerId, int batchSize) {
        return scheduledPostRepository.claimDuePosts(workerId, batchSize);
    }

    @Transactional
    public int releaseStuck(OffsetDateTime cutoff) {
        return scheduledPostRepository.releaseStuck(cutoff, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public ScheduledPost loadForPublish(Long id) {
        return scheduledPostRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Claimed post vanished: " + id));
    }

    @Transactional
    public void markPosted(Long id, String externalPostId) {
        scheduledPostRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Claimed post vanished: " + id))
                .recordSuccess(externalPostId);
    }

    @Transactional
    public void markFailed(Long id, String reason, int maxAttempts) {
        scheduledPostRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Claimed post vanished: " + id))
                .recordFailure(reason, maxAttempts);
    }

    // ---------------------------------------------------------------

    private static void requireValidSchedule(PostStatus status, OffsetDateTime scheduledFor) {
        if (status != PostStatus.SCHEDULED) {
            return;
        }
        if (scheduledFor == null) {
            throw badRequest("scheduledFor is required for a SCHEDULED post");
        }
        if (!scheduledFor.isAfter(OffsetDateTime.now())) {
            throw badRequest("scheduledFor must be in the future");
        }
    }

    private static String normalizeZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return "UTC";
        }
        try {
            return ZoneId.of(zone).getId();
        } catch (Exception e) {
            throw badRequest("Unknown timezone: " + zone);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
