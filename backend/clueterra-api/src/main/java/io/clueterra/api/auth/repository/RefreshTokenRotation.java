package io.clueterra.api.auth.repository;

public record RefreshTokenRotation(RefreshTokenRotationStatus status, Long userId) {
}
