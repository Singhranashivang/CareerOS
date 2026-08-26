package com.careeros.backend.schedule;

import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledPostRepository extends JpaRepository<ScheduledPost, Long> {

    List<ScheduledPost> findByUserOrderByScheduledForDesc(User user);

    Optional<ScheduledPost> findByIdAndUser(Long id, User user);

    /** Backs GET /api/suggestions — which achievements/repositories already have something actually published. */
    List<ScheduledPost> findByUserAndStatus(User user, PostStatus status);

    /**
     * The user's edited pairs, newest first — every LinkedIn post where they
     * changed the generated text before scheduling it. This query is the one
     * definition of "an edited pair": a snapshot exists, and the body differs
     * from it once surrounding whitespace is ignored (retyping the same text
     * with a stray newline is not a rewrite worth learning from).
     *
     * CANCELLED and FAILED rows count: the user still expressed a preference by
     * rewriting the text, and whether the post later published is unrelated to
     * how they write. Backs PreferredVoiceExamples.
     *
     * Ordered by id as well as created_at — several posts scheduled in the same
     * batch share a timestamp to the millisecond, and "newest first" has to be
     * a total order or the limit cuts arbitrarily between them.
     *
     * Native for btrim's character set. JPQL trim() becomes SQL TRIM, which
     * strips spaces and nothing else — a textarea's trailing newline would then
     * read as a rewrite, and the model's own text would come back labelled as
     * the user's voice. The whitespace that actually shows up here is newlines.
     */
    @Query(value = """
            SELECT * FROM scheduled_posts
             WHERE user_id = :#{#user.id}
               AND platform = 'LINKEDIN'
               AND generated_body IS NOT NULL
               AND btrim(body, E' \\t\\n\\r') <> btrim(generated_body, E' \\t\\n\\r')
             ORDER BY created_at DESC, id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<ScheduledPost> findEditedPairs(@Param("user") User user, @Param("limit") int limit);

    /**
     * Atomically claims due posts. FOR UPDATE SKIP LOCKED is the whole point:
     * without it a second app instance reads the same rows and posts twice.
     *
     * Deliberately not @Modifying — Spring Data rejects modifying queries that
     * return anything but void/int, and RETURNING needs the result set. Callers
     * must supply the transaction.
     */
    @Query(value = """
            UPDATE scheduled_posts
               SET status = 'PUBLISHING', claimed_by = :worker, claimed_at = now()
             WHERE id IN (
                 SELECT id FROM scheduled_posts
                  WHERE status = 'SCHEDULED' AND scheduled_for <= now()
                  ORDER BY scheduled_for
                  LIMIT :batchSize
                  FOR UPDATE SKIP LOCKED
             )
            RETURNING id
            """, nativeQuery = true)
    List<Long> claimDuePosts(@Param("worker") String worker,
                             @Param("batchSize") int batchSize);

    /**
     * Processes die mid-publish. Without this sweep those rows sit in PUBLISHING
     * forever and are never retried.
     */
    @Modifying
    @Query("""
           update ScheduledPost p
              set p.status = com.careeros.backend.schedule.PostStatus.SCHEDULED,
                  p.claimedBy = null,
                  p.claimedAt = null,
                  p.updatedAt = :now
            where p.status = com.careeros.backend.schedule.PostStatus.PUBLISHING
              and p.claimedAt < :cutoff
           """)
    int releaseStuck(@Param("cutoff") OffsetDateTime cutoff,
                     @Param("now") OffsetDateTime now);
}
