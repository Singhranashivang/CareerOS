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
