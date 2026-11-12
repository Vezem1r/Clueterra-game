package io.clueterra.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.clueterra.api.auth.repository.RefreshTokenJdbcRepository;
import io.clueterra.api.auth.repository.RefreshTokenRotation;
import io.clueterra.api.auth.repository.RefreshTokenRotationStatus;
import io.clueterra.api.config.JwtProperties;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    private RefreshTokenJdbcRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshExpiration(604_800_000L);
        properties.setRefreshReuseGraceSeconds(30);
        repository = mock(RefreshTokenJdbcRepository.class);
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("issue stores only the SHA-256 hash of a random token with the refresh lifetime")
    void issueStoresHashOnly() {
        String token = service.issue(7L);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(hash.capture(), eq(7L), any(UUID.class), eq(NOW.plusSeconds(604_800)));
        assertThat(token).hasSize(43).doesNotContain("=");
        assertThat(hash.getValue()).isEqualTo(sha256(token)).isNotEqualTo(token);
        assertThat(service.issue(7L)).isNotEqualTo(token);
    }

    @Test
    @DisplayName("rotate returns the new token only when the stored token was rotated")
    void rotateReturnsNewTokenOnlyWhenRotated() {
        when(repository.rotate(eq(sha256("current")), anyString(), eq(NOW.plusSeconds(604_800)), eq(30)))
                .thenReturn(new RefreshTokenRotation(RefreshTokenRotationStatus.ROTATED, 7L));

        RotatedRefreshToken rotated = service.rotate("current").orElseThrow();

        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        verify(repository).rotate(eq(sha256("current")), newHash.capture(), any(), anyInt());
        assertThat(rotated.userId()).isEqualTo(7L);
        assertThat(newHash.getValue()).isEqualTo(sha256(rotated.token()));
    }

    @Test
    @DisplayName("rotate rejects reused and invalid tokens")
    void rotateRejectsReusedAndInvalidTokens() {
        when(repository.rotate(eq(sha256("reused")), anyString(), any(), anyInt()))
                .thenReturn(new RefreshTokenRotation(RefreshTokenRotationStatus.REUSED, 7L));
        when(repository.rotate(eq(sha256("unknown")), anyString(), any(), anyInt()))
                .thenReturn(new RefreshTokenRotation(RefreshTokenRotationStatus.INVALID, null));

        assertThat(service.rotate("reused")).isEmpty();
        assertThat(service.rotate("unknown")).isEmpty();
    }

    @Test
    @DisplayName("blank tokens never reach the database")
    void blankTokensAreIgnored() {
        assertThat(service.rotate(null)).isEmpty();
        assertThat(service.rotate(" ")).isEmpty();
        service.revoke(null);
        service.revoke("");

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("revoke and revokeAll target the token family and the whole user")
    void revokeTargetsFamilyAndUser() {
        service.revoke("current");
        service.revokeAll(7L);

        verify(repository).revokeFamily(sha256("current"));
        verify(repository).revokeAllForUser(7L);
        verify(repository, never()).insert(anyString(), eq(7L), any(), any());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
