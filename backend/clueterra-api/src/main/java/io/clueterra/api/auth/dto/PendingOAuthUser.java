package io.clueterra.api.auth.dto;

public record PendingOAuthUser(
        String email, String name, String avatarUrl, String oauthId, String provider) {
}
