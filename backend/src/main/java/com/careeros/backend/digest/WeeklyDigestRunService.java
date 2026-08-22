package com.careeros.backend.digest;

import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** Upserts the single per-user row — see WeeklyDigestRun for why there's only ever one. */
@Service
@RequiredArgsConstructor
public class WeeklyDigestRunService {

    private final WeeklyDigestRunRepository weeklyDigestRunRepository;

    @Transactional(readOnly = true)
    public Optional<WeeklyDigestRun> findByUser(User user) {
        return weeklyDigestRunRepository.findByUser(user);
    }

    @Transactional
    public void record(
            User user,
            WeeklyDigestOutcome outcome,
            String reason,
            int reposSynced,
            int commitsSynced,
            int reposAnalyzed,
            int achievementsCreated
    ) {
        WeeklyDigestRun run = weeklyDigestRunRepository.findByUser(user)
                .orElseGet(() -> WeeklyDigestRun.builder().user(user).build());

        run.setRunAt(LocalDateTime.now());
        run.setOutcome(outcome);
        run.setReason(reason);
        run.setReposSynced(reposSynced);
        run.setCommitsSynced(commitsSynced);
        run.setReposAnalyzed(reposAnalyzed);
        run.setAchievementsCreated(achievementsCreated);

        weeklyDigestRunRepository.save(run);
    }
}
