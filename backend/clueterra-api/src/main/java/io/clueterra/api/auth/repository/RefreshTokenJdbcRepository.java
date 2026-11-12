package io.clueterra.api.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RefreshTokenJdbcRepository {

    private static final String INSERT = """
            INSERT INTO refresh_tokens (token_hash, user_id, family_id, expires_at)
            VALUES (:tokenHash, :userId, :familyId, :expiresAt)
            """;

    private static final String ROTATE = """
            SELECT result_status, result_user_id
            FROM rotate_refresh_token(:tokenHash, :newTokenHash, :expiresAt, :reuseGraceSeconds)
            """;

    private static final String REVOKE_FAMILY = """
            UPDATE refresh_tokens
            SET revoked_at = NOW()
            WHERE revoked_at IS NULL
              AND family_id = (SELECT family_id FROM refresh_tokens WHERE token_hash = :tokenHash)
            """;

    private static final String REVOKE_ALL_FOR_USER = """
            UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = :userId AND revoked_at IS NULL
            """;

    private static final String DELETE_EXPIRED = """
            DELETE FROM refresh_tokens WHERE expires_at < NOW()
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public void insert(String tokenHash, long userId, UUID familyId, Instant expiresAt) {
        var params = new MapSqlParameterSource()
                .addValue("tokenHash", tokenHash)
                .addValue("userId", userId)
                .addValue("familyId", familyId)
                .addValue("expiresAt", Timestamp.from(expiresAt));
        jdbc.update(INSERT, params);
    }

    public RefreshTokenRotation rotate(String tokenHash, String newTokenHash, Instant expiresAt, int reuseGraceSeconds) {
        var params = new MapSqlParameterSource()
                .addValue("tokenHash", tokenHash)
                .addValue("newTokenHash", newTokenHash)
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("reuseGraceSeconds", reuseGraceSeconds);
        return jdbc.queryForObject(ROTATE, params, (rs, rowNum) -> new RefreshTokenRotation(
                RefreshTokenRotationStatus.valueOf(rs.getString("result_status")),
                rs.getObject("result_user_id", Long.class)));
    }

    public int revokeFamily(String tokenHash) {
        return jdbc.update(REVOKE_FAMILY, new MapSqlParameterSource("tokenHash", tokenHash));
    }

    public int revokeAllForUser(long userId) {
        return jdbc.update(REVOKE_ALL_FOR_USER, new MapSqlParameterSource("userId", userId));
    }

    public int deleteExpired() {
        return jdbc.update(DELETE_EXPIRED, new MapSqlParameterSource());
    }
}
