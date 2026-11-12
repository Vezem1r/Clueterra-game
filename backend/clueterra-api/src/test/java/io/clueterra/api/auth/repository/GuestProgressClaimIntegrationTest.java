package io.clueterra.api.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

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
@Import(TestcontainersConfiguration.class)
class GuestProgressClaimIntegrationTest {

    private static final String TRUNCATE_TEST_DATA =
            "TRUNCATE cl_api_schema.users, cl_api_schema.game_sessions, cl_schema.daily_challenges, cl_schema.countries CASCADE";

    private static final String SEED_COUNTRY = """
            INSERT INTO cl_schema.countries (id, iso_alpha2, iso_alpha3, name_en)
            VALUES (10, 'DE', 'DEU', 'Germany')
            """;

    private static final String SEED_CHALLENGE = """
            INSERT INTO cl_schema.daily_challenges (
                id, challenge_date, challenge_level, countries_json, hints_json,
                difficulty, total_countries, total_hints, is_active
            )
            VALUES (
                1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE, 'EASY', '[]', '[]',
                'EASY', 1, 1, TRUE
            )
            """;

    private static final String SET_NO_STREAK_BONUS = """
            INSERT INTO app_settings (key, value)
            VALUES ('streak_max_bonus_percent', '0')
            ON CONFLICT (key) DO UPDATE SET value = '0'
            """;

    private static final String INSERT_REGISTERED_USER = """
            INSERT INTO users (id, email, username, password_hash, role, enabled, is_guest)
            VALUES (:id, :email, :username, 'hash', 'USER', TRUE, FALSE)
            """;

    private static final String INSERT_GUEST_USER = """
            INSERT INTO users (id, role, enabled, is_guest, guest_token, guest_token_expires_at)
            VALUES (:id, 'USER', TRUE, TRUE, :guestToken, NOW() + INTERVAL '1 day')
            """;

    private static final String INSERT_PROFILE = """
            INSERT INTO user_profiles (
                user_id, total_xp, current_level, challenges_played, challenges_won,
                current_streak, best_streak, streak_freezes
            )
            VALUES (
                :userId, :totalXp, :level, :played, :won,
                :streak, :bestStreak, 0
            )
            """;

    private static final String INSERT_SESSION = """
            INSERT INTO game_sessions (
                id, user_id, challenge_id, challenge_level, status, current_country_idx,
                started_at, last_activity_at, completed_at, total_xp_earned,
                streak_bonus_percent, streak_bonus_xp, max_hints_per_country,
                max_attempts, xp_before, level_before
            )
            VALUES (
                :id, :userId, 1, 'EASY', :status, 0,
                :startedAt, :lastActivityAt, :completedAt, :totalXp,
                :streakBonusPercent, :streakBonusXp, 3,
                3, :xpBefore, :levelBefore
            )
            """;

    private static final String INSERT_SESSION_COUNTRY = """
            INSERT INTO game_session_countries (
                id, session_id, country_id, country_order, total_hints, hints_revealed,
                attempts_used, is_guessed, time_spent_ms, xp_earned
            )
            VALUES (
                :id, :sessionId, 10, 1, 3, 1,
                :attemptsUsed, :guessed, 5000, :xpEarned
            )
            """;

    private static final String INSERT_TODAY_PARTICIPATION = """
            INSERT INTO user_daily_participation (id, user_id, challenge_date, challenge_level, session_id)
            VALUES (:id, :userId, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE, 'EASY', :sessionId)
            """;

    private static final String INSERT_GUEST_LEGAL_EVENT = """
            INSERT INTO legal_acceptance_events (
                id, user_id, acceptance_source, terms_version, privacy_version, accepted_at
            )
            VALUES (900, :userId, 'GUEST_START', '2026-04-21', '2026-04-21', NOW())
            """;

    private static final String CALL_CLAIM = """
            SELECT * FROM claim_guest_today_progress(:guestToken, :targetUserId)
            """;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void cleanData() {
        jdbc.getJdbcTemplate().execute(TRUNCATE_TEST_DATA);
        jdbc.getJdbcTemplate().execute(SEED_COUNTRY);
        jdbc.getJdbcTemplate().execute(SEED_CHALLENGE);
        jdbc.getJdbcTemplate().execute(SET_NO_STREAK_BONUS);
    }

    @Test
    @DisplayName("in-progress guest session is claimed when target has not played today")
    void inProgressGuestSessionIsClaimed() {
        insertRegisteredUser();
        insertGuestUser();
        insertProfile(42L, 50, 2, 2, 1, 0, 1);
        insertProfile(100L, 20, 1, 1, 0, 0, 0);
        insertSession(99L, 100L, "IN_PROGRESS", null, 0, 0, 0, 1);
        insertSessionCountry(990L, 99L, false, 0, 0);
        insertTodayParticipation(500L, 100L, 99L);
        insertGuestLegalEvent();

        Map<String, Object> result = claim("guest-token");

        assertThat(result.get("claim_status")).isEqualTo("CLAIMED_IN_PROGRESS");
        assertSessionOwnerAndBaseline(42L, 50, 2);
        assertParticipationOwner(42L);
        assertProfileTotals(50, 2, 1);
        assertGuestTokenExpired();
        assertLegalEventOwner();
    }

