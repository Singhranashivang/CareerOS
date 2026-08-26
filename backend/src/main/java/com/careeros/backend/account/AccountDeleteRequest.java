package com.careeros.backend.account;

/** DELETE /api/account — username must exactly match the caller's own, typed by hand. */
public record AccountDeleteRequest(String username) {
}
