package com.careeros.backend.digest;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * isDue()'s day/hour/timezone/already-ran guard is real branching logic with
 * no other coverage. Clock is injected specifically so this doesn't depend
 * on when the test happens to run.
 */
class WeeklyDigestSchedulerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final WeeklyDigestRunService weeklyDigestRunService = mock(WeeklyDigestRunService.class);
    private final WeeklyDigestService weeklyDigestService = mock(WeeklyDigestService.class);

    /** 2026-08-24 is a Monday. 09:00 UTC. */
    private static final Instant MONDAY_9AM_UTC =
            LocalDateTime.of(2026, 8, 24, 9, 0).atZone(ZoneOffset.UTC).toInstant();

    private WeeklyDigestScheduler schedulerAt(Instant instant) {
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        return new WeeklyDigestScheduler(userRepository, weeklyDigestRunService, weeklyDigestService, clock);
    }

    private static User userWithZone(String zone) {
        return User.builder().id(1L).githubId(1L).username("u").timezone(zone).build();
    }

    @Test
    void triggersAtMonday9amInTheUsersOwnTimezone() {
        User user = userWithZone("UTC");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(weeklyDigestRunService.findByUser(user)).thenReturn(Optional.empty());

        schedulerAt(MONDAY_9AM_UTC).tick();

        verify(weeklyDigestService).runForUser(1L);
    }

    @Test
    void doesNotTriggerAtTheSameServerInstantForAUserInADifferentTimezone() {
        // 09:00 UTC is 14:30 in Asia/Kolkata (UTC+5:30) — not that user's 09:00.
        User user = userWithZone("Asia/Kolkata");
        when(userRepository.findAll()).thenReturn(List.of(user));

        schedulerAt(MONDAY_9AM_UTC).tick();

        verifyNoInteractions(weeklyDigestService);
    }

    @Test
    void triggersForThatTimezonesOwnMonday9am() {
        // Asia/Kolkata is UTC+5:30, so their 09:00 Monday is 03:30 UTC Monday.
        Instant kolkataNine = MONDAY_9AM_UTC.minusSeconds(5 * 3600 + 30 * 60);
        User user = userWithZone("Asia/Kolkata");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(weeklyDigestRunService.findByUser(user)).thenReturn(Optional.empty());

        schedulerAt(kolkataNine).tick();

        verify(weeklyDigestService).runForUser(1L);
    }

    @Test
    void doesNotTriggerOutsideTheMatchingHour() {
        User user = userWithZone("UTC");
        when(userRepository.findAll()).thenReturn(List.of(user));

        schedulerAt(MONDAY_9AM_UTC.plusSeconds(3600)).tick(); // 10:00 Monday

        verifyNoInteractions(weeklyDigestService);
    }

    @Test
    void doesNotTriggerTwiceWithinTheSameWeek() {
        User user = userWithZone("UTC");
        when(userRepository.findAll()).thenReturn(List.of(user));
        WeeklyDigestRun lastRun = WeeklyDigestRun.builder()
                .runAt(LocalDateTime.of(2026, 8, 24, 9, 0)) // ran this same Monday already
                .build();
        when(weeklyDigestRunService.findByUser(user)).thenReturn(Optional.of(lastRun));

        schedulerAt(MONDAY_9AM_UTC.plusSeconds(60)).tick(); // a second tick landing in the same hour

        verifyNoInteractions(weeklyDigestService);
    }

    @Test
    void triggersAgainTheFollowingMondayAfterALastRun() {
        User user = userWithZone("UTC");
        when(userRepository.findAll()).thenReturn(List.of(user));
        WeeklyDigestRun lastRun = WeeklyDigestRun.builder()
                .runAt(LocalDateTime.of(2026, 8, 17, 9, 0)) // ran last Monday
                .build();
        when(weeklyDigestRunService.findByUser(user)).thenReturn(Optional.of(lastRun));

        schedulerAt(MONDAY_9AM_UTC).tick();

        verify(weeklyDigestService).runForUser(1L);
    }

    @Test
    void anUnrecognisedTimezoneFallsBackToUtcRatherThanErroring() {
        User user = userWithZone("not-a-real-zone");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(weeklyDigestRunService.findByUser(user)).thenReturn(Optional.empty());

        schedulerAt(MONDAY_9AM_UTC).tick();

        verify(weeklyDigestService).runForUser(1L);
    }
}
