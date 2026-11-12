package io.clueterra.api.game.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

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
class StreakResetIntegrationTest {

    private static final String CLUETERRA = "CLUETERRA";
    private static final String BUDGET = "BUDGET";

    private static final String TRUNCATE_TEST_DATA =
            "TRUNCATE cl_api_schema.users, cl_api_schema.user_profiles, cl_api_schema.game_sessions CASCADE";

    private static final String INSERT_USER =
            "INSERT INTO users (id, username) VALUES (:id, :username)";

    private static final String INSERT_PROFILE = """
            INSERT INTO user_profiles (user_id, current_streak, best_streak, streak_freezes)
            VALUES (:userId, :streak, :streak, :freezes)
            """;

    private static final String INSERT_ACTIVITY = """
            INSERT INTO user_daily_activity (user_id, activity_date, game_code)
            VALUES (:userId, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE - :daysAgo, :gameCode)
            """;

    private static final String CALL_RESET =
            "SELECT * FROM reset_expired_streaks()";

    private static final String CALL_AWARD_XP = """
            SELECT * FROM award_session_xp(
                :userId, 100, :won, 100, 50, 365, 0.4, 10, 5
            )
            """;

    private static final String CALL_RECORD_ACTIVITY = """
            SELECT * FROM record_daily_streak_activity(
                :userId, :gameCode, 50, 365, 0.4, 10, 5
            )
            """;

    private static final String SELECT_PROFILE =
            "SELECT * FROM user_profiles WHERE user_id = :userId";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void cleanData() {
        jdbc.getJdbcTemplate().execute(TRUNCATE_TEST_DATA);
    }

    @Test
    @DisplayName("User with shield: freeze consumed, streak preserved; User without shield: streak reset")
    void shieldProtectsStreak_noShieldResetsStreak() {
        long shieldedUserId = 1L;
        long unshieldedUserId = 2L;

        insertUser(shieldedUserId, "shielded");
        insertUser(unshieldedUserId, "unshielded");
        insertProfile(shieldedUserId, 15, 2);
        insertProfile(unshieldedUserId, 10, 0);

        Map<String, Object> result = callResetExpiredStreaks();
        int freezesConsumed = ((Number) result.get("freezes_consumed")).intValue();
        int streaksReset = ((Number) result.get("streaks_reset")).intValue();

        assertThat(freezesConsumed).isEqualTo(1);
        assertThat(streaksReset).isEqualTo(1);

        Map<String, Object> shielded = getProfile(shieldedUserId);
        assertThat(((Number) shielded.get("current_streak")).intValue()).isEqualTo(15);
        assertThat(((Number) shielded.get("streak_freezes")).intValue()).isEqualTo(1);
        assertThat(((Number) shielded.get("streak_freezes_used_since_completion")).intValue()).isEqualTo(1);
        assertThat(((Number) shielded.get("streak_freezes_acknowledged_since_completion")).intValue()).isZero();
        assertThat(shielded.get("streak_freeze_used_at")).isNotNull();

        Map<String, Object> unshielded = getProfile(unshieldedUserId);
        assertThat(((Number) unshielded.get("current_streak")).intValue()).isZero();
        assertThat(((Number) unshielded.get("streak_freezes")).intValue()).isZero();
        assertThat(((Number) unshielded.get("streak_freezes_used_since_completion")).intValue()).isZero();
        assertThat(((Number) unshielded.get("streak_freezes_acknowledged_since_completion")).intValue()).isZero();
    }

    @Test
    @DisplayName("A daily game in any registered game protects the streak from the reset")
    void activeUserIsSkipped() {
        long clueterraUserId = 3L;
        long budgetUserId = 4L;
        long inactiveUserId = 5L;

        insertUser(clueterraUserId, "clueterra");
        insertUser(budgetUserId, "budget");
        insertUser(inactiveUserId, "inactive");
        insertProfile(clueterraUserId, 5, 0);
        insertProfile(budgetUserId, 5, 0);
        insertProfile(inactiveUserId, 5, 0);
        insertActivity(clueterraUserId, 0, CLUETERRA);
        insertActivity(budgetUserId, 1, BUDGET);
        insertActivity(inactiveUserId, 2, BUDGET);

        Map<String, Object> result = callResetExpiredStreaks();

        assertThat(((Number) result.get("streaks_reset")).intValue()).isEqualTo(1);
        assertThat(getStreak(clueterraUserId)).isEqualTo(5);
        assertThat(getStreak(budgetUserId)).isEqualTo(5);
        assertThat(getStreak(inactiveUserId)).isZero();
    }

    @Test
    @DisplayName("Multiple freezes: only one consumed per reset cycle, even if the reset runs twice")
    void onlyOneFreeze_consumedPerCycle() {
        long userId = 6L;
        insertUser(userId, "multishield");
        insertProfile(userId, 30, 3);

        callResetExpiredStreaks();
        Map<String, Object> rerun = callResetExpiredStreaks();

        Map<String, Object> profile = getProfile(userId);
        assertThat(((Number) rerun.get("freezes_consumed")).intValue()).isZero();
        assertThat(((Number) profile.get("current_streak")).intValue()).isEqualTo(30);
        assertThat(((Number) profile.get("streak_freezes")).intValue()).isEqualTo(2);
        assertThat(((Number) profile.get("streak_freezes_used_since_completion")).intValue()).isEqualTo(1);
        assertThat(((Number) profile.get("streak_freezes_acknowledged_since_completion")).intValue()).isZero();
    }