    @Test
    @DisplayName("completed guest session is claimed and awarded to target profile")
    void completedGuestSessionIsClaimedAndAwarded() {
        insertRegisteredUser();
        insertGuestUser();
        insertProfile(42L, 50, 2, 2, 1, 0, 1);
        insertProfile(100L, 130, 2, 1, 1, 1, 1);
        insertSession(99L, 100L, "COMPLETED", Instant.parse("2026-06-15T10:05:00Z"), 130, 30, 0, 1);
        insertSessionCountry(990L, 99L, true, 1, 100);
        insertTodayParticipation(500L, 100L, 99L);

        Map<String, Object> result = claim("guest-token");

        assertThat(result.get("claim_status")).isEqualTo("CLAIMED_COMPLETED");
        assertSessionOwnerAndBaseline(42L, 50, 2);
        assertSessionAward();
        assertParticipationOwner(42L);
        assertProfileTotals(150, 3, 2);
        assertCompletedProjectionClaimedToTarget();
        assertThat(profileValue("current_streak")).isEqualTo(1);
        assertGuestTokenExpired();
    }

    @Test
    @DisplayName("claim is skipped when target already played today")
    void targetAlreadyPlayedSkipsClaim() {
        insertRegisteredUser();
        insertGuestUser();
        insertProfile(42L, 50, 2, 2, 1, 0, 1);
        insertProfile(100L, 20, 1, 1, 0, 0, 0);
        insertSession(98L, 42L, "COMPLETED", Instant.parse("2026-06-15T09:00:00Z"), 80, 0, 50, 2);
        insertSessionCountry(980L, 98L, true, 1, 80);
        insertTodayParticipation(501L, 42L, 98L);
        insertSession(99L, 100L, "IN_PROGRESS", null, 0, 0, 0, 1);
        insertSessionCountry(990L, 99L, false, 0, 0);
        insertTodayParticipation(500L, 100L, 99L);

        Map<String, Object> result = claim("guest-token");

        assertThat(result.get("claim_status")).isEqualTo("TARGET_ALREADY_PLAYED");
        assertSessionOwnerAndBaseline(100L, 0, 1);
        assertParticipationOwner(100L);
        assertThat(guestToken()).isEqualTo("guest-token");
    }

    @Test
    @DisplayName("missing guest token returns NO_GUEST")
    void missingGuestReturnsNoGuest() {
        insertRegisteredUser();
        insertProfile(42L, 50, 2, 2, 1, 0, 1);

        Map<String, Object> result = claim("missing-token");

        assertThat(result.get("claim_status")).isEqualTo("NO_GUEST");
        assertThat(result.get("guest_user_id")).isNull();
    }

    @Test
    @DisplayName("guest without today's participation returns NO_TODAY_PROGRESS")
    void guestWithoutTodayProgressReturnsNoop() {
        insertRegisteredUser();
        insertGuestUser();
        insertProfile(42L, 50, 2, 2, 1, 0, 1);
        insertProfile(100L, 20, 1, 1, 0, 0, 0);

        Map<String, Object> result = claim("guest-token");

        assertThat(result.get("claim_status")).isEqualTo("NO_TODAY_PROGRESS");
        assertThat(((Number) result.get("guest_user_id")).longValue()).isEqualTo(100L);
        assertThat(guestToken()).isEqualTo("guest-token");
    }

    private Map<String, Object> claim(String guestToken) {
        return jdbc.queryForMap(CALL_CLAIM,
                new MapSqlParameterSource()
                        .addValue("guestToken", guestToken)
                        .addValue("targetUserId", 42L));
    }

    private void insertRegisteredUser() {
        jdbc.update(INSERT_REGISTERED_USER, new MapSqlParameterSource()
                .addValue("id", 42L)
                .addValue("email", "target@example.com")
                .addValue("username", "target"));
    }

    private void insertGuestUser() {
        jdbc.update(INSERT_GUEST_USER, new MapSqlParameterSource()
                .addValue("id", 100L)
                .addValue("guestToken", "guest-token"));
    }

    private void insertProfile(long userId, long totalXp, int level, int played, int won, int streak, int bestStreak) {
        jdbc.update(INSERT_PROFILE, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("totalXp", totalXp)
                .addValue("level", level)
                .addValue("played", played)
                .addValue("won", won)
                .addValue("streak", streak)
                .addValue("bestStreak", bestStreak));
    }

