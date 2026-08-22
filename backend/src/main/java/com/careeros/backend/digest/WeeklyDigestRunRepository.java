package com.careeros.backend.digest;

import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeeklyDigestRunRepository extends JpaRepository<WeeklyDigestRun, Long> {

    Optional<WeeklyDigestRun> findByUser(User user);
}
