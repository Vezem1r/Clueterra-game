package io.clueterra.api.auth.dto;

public record OAuthUserInfo(
        String email,
        String name,
        String avatarUrl,
        String oauthId,
        boolean emailVerified) {
}
