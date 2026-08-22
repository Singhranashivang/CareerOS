package com.careeros.backend.digest;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Fires hourly, on the server's own clock, and checks each user's LOCAL time
 * against Monday 09:00 — there's no single server-side cron expression that
 * means "09:00 in whichever of N different timezones a user picked", so this
 * evaluates the condition per user instead of trying to schedule per user.
 *
 * ponytail: userRepository.findAll() every tick, O(users). Fine at this
 * scale; switch to a paged/batch query if the user base grows past a few
 * hundred.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyDigestScheduler {

    private final UserRepository userRepository;
    private final WeeklyDigestRunService weeklyDigestRunService;
    private final WeeklyDigestService weeklyDigestService;
    private final Clock clock;

    @Scheduled(cron = "0 0 * * * *") // top of every hour
    public void tick() {
        for (User user : userRepository.findAll()) {
            if (isDue(user)) {
                log.info("Weekly digest due for user {} (timezone {})", user.getId(), user.getTimezone());
                weeklyDigestService.runForUser(user.getId());
            }
        }
    }

    private boolean isDue(User user) {
        ZonedDateTime local = ZonedDateTime.now(clock).withZoneSameInstant(zoneOf(user));
        if (local.getDayOfWeek() != DayOfWeek.MONDAY || local.getHour() != 9) {
            return false;
        }

        // Guards against firing twice if the tick lands more than once in the
        // matching hour, and against re-firing later the same week — the next
        // eligible Monday 09:00 is always more than 6 days after the last run.
        return weeklyDigestRunService.findByUser(user)
                .map(run -> run.getRunAt().isBefore(LocalDateTime.now(clock).minusDays(6)))
                .orElse(true);
    }

    private static ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (Exception e) {
            log.warn("User {} has an unrecognised timezone '{}', falling back to UTC",
                    user.getId(), user.getTimezone());
            return ZoneOffset.UTC;
        }
    }
}
