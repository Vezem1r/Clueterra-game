package io.clueterra.questions.repository.jdbc;

import io.clueterra.questions.entity.DailyChallenge;
import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.Difficulty;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DailyChallengeJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    private static final String INSERT_SQL = """
            INSERT INTO daily_challenges (id, challenge_date, challenge_level, countries_json, hints_json,
                difficulty, total_countries, total_hints, is_active, created_at)
            VALUES (:id, :challengeDate, :challengeLevel, :countriesJson, :hintsJson,
                :difficulty, :totalCountries, :totalHints, :isActive, :createdAt)
            """;

    private static final String UPDATE_SQL = """
            UPDATE daily_challenges
            SET countries_json = :countriesJson, hints_json = :hintsJson,
                difficulty = :difficulty, total_countries = :totalCountries,
                total_hints = :totalHints, is_active = :isActive
            WHERE id = :id
            """;

    public DailyChallenge save(DailyChallenge challenge) {
        if (challenge.getId() == null) {
            challenge.setId(TSID.Factory.getTsid().toLong());
        }
        if (challenge.getCreatedAt() == null) {
            challenge.setCreatedAt(Instant.now(clock));
        }
        if (challenge.getChallengeLevel() == null) {
            challenge.setChallengeLevel(ChallengeLevel.MEDIUM);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", challenge.getId())
                .addValue("challengeDate", Date.valueOf(challenge.getChallengeDate()))
                .addValue("challengeLevel", challenge.getChallengeLevel().name())
                .addValue("countriesJson", challenge.getCountriesJson())
                .addValue("hintsJson", challenge.getHintsJson())
                .addValue("difficulty", challenge.getDifficulty().name())
                .addValue("totalCountries", challenge.getTotalCountries())
                .addValue("totalHints", challenge.getTotalHints())
                .addValue("isActive", challenge.getIsActive())
                .addValue("createdAt", Timestamp.from(challenge.getCreatedAt()));

        jdbc.update(INSERT_SQL, params);
        return challenge;
    }

    public Optional<DailyChallenge> findById(Long id) {
        List<DailyChallenge> results = jdbc.query(
                "SELECT * FROM daily_challenges WHERE id = :id",
                Map.of("id", id),
                rowMapper()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public void update(DailyChallenge challenge) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", challenge.getId())
                .addValue("countriesJson", challenge.getCountriesJson())
                .addValue("hintsJson", challenge.getHintsJson())
                .addValue("difficulty", challenge.getDifficulty().name())
                .addValue("totalCountries", challenge.getTotalCountries())
                .addValue("totalHints", challenge.getTotalHints())
                .addValue("isActive", challenge.getIsActive());
        jdbc.update(UPDATE_SQL, params);
    }

    public Optional<DailyChallenge> findByDateAndLevel(LocalDate date, ChallengeLevel level) {
        List<DailyChallenge> results = jdbc.query(
                "SELECT * FROM daily_challenges WHERE challenge_date = :date AND challenge_level = :level",
                Map.of("date", Date.valueOf(date), "level", level.name()),
                rowMapper()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public void lockSlotForGeneration(LocalDate date, ChallengeLevel level) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "daily-challenge:" + date + ":" + level.name()));
    }

    public Optional<DailyChallenge> findByIdForUpdate(Long id) {
        return jdbc.query("SELECT * FROM daily_challenges WHERE id = :id FOR UPDATE", Map.of("id", id), rowMapper())
                .stream()
                .findFirst();
    }

    public int deactivateOlderThan(LocalDate beforeDate) {
        return jdbc.update("""
                UPDATE daily_challenges
                SET is_active = false
                WHERE is_active = true AND challenge_date < :beforeDate
                """, Map.of("beforeDate", Date.valueOf(beforeDate)));
    }

    public List<DailyChallenge> findByDateRangeAndLevel(
            LocalDate startDate, LocalDate endDate, ChallengeLevel level) {
        return jdbc.query("""
                        SELECT * FROM daily_challenges
                        WHERE challenge_date >= :startDate AND challenge_date <= :endDate
                          AND challenge_level = :level
                        ORDER BY challenge_date
                        """,
                Map.of("startDate", Date.valueOf(startDate),
                        "endDate", Date.valueOf(endDate),
                        "level", level.name()),
                rowMapper()
        );
    }

    public long count() {
        Long result = jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_challenges",
                Map.of(),
                Long.class
        );
        return result != null ? result : 0;
    }

    public long countActive() {
        Long result = jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_challenges WHERE is_active = true",
                Map.of(),
                Long.class
        );
        return result != null ? result : 0;
    }

    private RowMapper<DailyChallenge> rowMapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    private DailyChallenge mapRow(ResultSet rs) throws SQLException {
        return DailyChallenge.builder()
                .id(rs.getLong("id"))
                .challengeDate(rs.getDate("challenge_date").toLocalDate())
                .challengeLevel(ChallengeLevel.fromString(rs.getString("challenge_level")))
                .countriesJson(rs.getString("countries_json"))
                .hintsJson(rs.getString("hints_json"))
                .difficulty(Difficulty.valueOf(rs.getString("difficulty")))
                .totalCountries(rs.getInt("total_countries"))
                .totalHints(rs.getInt("total_hints"))
                .isActive(rs.getBoolean("is_active"))
                .createdAt(getInstant(rs))
                .build();
    }

    private Instant getInstant(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return ts != null ? ts.toInstant() : null;
    }
}
