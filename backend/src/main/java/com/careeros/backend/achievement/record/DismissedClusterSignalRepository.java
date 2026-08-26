package com.careeros.backend.achievement.record;

import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DismissedClusterSignalRepository extends JpaRepository<DismissedClusterSignal, Long> {

    /** Backs DismissedAreaOverlapGate — every dismissal recorded for this user in this repository. */
    List<DismissedClusterSignal> findByUserAndRepositoryName(User user, String repositoryName);
}
