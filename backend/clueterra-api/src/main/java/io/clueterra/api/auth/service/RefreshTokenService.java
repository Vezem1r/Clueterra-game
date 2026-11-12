package io.clueterra.api.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.clueterra.api.auth.repository.RefreshTokenJdbcRepository;
import io.clueterra.api.auth.repository.RefreshTokenRotation;
import io.clueterra.api.auth.repository.RefreshTokenRotationStatus;
import io.clueterra.api.config.JwtProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenJdbcRepository repository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public String issue(long userId) {
        String token = newToken();
        repository.insert(hash(token), userId, UUID.randomUUID(), expiresAt());
        return token;
    }

    public Optional<RotatedRefreshToken> rotate(String token) {
        if (!hasText(token)) {
            return Optional.empty();
        }
        String newToken = newToken();
        RefreshTokenRotation rotation = repository.rotate(
                hash(token), hash(newToken), expiresAt(), jwtProperties.getRefreshReuseGraceSeconds());
        if (rotation.status() == RefreshTokenRotationStatus.REUSED) {
            log.warn("Refresh token reuse detected; revoked the session family for userId={}", rotation.userId());
        }
        return rotation.status() == RefreshTokenRotationStatus.ROTATED
                ? Optional.of(new RotatedRefreshToken(rotation.userId(), newToken))
                : Optional.empty();
    }

    public void revoke(String token) {
        if (hasText(token)) {
            repository.revokeFamily(hash(token));
        }
    }

    public void revokeAll(long userId) {
        int revoked = repository.revokeAllForUser(userId);
        if (revoked > 0) {
            log.info("Revoked {} refresh tokens for userId={}", revoked, userId);
        }
    }

    public int deleteExpired() {
        return repository.deleteExpired();
    }

    private Instant expiresAt() {
        return clock.instant().plusMillis(jwtProperties.getRefreshExpiration());
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
