package com.careeros.backend.audit;

import com.careeros.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /** Backs GET /api/account/export — every audit entry for one user. */
    List<AuditLogEntity> findByUserOrderByOccurredAtDesc(User user);
}
