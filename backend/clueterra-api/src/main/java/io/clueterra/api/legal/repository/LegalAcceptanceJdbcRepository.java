package io.clueterra.api.legal.repository;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class LegalAcceptanceJdbcRepository {

    private static final String UPDATE_USER_ACCEPTANCE = """
            UPDATE users
            SET terms_version_accepted = :termsVersion,
                privacy_version_accepted = :privacyVersion,
                legal_accepted_at = :acceptedAt,
                updated_at = NOW()
            WHERE id = :userId
            """;

    private static final String INSERT_EVENT = """
            INSERT INTO legal_acceptance_events (
                id, user_id, game_session_id, acceptance_source, terms_version,
                privacy_version, accepted_at
            )
            VALUES (
                :id, :userId, :gameSessionId, :acceptanceSource, :termsVersion,
                :privacyVersion, :acceptedAt
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public void updateUserAcceptance(long userId, String termsVersion, String privacyVersion, Instant acceptedAt) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("termsVersion", termsVersion)
                .addValue("privacyVersion", privacyVersion)
                .addValue("acceptedAt", Timestamp.from(acceptedAt));
        jdbc.update(UPDATE_USER_ACCEPTANCE, params);
    }

    public void insertEvent(Long userId, Long gameSessionId, String acceptanceSource, String termsVersion,
                            String privacyVersion, Instant acceptedAt) {
        var params = new MapSqlParameterSource()
                .addValue("id", TSID.Factory.getTsid().toLong())
                .addValue("userId", userId)
                .addValue("gameSessionId", gameSessionId)
                .addValue("acceptanceSource", acceptanceSource)
                .addValue("termsVersion", termsVersion)
                .addValue("privacyVersion", privacyVersion)
                .addValue("acceptedAt", Timestamp.from(acceptedAt));
        jdbc.update(INSERT_EVENT, params);
    }
}
