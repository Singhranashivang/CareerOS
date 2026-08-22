package com.careeros.backend.audit;

import com.careeros.backend.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditLogServiceTest {

    @Test
    void neverThrowsEvenWhenTheRepositoryFails() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));
        AuditLogService service = new AuditLogService(repository);

        User user = User.builder().id(1L).build();

        // A broken audit write must never turn into a broken request for the caller.
        assertThatCode(() -> service.record(user, AuditAction.SIGN_IN, null, AuditOutcome.SUCCESS))
                .doesNotThrowAnyException();
    }
}