    @Test
    @DisplayName("Zero streak users are not affected")
    void zeroStreakUsersIgnored() {
        long userId = 7L;
        insertUser(userId, "nostreak");
        insertProfile(userId, 0, 2);

        Map<String, Object> result = callResetExpiredStreaks();

        assertThat(((Number) result.get("freezes_consumed")).intValue()).isZero();
        assertThat(((Number) result.get("streaks_reset")).intValue()).isZero();
        assertThat(((Number) getProfile(userId).get("streak_freezes")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("Winning after today's consumed freeze continues the preserved streak")
    void awardXpContinuesStreakAfterFreezeUsedToday() {
        long userId = 8L;
        insertUser(userId, "continued");
        insertProfile(userId, 15, 1);
        markFreezeUsedNow(userId);

        Map<String, Object> result = awardXp(userId, true);

        assertThat(((Number) result.get("new_streak")).intValue()).isEqualTo(16);
        assertThat(((Number) getProfile(userId).get("current_streak")).intValue()).isEqualTo(16);
        assertThat(((Number) getProfile(userId).get("streak_freezes_used_since_completion")).intValue()).isZero();
        assertThat(((Number) getProfile(userId).get("streak_freezes_acknowledged_since_completion")).intValue()).isZero();
    }

    @Test
    @DisplayName("A day without any daily game starts the streak again")
    void awardXpStartsStreakWithoutYesterdayActivity() {
        long userId = 9L;
        insertUser(userId, "restarted");
        insertProfile(userId, 5, 0);
        insertActivity(userId, 2, CLUETERRA);

        Map<String, Object> result = awardXp(userId, true);

        assertThat(((Number) result.get("new_streak")).intValue()).isEqualTo(1);
        assertThat(getStreak(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Budget yesterday continues the streak in Clueterra today, and a loss keeps it")
    void budgetActivityContinuesClueterraStreak() {
        long userId = 10L;
        insertUser(userId, "crossgame");
        insertProfile(userId, 4, 0);
        insertActivity(userId, 1, BUDGET);

        Map<String, Object> result = awardXp(userId, false);

        assertThat(((Number) result.get("new_streak")).intValue()).isEqualTo(5);
        assertThat(((Number) getProfile(userId).get("challenges_won")).intValue()).isZero();
    }

    @Test
    @DisplayName("Playing a second game on the same day keeps the streak and gives the same bonus")
    void secondGameSameDayDoesNotAdvanceStreak() {
        long userId = 11L;
        insertUser(userId, "bothgames");
        insertProfile(userId, 9, 0);
        insertActivity(userId, 1, CLUETERRA);

        Map<String, Object> first = recordActivity(userId, BUDGET);
        Map<String, Object> second = awardXp(userId, true);

        assertThat(((Number) first.get("new_streak")).intValue()).isEqualTo(10);
        assertThat(((Number) first.get("freezes_earned")).intValue()).isEqualTo(1);
        assertThat(((Number) second.get("new_streak")).intValue()).isEqualTo(10);
        assertThat(((Number) second.get("freezes_earned")).intValue()).isZero();
        assertThat(((Number) second.get("streak_bonus_percent")).doubleValue())
                .isEqualTo(((Number) first.get("streak_bonus_percent")).doubleValue());
        assertThat(((Number) getProfile(userId).get("streak_freezes")).intValue()).isEqualTo(1);
    }

    private void insertUser(long id, String username) {
        jdbc.update(INSERT_USER, new MapSqlParameterSource().addValue("id", id).addValue("username", username));
    }

    private void insertProfile(long userId, int streak, int freezes) {
        jdbc.update(INSERT_PROFILE, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("streak", streak)
                .addValue("freezes", freezes));
    }

    private void insertActivity(long userId, int daysAgo, String gameCode) {
        jdbc.update(INSERT_ACTIVITY, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("daysAgo", daysAgo)
                .addValue("gameCode", gameCode));
    }

    private void markFreezeUsedNow(long userId) {
        jdbc.update(
                """
                        UPDATE user_profiles
                        SET streak_freeze_used_at = NOW(),
                            streak_freezes_used_since_completion = 1,
                            streak_freezes_acknowledged_since_completion = 1
                        WHERE user_id = :userId
                        """,
                new MapSqlParameterSource().addValue("userId", userId));
    }

    private Map<String, Object> awardXp(long userId, boolean won) {
        return jdbc.queryForMap(CALL_AWARD_XP, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("won", won));
    }

    private Map<String, Object> recordActivity(long userId, String gameCode) {
        return jdbc.queryForMap(CALL_RECORD_ACTIVITY, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("gameCode", gameCode));
    }

    private Map<String, Object> callResetExpiredStreaks() {
        return jdbc.queryForMap(CALL_RESET, Map.of());
    }

    private Map<String, Object> getProfile(long userId) {
        return jdbc.queryForMap(SELECT_PROFILE, new MapSqlParameterSource().addValue("userId", userId));
    }

    private int getStreak(long userId) {
        return ((Number) getProfile(userId).get("current_streak")).intValue();
    }
}
