package io.clueterra.api.auth.service;

public record RotatedRefreshToken(long userId, String token) {
}
