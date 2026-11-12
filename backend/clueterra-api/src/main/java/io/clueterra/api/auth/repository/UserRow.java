package io.clueterra.api.auth.repository;

public record UserRow(
        Long id,
        String email,
        String passwordHash,
        String username,
        String role,
        boolean enabled,
        String totpSecret,
        boolean totpEnabled,
        String oauthProvider,
        String oauthId,
        String termsVersionAccepted,
        String privacyVersionAccepted,
        boolean isGuest,
        String guestToken) {
}
