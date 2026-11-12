package io.clueterra.api.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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
@Import({TestcontainersConfiguration.class, ProfileJdbcRepository.class})
class ProfileProjectionIntegrationTest {

    private static final String TRUNCATE_TEST_DATA =
            "TRUNCATE cl_api_schema.users, cl_api_schema.game_sessions, cl_schema.daily_challenges, cl_schema.countries CASCADE";

    private static final String SEED_COUNTRIES = """
            INSERT INTO cl_schema.countries (id, iso_alpha2, iso_alpha3, name_en)
            VALUES
                (10, 'DE', 'DEU', 'Germany'),
                (20, 'FR', 'FRA', 'France')
            """;

    private static final String SEED_CHALLENGE = """
            INSERT INTO cl_schema.daily_challenges (
                id, challenge_date, challenge_level, countries_json, hints_json,
                difficulty, total_countries, total_hints, is_active
            )
            VALUES (
                1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE, 'EASY', '[]', '[]',
                'EASY', 2, 6, TRUE
            )
            """;

    private static final String INSERT_USER = """
            INSERT INTO users (id, email, username, password_hash, role, enabled, is_guest)
            VALUES (:id, :email, :username, 'hash', 'USER', TRUE, FALSE)
            """;

    private static final String INSERT_PROFILE = """
            INSERT INTO user_profiles (
                user_id, total_xp, current_level, challenges_played, challenges_won,
                current_streak, best_streak, streak_freezes
            )
            VALUES (:userId, 0, 1, 0, 0, 0, 0, 0)
            """;

    private static final String INSERT_SESSION = """
            INSERT INTO game_sessions (
                id, user_id, challenge_id, challenge_level, status, current_country_idx,
                started_at, last_activity_at, completed_at, total_xp_earned,
                streak_bonus_percent, streak_bonus_xp, max_hints_per_country,
                max_attempts, xp_before, level_before
            )
            VALUES (
                :id, :userId, 1, :challengeLevel, 'COMPLETED', 0,
                :startedAt, :completedAt, :completedAt, :totalXp,
                0, 0, 3,
                3, 0, 1
            )
            """;

    private static final String INSERT_SESSION_COUNTRY = """
            INSERT INTO game_session_countries (
                id, session_id, country_id, country_order, total_hints, hints_revealed,
                attempts_used, is_guessed, guessed_at, time_spent_ms, xp_earned
            )
            VALUES (
                :id, :sessionId, :countryId, :countryOrder, 3, 1,
                :attemptsUsed, :guessed, :guessedAt, 5000, :xpEarned
            )
            """;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private ProfileJdbcRepository profileRepository;

    @BeforeEach
    void cleanData() {
        jdbc.getJdbcTemplate().execute(TRUNCATE_TEST_DATA);
        jdbc.getJdbcTemplate().execute(SEED_COUNTRIES);
        jdbc.getJdbcTemplate().execute(SEED_CHALLENGE);
    }

    @Test
    @DisplayName("completed session projection is applied once and stores solved countries")
    void completedSessionProjectionIsIdempotent() {
        insertUser();
        insertProfile();
        insertSession("MEDIUM", Instant.parse("2026-06-15T10:05:00Z"), 150);
        insertSessionCountry(990L, 10L, 1, 1, 100);
        insertSessionCountry(991L, 20L, 2, 1, 50);

        assertThat(applyProjection()).isTrue();
        assertThat(applyProjection()).isFalse();

        assertThat(profileValue("countries_played")).isEqualTo(2);
        assertThat(profileValue("countries_solved")).isEqualTo(2);
        assertThat(profileValue("perfect_runs")).isEqualTo(1);
        assertThat(profileStringValue()).isEqualTo("MEDIUM");
        assertThat(solutionCount()).isEqualTo(2);
        assertThat(difficultyRuns()).isEqualTo(1);
        assertThat(difficultyValue("MEDIUM", "countries_played")).isEqualTo(2);
        assertThat(difficultyValue("MEDIUM", "countries_solved")).isEqualTo(2);
        assertThat(projectionAppliedAt()).isNotNull();
    }

    @Test
    @DisplayName("completed but non-perfect session does not increment perfectRuns")
    void nonPerfectSessionDoesNotIncrementPerfectRuns() {
        insertUser();
        insertProfile();
        insertSession("EASY", Instant.parse("2026-06-15T10:05:00Z"), 80);
        insertSessionCountry(990L, 10L, 1, 2, 80);

        assertThat(applyProjection()).isTrue();

        assertThat(profileValue("countries_played")).isEqualTo(1);
        assertThat(profileValue("countries_solved")).isEqualTo(1);
        assertThat(profileValue("perfect_runs")).isZero();
        assertThat(solutionCount()).isEqualTo(1);
        assertThat(difficultyValue("EASY", "countries_played")).isEqualTo(1);
        assertThat(difficultyValue("EASY", "countries_solved")).isEqualTo(1);
    }

