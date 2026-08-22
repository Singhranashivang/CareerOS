package com.careeros.backend.audit;

import com.careeros.backend.user.User;
import com.careeros.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQUIRES_NEW is the one non-obvious piece of AuditLogService: an audit row
 * for a FAILURE must survive even though the action it's recording rolled
 * back its own transaction — that's exactly the case this table exists to
 * capture. Needs the real Spring transactional proxy (a plain unit test
 * calling the service directly wouldn't exercise @Transactional at all), so
 * this runs against the real dev Postgres like the other @SpringBootTest
 * classes in this project.
 */
@SpringBootTest
class AuditLogServiceTransactionTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User user;

    @AfterEach
    void cleanUp() {
        if (user != null) {
            auditLogRepository.findAll().stream()
                    .filter(row -> row.getUser().getId().equals(user.getId()))
                    .forEach(auditLogRepository::delete);
            userRepository.delete(user);
        }
    }

    @Test
    void auditRowSurvivesEvenWhenTheEnclosingTransactionRollsBack() {
        user = userRepository.save(User.builder()
                .githubId(System.nanoTime())
                .username("audit-transaction-test")
                .build());

        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outerTransaction.executeWithoutResult(status -> {
            auditLogService.record(user, AuditAction.ACHIEVEMENT_ANALYZE, "4", AuditOutcome.FAILURE);
            throw new RuntimeException("force rollback of the enclosing transaction");
        })).hasMessageContaining("force rollback");

        List<AuditLogEntity> rows = auditLogRepository.findAll().stream()
                .filter(row -> row.getUser().getId().equals(user.getId()))
                .toList();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo(AuditAction.ACHIEVEMENT_ANALYZE);
        assertThat(rows.get(0).getTarget()).isEqualTo("4");
        assertThat(rows.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
    }
}
