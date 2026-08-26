package com.careeros.backend.account;

import com.careeros.backend.audit.AuditAction;
import com.careeros.backend.audit.AuditLogService;
import com.careeros.backend.audit.AuditOutcome;
import com.careeros.backend.security.CurrentUserService;
import com.careeros.backend.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    /** Falls through to RateLimitFilter's default READS tier — a handful of DB reads, no GitHub/LLM call. */
    @GetMapping("/export")
    public AccountExportResponse export() {
        User user = currentUserService.require();
        try {
            AccountExportResponse response = accountService.export(user);
            auditLogService.record(user, AuditAction.ACCOUNT_EXPORT, null, AuditOutcome.SUCCESS);
            return response;
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.ACCOUNT_EXPORT, null, AuditOutcome.FAILURE);
            throw e;
        }
    }

    /**
     * Falls through to RateLimitFilter's default READS tier — a user deletes
     * their account at most a handful of times ever, never a loop target.
     * Both the SUCCESS and FAILURE audit rows are written inside
     * AccountService.deleteAccount, not here — see its javadoc for why the
     * ordering relative to the delete matters for this one action.
     */
    @DeleteMapping
    public ResponseEntity<Void> delete(
            @RequestBody AccountDeleteRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        User user = currentUserService.require();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        accountService.deleteAccount(user, request.username());

        // The account (and with it, the session's own backing user row) is
        // gone — end the session now rather than leave a cookie that still
        // authenticates as a user CurrentUserService can no longer resolve.
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);

        return ResponseEntity.noContent().build();
    }

    /** Falls through to RateLimitFilter's default READS tier — one GitHub call, and only ever once per reconnect. */
    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect() {
        User user = currentUserService.require();
        try {
            accountService.disconnectGithub(user);
            auditLogService.record(user, AuditAction.ACCOUNT_DISCONNECT, null, AuditOutcome.SUCCESS);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            auditLogService.record(user, AuditAction.ACCOUNT_DISCONNECT, null, AuditOutcome.FAILURE);
            throw e;
        }
    }
}
