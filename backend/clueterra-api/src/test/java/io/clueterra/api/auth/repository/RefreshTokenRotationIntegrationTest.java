package io.clueterra.api.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import io.clueterra.api.TestcontainersConfiguration;

@JdbcTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.schemas=cl_api_schema",
                "spring.flyway.default-schema=cl_api_schema",
                "spring.flyway.create-schemas=true",
                "spring.datasource.hikari.connection-init-sql=SET search_path TO cl_api_schema, cl_schema"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, RefreshTokenJdbcRepository.class})
class RefreshTokenRotationIntegrationTest {

    private static final long USER_ID = 1L;
    private static final int GRACE_SECONDS = 30;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private RefreshTokenJdbcRepository repository;

    @BeforeEach
    void insertUser() {
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_api_schema.users CASCADE");
        jdbc.update("""
                INSERT INTO users (id, email, username, password_hash, role, enabled)
                VALUES (:id, 'player@example.com', 'player', 'hash', 'USER', TRUE)
                """, Map.of("id", USER_ID));
    }

    @Test
    void rotatesAValidTokenIntoTheSameFamily() {
        repository.insert(hash('a'), USER_ID, UUID.randomUUID(), future());

        RefreshTokenRotation rotation = repository.rotate(hash('a'), hash('b'), future(), GRACE_SECONDS);

        assertThat(rotation).isEqualTo(new RefreshTokenRotation(RefreshTokenRotationStatus.ROTATED, USER_ID));
        assertThat(familyOf(hash('b'))).isEqualTo(familyOf(hash('a')));
        assertThat(repository.rotate(hash('b'), hash('c'), future(), GRACE_SECONDS).status())
                .isEqualTo(RefreshTokenRotationStatus.ROTATED);
    }

    @Test
    void toleratesConcurrentReuseWithinTheGraceWindow() {
        repository.insert(hash('a'), USER_ID, UUID.randomUUID(), future());
        repository.rotate(hash('a'), hash('b'), future(), GRACE_SECONDS);

        RefreshTokenRotation second = repository.rotate(hash('a'), hash('c'), future(), GRACE_SECONDS);

        assertThat(second.status()).isEqualTo(RefreshTokenRotationStatus.ROTATED);
        assertThat(isRevoked(hash('b'))).isFalse();
        assertThat(isRevoked(hash('c'))).isFalse();
    }

    @Test
    void reuseAfterTheGraceWindowRevokesTheWholeFamily() {
        repository.insert(hash('a'), USER_ID, UUID.randomUUID(), future());
        repository.rotate(hash('a'), hash('b'), future(), GRACE_SECONDS);
        jdbc.update("UPDATE refresh_tokens SET used_at = NOW() - INTERVAL '1 minute' WHERE token_hash = :hash",
                Map.of("hash", hash('a')));

        RefreshTokenRotation reuse = repository.rotate(hash('a'), hash('c'), future(), GRACE_SECONDS);

        assertThat(reuse).isEqualTo(new RefreshTokenRotation(RefreshTokenRotationStatus.REUSED, USER_ID));
        assertThat(isRevoked(hash('b'))).isTrue();
        assertThat(exists(hash('c'))).isFalse();
        assertThat(repository.rotate(hash('b'), hash('d'), future(), GRACE_SECONDS).status())
                .isEqualTo(RefreshTokenRotationStatus.INVALID);
    }

    @Test
    void rejectsUnknownExpiredAndRevokedTokens() {
        repository.insert(hash('e'), USER_ID, UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.MINUTES));
        repository.insert(hash('r'), USER_ID, UUID.randomUUID(), future());
        repository.revokeFamily(hash('r'));

        assertThat(repository.rotate(hash('u'), hash('x'), future(), GRACE_SECONDS))
                .isEqualTo(new RefreshTokenRotation(RefreshTokenRotationStatus.INVALID, null));
        assertThat(repository.rotate(hash('e'), hash('y'), future(), GRACE_SECONDS).status())
                .isEqualTo(RefreshTokenRotationStatus.INVALID);
        assertThat(repository.rotate(hash('r'), hash('z'), future(), GRACE_SECONDS).status())
                .isEqualTo(RefreshTokenRotationStatus.INVALID);
    }

    @Test
    void revokesEverySessionOfAUserAndCleansUpExpiredTokens() {
        repository.insert(hash('a'), USER_ID, UUID.randomUUID(), future());
        repository.insert(hash('b'), USER_ID, UUID.randomUUID(), future());
        repository.insert(hash('e'), USER_ID, UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(repository.revokeAllForUser(USER_ID)).isEqualTo(3);
        assertThat(repository.deleteExpired()).isEqualTo(1);
        assertThat(isRevoked(hash('a'))).isTrue();
        assertThat(isRevoked(hash('b'))).isTrue();
    }

    @Test
    void deletingAUserDeletesTheirRefreshTokens() {
        repository.insert(hash('a'), USER_ID, UUID.randomUUID(), future());

        jdbc.update("DELETE FROM users WHERE id = :id", Map.of("id", USER_ID));

        assertThat(exists(hash('a'))).isFalse();
    }

    private static String hash(char fill) {
        return String.valueOf(fill).repeat(64);
    }

    private static Instant future() {
        return Instant.now().plus(7, ChronoUnit.DAYS);
    }

    private UUID familyOf(String tokenHash) {
        return jdbc.queryForObject("SELECT family_id FROM refresh_tokens WHERE token_hash = :hash",
                Map.of("hash", tokenHash), UUID.class);
    }

    private boolean isRevoked(String tokenHash) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM refresh_tokens WHERE token_hash = :hash",
                Map.of("hash", tokenHash), Boolean.class));
    }

    private boolean exists(String tokenHash) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM refresh_tokens WHERE token_hash = :hash)",
                Map.of("hash", tokenHash), Boolean.class));
    }
}
