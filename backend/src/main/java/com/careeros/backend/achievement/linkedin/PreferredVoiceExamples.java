package com.careeros.backend.achievement.linkedin;

import com.careeros.backend.schedule.ScheduledPost;
import com.careeros.backend.schedule.ScheduledPostRepository;
import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The user's own rewrites of generated LinkedIn posts, used as writing samples
 * once there are enough of them to be a pattern rather than a one-off.
 *
 * Only the edited half of each pair is handed to the prompt. The generated half
 * is stored (ScheduledPost.generatedBody) and is what identifies an edit, but
 * it is the model's writing, not the user's — labelling it "how this user
 * writes" would teach exactly the wrong voice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PreferredVoiceExamples {

    private final ScheduledPostRepository scheduledPostRepository;

    /**
     * Edited pairs needed before any sample is used. Below this the prompt is
     * byte-identical to one built with no samples at all — two rewrites is as
     * easily one bad generation the user fixed as it is a house style, and a
     * sample the user doesn't actually consider their voice is worse than none.
     */
    @Value("${app.linkedin.voice-examples.min-pairs:3}")
    private int minPairs;

    /** How many samples reach the prompt once minPairs is met. Newest first. */
    @Value("${app.linkedin.voice-examples.max-examples:3}")
    private int maxExamples;

    /**
     * A sample this long is a wall of text that crowds out the evidence and
     * teaches length rather than register. Skipped rather than truncated —
     * half a post ends mid-sentence and reads as a style to imitate.
     */
    @Value("${app.linkedin.voice-examples.max-sample-chars:1500}")
    private int maxSampleChars;

    /** Empty until the threshold is met — see minPairs. Newest edit first. */
    @Transactional(readOnly = true)
    public List<String> forUser(User user) {

        // Fetching max(minPairs, maxExamples) is what lets one query answer both
        // questions: a short result means we saw every pair the user has, so the
        // count is exact; a full one means the threshold is met regardless of
        // how many more exist beyond the limit.
        int limit = Math.max(Math.max(minPairs, maxExamples), 1);
        List<ScheduledPost> pairs = scheduledPostRepository.findEditedPairs(user, limit);

        // The length cap picks which samples are usable, never whether the
        // threshold is met — that question is about whether this user rewrites
        // as a habit, which an over-long rewrite still answers yes to.
        if (pairs.size() < minPairs) {
            log.debug("No preferred-voice samples for user {}: {} edited pair(s), {} needed",
                    user.getId(), pairs.size(), minPairs);
            return List.of();
        }

        List<String> samples = pairs.stream()
                .map(ScheduledPost::getBody)
                .filter(body -> body != null && body.length() <= maxSampleChars)
                .limit(Math.max(maxExamples, 0))
                .toList();

        log.info("Using {} preferred-voice sample(s) for user {} ({} edited pair(s), threshold {})",
                samples.size(), user.getId(), pairs.size(), minPairs);
        return samples;
    }
}