    @Test
    void privateGuestProfileIncludesRealStatsWithoutALeaderboardRank() {
        insertUser();
        insertProfile();
        jdbc.update("UPDATE users SET is_guest = TRUE WHERE id = :userId",
                new MapSqlParameterSource("userId", 42L));
        jdbc.update("""
                UPDATE user_profiles
                SET total_xp = 250, current_level = 2, challenges_played = 2,
                    countries_solved = 3, perfect_runs = 1, favorite_difficulty = 'EASY'
                WHERE user_id = :userId
                """, new MapSqlParameterSource("userId", 42L));

        ProfileRow profile = profileRepository.findOwnProfile(42L).orElseThrow();

        assertThat(profile.totalXp()).isEqualTo(250);
        assertThat(profile.completedRuns()).isEqualTo(2);
        assertThat(profile.countriesSolved()).isEqualTo(3);
        assertThat(profile.perfectRuns()).isEqualTo(1);
        assertThat(profile.favoriteDifficulty()).isEqualTo("EASY");
        assertThat(profile.leaderboardRank()).isNull();
    }

    private void insertUser() {
        jdbc.update(INSERT_USER, new MapSqlParameterSource()
                .addValue("id", 42L)
                .addValue("email", "user" + 42L + "@example.com")
                .addValue("username", "user" + 42L));
    }

    private void insertProfile() {
        jdbc.update(INSERT_PROFILE, new MapSqlParameterSource("userId", 42L));
    }

    private void insertSession(String challengeLevel, Instant completedAt, int totalXp) {
        jdbc.update(INSERT_SESSION, new MapSqlParameterSource()
                .addValue("id", 99L)
                .addValue("userId", 42L)
                .addValue("challengeLevel", challengeLevel)
                .addValue("startedAt", Timestamp.from(completedAt.minusSeconds(60)))
                .addValue("completedAt", Timestamp.from(completedAt))
                .addValue("totalXp", totalXp));
    }

    private void insertSessionCountry(
            long id,
            long countryId,
            int countryOrder,
            int attemptsUsed,
            int xpEarned) {
        jdbc.update(INSERT_SESSION_COUNTRY, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("sessionId", 99L)
                .addValue("countryId", countryId)
                .addValue("countryOrder", countryOrder)
                .addValue("guessed", true)
                .addValue("attemptsUsed", attemptsUsed)
                .addValue("guessedAt", Timestamp.from(Instant.parse("2026-06-15T10:03:00Z")))
                .addValue("xpEarned", xpEarned));
    }

    private boolean applyProjection() {
        Boolean applied = jdbc.queryForObject(
                "SELECT apply_completed_session_projection(:sessionId)",
                new MapSqlParameterSource("sessionId", 99L),
                Boolean.class);
        return Boolean.TRUE.equals(applied);
    }

    private long profileValue(String column) {
        Number value = jdbc.queryForObject(
                "SELECT " + column + " FROM user_profiles WHERE user_id = :userId",
                new MapSqlParameterSource("userId", 42L),
                Number.class);
        assert value != null;
        return value.longValue();
    }

    private String profileStringValue() {
        return jdbc.queryForObject(
                "SELECT " + "favorite_difficulty" + " FROM user_profiles WHERE user_id = :userId",
                new MapSqlParameterSource("userId", 42L),
                String.class);
    }

    private long solutionCount() {
        Number value = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM user_country_solutions
                        WHERE user_id = :userId
                          AND source_session_id = :sessionId
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", 42L)
                        .addValue("sessionId", 99L),
                Number.class);
        assert value != null;
        return value.longValue();
    }

    private long difficultyRuns() {
        return difficultyValue("MEDIUM", "completed_runs");
    }

    private long difficultyValue(String challengeLevel, String column) {
        Number value = jdbc.queryForObject(
                """
                        SELECT %s
                        FROM user_difficulty_stats
                        WHERE user_id = :userId
                          AND challenge_level = :challengeLevel
                        """.formatted(column),
                new MapSqlParameterSource()
                        .addValue("userId", 42L)
                        .addValue("challengeLevel", challengeLevel),
                Number.class);
        assert value != null;
        return value.longValue();
    }

    private Timestamp projectionAppliedAt() {
        return jdbc.queryForObject(
                "SELECT profile_projection_applied_at FROM game_sessions WHERE id = :sessionId",
                new MapSqlParameterSource("sessionId", 99L),
                Timestamp.class);
    }
}