    private void insertSession(
            long sessionId,
            long userId,
            String status,
            Instant completedAt,
            int totalXp,
            int streakBonusXp,
            long xpBefore,
            int levelBefore) {
        Instant startedAt = Instant.parse("2026-06-15T10:00:00Z");
        Instant lastActivityAt = completedAt != null ? completedAt : startedAt;
        jdbc.update(INSERT_SESSION, new MapSqlParameterSource()
                .addValue("id", sessionId)
                .addValue("userId", userId)
                .addValue("status", status)
                .addValue("startedAt", Timestamp.from(startedAt))
                .addValue("lastActivityAt", Timestamp.from(lastActivityAt))
                .addValue("completedAt", completedAt != null ? Timestamp.from(completedAt) : null)
                .addValue("totalXp", totalXp)
                .addValue("streakBonusPercent", streakBonusXp > 0 ? 10.0 : 0.0)
                .addValue("streakBonusXp", streakBonusXp)
                .addValue("xpBefore", xpBefore)
                .addValue("levelBefore", levelBefore));
    }

    private void insertSessionCountry(long id, long sessionId, boolean guessed, int attemptsUsed, int xpEarned) {
        jdbc.update(INSERT_SESSION_COUNTRY, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("sessionId", sessionId)
                .addValue("guessed", guessed)
                .addValue("attemptsUsed", attemptsUsed)
                .addValue("xpEarned", xpEarned));
    }

    private void insertTodayParticipation(long id, long userId, long sessionId) {
        jdbc.update(INSERT_TODAY_PARTICIPATION, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("sessionId", sessionId));
    }

    private void insertGuestLegalEvent() {
        jdbc.update(INSERT_GUEST_LEGAL_EVENT, new MapSqlParameterSource().addValue("userId", 100L));
    }

    private void assertSessionOwnerAndBaseline(long userId, long xpBefore, int levelBefore) {
        Map<String, Object> session = session();
        assertThat(((Number) session.get("user_id")).longValue()).isEqualTo(userId);
        assertThat(((Number) session.get("xp_before")).longValue()).isEqualTo(xpBefore);
        assertThat(((Number) session.get("level_before")).intValue()).isEqualTo(levelBefore);
    }

    private void assertSessionAward() {
        Map<String, Object> session = session();
        assertThat(((Number) session.get("total_xp_earned")).intValue()).isEqualTo(100);
        assertThat(((Number) session.get("streak_bonus_xp")).intValue()).isEqualTo(0);
    }

    private void assertParticipationOwner(long userId) {
        Long owner = jdbc.queryForObject(
                "SELECT user_id FROM user_daily_participation WHERE session_id = :sessionId",
                new MapSqlParameterSource("sessionId", 99L),
                Long.class);
        assertThat(owner).isEqualTo(userId);
    }

    private void assertProfileTotals(long totalXp, int played, int won) {
        assertThat(profileValue("total_xp")).isEqualTo(totalXp);
        assertThat(profileValue("challenges_played")).isEqualTo(played);
        assertThat(profileValue("challenges_won")).isEqualTo(won);
    }

    private void assertCompletedProjectionClaimedToTarget() {
        assertThat(profileValue("countries_played")).isEqualTo(1);
        assertThat(profileValue("countries_solved")).isEqualTo(1);
        assertThat(profileValue("perfect_runs")).isEqualTo(1);
        assertThat(profileStringValue()).isEqualTo("EASY");
        assertThat(solutionCount(42L)).isEqualTo(1);
        assertThat(solutionCount(100L)).isZero();
        assertThat(session().get("profile_projection_applied_at")).isNotNull();
    }

    private void assertGuestTokenExpired() {
        Map<String, Object> user = user();
        assertThat(user.get("guest_token")).isNull();
        assertThat((Timestamp) user.get("guest_token_expires_at")).isBefore(new Timestamp(System.currentTimeMillis()));
    }

    private void assertLegalEventOwner() {
        Long owner = jdbc.queryForObject(
                "SELECT user_id FROM legal_acceptance_events WHERE id = :id",
                new MapSqlParameterSource("id", 900L),
                Long.class);
        assertThat(owner).isEqualTo(42L);
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

    private long solutionCount(long userId) {
        Number value = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM user_country_solutions
                        WHERE user_id = :userId
                          AND source_session_id = :sessionId
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("sessionId", 99L),
                Number.class);
        assert value != null;
        return value.longValue();
    }

    private String guestToken() {
        return jdbc.queryForObject(
                "SELECT guest_token FROM users WHERE id = :userId",
                new MapSqlParameterSource("userId", 100L),
                String.class);
    }

    private Map<String, Object> session() {
        return jdbc.queryForMap(
                "SELECT * FROM game_sessions WHERE id = :sessionId",
                new MapSqlParameterSource("sessionId", 99L));
    }

    private Map<String, Object> user() {
        return jdbc.queryForMap(
                "SELECT * FROM users WHERE id = :userId",
                new MapSqlParameterSource("userId", 100L));
    }
}
