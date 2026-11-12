package io.clueterra.api.rivergame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.api.achievements.engine.BadgeEngineJdbcRepository;
import io.clueterra.api.achievements.engine.BadgeEvaluationService;
import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.admin.users.repository.AdminUserJdbcRepository;
import io.clueterra.api.profile.repository.ProfileBadgeProgressJdbcRepository;
import io.clueterra.api.exception.GameSqlState;
import io.clueterra.api.rivergame.repository.RiverGameJdbcRepository;
import io.clueterra.api.rivergame.dto.RiverPlayerStatsDto;
import io.clueterra.api.rivergame.model.RiverQuestionRow;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import io.clueterra.api.TestcontainersConfiguration;

@JdbcTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=cl_api_schema",
        "spring.flyway.default-schema=cl_api_schema",
        "spring.flyway.create-schemas=true",
        "spring.datasource.hikari.connection-init-sql=SET search_path TO cl_api_schema, cl_schema"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RiverGameProgressIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_api_schema.users, cl_schema.river_game_question CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users (id, email, username, password_hash, role, enabled, is_guest)
                VALUES (1, 'river@example.com', 'river_player', 'hash', 'USER', TRUE, FALSE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.river_game_question
                    (id, country_id, river_key, name_en, answer_aliases, rank_in_country, is_active)
                VALUES (101, 10, 'q584', 'Rhine', ARRAY['Rhein'], 1, TRUE),
                       (102, 10, 'q1653', 'Danube', ARRAY['Donau'], 2, TRUE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.river_game_hint (question_id, position, hint_text, place)
                VALUES (101, 1, 'North Sea', NULL),
                       (101, 2, 'Cologne lies on its banks.', '{"name":"Cologne","lng":6.96,"lat":50.94}'),
                       (101, 3, 'R, 5 letters', NULL),
                       (102, 1, 'Black Sea', NULL),
                       (102, 2, 'Vienna lies on its banks.', '{"name":"Vienna","lng":16.37,"lat":48.21}'),
                       (102, 3, 'D, 6 letters', NULL)
                """);
    }

    @Test
    void revealedCityClueCarriesItsPlace() throws Exception {
        long questionId = nextQuestion();
        RiverGameJdbcRepository repository = new RiverGameJdbcRepository(jdbc);
        repository.revealHint(1L, questionId);
        repository.revealHint(1L, questionId);
        RiverQuestionRow current = repository.findCurrent(1L).orElseThrow();
        JsonNode hints = new ObjectMapper().readTree(current.revealedHints());
        assertThat(hints).hasSize(2);
        assertThat(hints.get(0).get("position").asInt()).isEqualTo(1);
        assertThat(hints.get(0).get("place").isNull()).isTrue();
        assertThat(hints.get(1).get("place").get("name").asText())
                .isEqualTo(questionId == 101L ? "Cologne" : "Vienna");
        assertThat(current.baseXp()).isPositive();
        assertThat(current.hintPenaltyPercent()).isPositive();
    }

    @Test
    void actionsOnAFinishedRiverAreConflicts() {
        long questionId = nextQuestion();
        RiverGameJdbcRepository repository = new RiverGameJdbcRepository(jdbc);
        repository.showAnswer(1L, questionId);

        assertThatThrownBy(() -> repository.showAnswer(1L, questionId))
                .isInstanceOfSatisfying(DataAccessException.class,
                        error -> assertThat(GameSqlState.from(error)).contains(GameSqlState.ROUND_NOT_CURRENT));
    }

    @Test
    void resumeRevealGuessAndAwardXpOnce() {
        long questionId = nextQuestion();
        assertThat(nextQuestion()).isEqualTo(questionId);
        assertThat(new RiverGameJdbcRepository(jdbc).findCurrent(1L).orElseThrow().course())
                .contains("\"coordinates\"");
        assertThat(jdbc.queryForObject(
                "SELECT cl_api_schema.river_game_reveal_hint(:userId, :questionId)",
                Map.of("userId", 1L, "questionId", questionId), Short.class)).isEqualTo((short) 1);

        String answer = questionId == 101L ? "Rhein" : "Donau";
        Map<String, Object> wrong = guess(questionId, "Not a river");
        assertThat(wrong.get("correct")).isEqualTo(false);
        assertThat(wrong.get("resolved_name")).isNull();
        assertThat(jdbc.queryForMap(
                "SELECT attempted_rivers, solved_rivers FROM cl_api_schema.user_river_stats WHERE user_id = 1",
                Map.of())).containsEntry("attempted_rivers", 1L).containsEntry("solved_rivers", 0L);

        Map<String, Object> solved = guess(questionId, answer);
        assertThat(solved.get("correct")).isEqualTo(true);
        assertThat(solved.get("xp_awarded")).isEqualTo(170);
        assertThat(jdbc.queryForObject(
                "SELECT total_xp FROM cl_api_schema.user_profiles WHERE user_id = 1",
                Map.of(), Long.class)).isEqualTo(170L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cl_api_schema.user_xp_events WHERE user_id = 1 AND source_type = 'RIVER_GAME'",
                Map.of(), Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap(
                "SELECT attempted_rivers, solved_rivers, answer_attempts "
                        + "FROM cl_api_schema.user_river_stats WHERE user_id = 1",
                Map.of())).containsEntry("attempted_rivers", 1L)
                .containsEntry("solved_rivers", 1L).containsEntry("answer_attempts", 2L);
        assertThat(new BadgeEngineJdbcRepository(jdbc).loadUserStats(1L).riverAttempted()).isEqualTo(1);
        assertThat(new ProfileBadgeProgressJdbcRepository(jdbc).loadUserStats(1L).riverSolved()).isEqualTo(1);
        assertThat(new RiverGameJdbcRepository(jdbc).findPlayerStats(1L).answerAttempts()).isEqualTo(2);
        assertThat(nextQuestion()).isNotEqualTo(questionId);
    }

    @Test
    void skipWithoutGuessDoesNotCountAsAttempt() {
        long questionId = nextQuestion();
        jdbc.queryForObject("SELECT cl_api_schema.river_game_skip(:userId, :questionId)",
                Map.of("userId", 1L, "questionId", questionId), Long.class);

        assertThat(jdbc.queryForObject("""
                SELECT attempt_count FROM cl_api_schema.river_game_progress
                WHERE user_id = 1 AND question_id = :questionId
                """, Map.of("questionId", questionId), Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT skip_count FROM cl_api_schema.river_game_progress
                WHERE user_id = 1 AND question_id = :questionId
                """, Map.of("questionId", questionId), Integer.class)).isEqualTo(1);
    }

    @Test
    void playerStatsUseDistinctRiversAndDoNotCountUnattemptedSkips() {
        RiverGameJdbcRepository repository = new RiverGameJdbcRepository(jdbc);
        assertThat(repository.findPlayerStats(1L)).isEqualTo(new RiverPlayerStatsDto(0, 0, 0, 0, 0, null));

        long questionId = nextQuestion();
        guess(questionId, "Incorrect answer");
        guess(questionId, "Still incorrect");
        assertThat(repository.findPlayerStats(1L)).extracting(
                RiverPlayerStatsDto::attemptedRivers, RiverPlayerStatsDto::solvedRivers,
                RiverPlayerStatsDto::answerAttempts)
                .containsExactly(1L, 0L, 2L);

        jdbc.queryForObject("SELECT cl_api_schema.river_game_skip(:userId, :questionId)",
                Map.of("userId", 1L, "questionId", questionId), Long.class);
        assertThat(repository.findPlayerStats(1L)).extracting(
                RiverPlayerStatsDto::attemptedRivers, RiverPlayerStatsDto::solvedRivers,
                RiverPlayerStatsDto::skips)
                .containsExactly(1L, 0L, 1L);
    }

    @Test
    void adminUsersIncludeRiverStatsAndFilterByDistinctAttemptedRivers() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users (id, email, username, password_hash, role, enabled, is_guest)
                VALUES (2, 'new@example.com', 'new_player', 'hash', 'USER', TRUE, FALSE)
                """);
        long questionId = nextQuestion();
        guess(questionId, "Incorrect answer");

        AdminUserJdbcRepository users = new AdminUserJdbcRepository(jdbc);
        assertThat(users.findById(1L).orElseThrow().riverStats().attemptedRivers()).isEqualTo(1);
        assertThat(users.findById(1L).orElseThrow().riverStats().answerAttempts()).isEqualTo(1);
        assertThat(users.findById(2L).orElseThrow().riverStats().attemptedRivers()).isZero();

        var played = users.findAll(null, null, null, null, false, 1, null,
                "riversPlayed", "desc", PageRequest.of(0, 20), false);
        assertThat(played.getTotalElements()).isEqualTo(1);
        assertThat(played.getContent()).extracting(user -> user.id()).containsExactly(1L);

        var notPlayed = users.findAll(null, null, null, null, false, null, 0,
                "riversPlayed", "asc", PageRequest.of(0, 20), false);
        assertThat(notPlayed.getTotalElements()).isEqualTo(1);
        assertThat(notPlayed.getContent()).extracting(user -> user.id()).containsExactly(2L);
    }

    @Test
    void transboundaryRiverAppearsAfterNewRiversAndNeverAwardsXpTwice() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.river_game_question
                    (id, country_id, river_key, name_en, rank_in_country, is_active)
                VALUES (103, 11, 'q584', 'Rhine', 1, TRUE)
                """);

        nextQuestion();
        jdbc.getJdbcTemplate().execute("""
                UPDATE cl_api_schema.river_game_player_state
                SET current_question_id = 101 WHERE user_id = 1
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress (user_id, question_id)
                VALUES (1, 101) ON CONFLICT DO NOTHING
                """);
        assertThat(guess(101, "Rhine").get("xp_awarded")).isEqualTo(200);
        assertThat(nextQuestion()).isEqualTo(102L);

        jdbc.getJdbcTemplate().execute("""
                UPDATE cl_api_schema.river_game_player_state
                SET current_question_id = 103 WHERE user_id = 1
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress (user_id, question_id)
                VALUES (1, 103) ON CONFLICT DO NOTHING
                """);
        assertThat(guess(103, "Rhine").get("xp_awarded")).isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cl_api_schema.user_xp_events "
                        + "WHERE user_id = 1 AND source_type = 'RIVER_GAME'",
                Map.of(), Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap(
                "SELECT attempted_rivers, solved_rivers FROM cl_api_schema.user_river_stats WHERE user_id = 1",
                Map.of())).containsEntry("attempted_rivers", 1L).containsEntry("solved_rivers", 1L);
    }

    @Test
    void guestClaimCombinesDistinctSolutionsWithoutDuplicatingXp() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'river_guest', 'USER', TRUE, TRUE, 'river-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress
                    (user_id, question_id, first_attempt_at, first_solved_at,
                     attempt_count, solve_count, best_hints_used, xp_awarded)
                VALUES (1, 101, NOW(), NOW(), 1, 1, 0, 200),
                       (2, 101, NOW(), NOW(), 1, 1, 1, 170),
                       (2, 102, NOW(), NOW(), 1, 1, 0, 200)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.user_profiles (user_id, total_xp, current_level)
                VALUES (1, 200, 2), (2, 370, 2)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_player_state (user_id, current_question_id)
                VALUES (1, NULL), (2, NULL)
                """);

        Boolean claimed = jdbc.queryForObject(
                "SELECT cl_api_schema.river_game_claim_guest(:token, :target)",
                Map.of("token", "river-token", "target", 1L), Boolean.class);

        assertThat(claimed).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cl_api_schema.river_game_progress WHERE user_id = 1",
                Map.of(), Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cl_api_schema.river_game_progress WHERE user_id = 2",
                Map.of(), Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT total_xp FROM cl_api_schema.user_profiles WHERE user_id = 1",
                Map.of(), Long.class)).isEqualTo(400L);
        assertThat(jdbc.queryForObject(
                "SELECT total_xp FROM cl_api_schema.user_profiles WHERE user_id = 2",
                Map.of(), Long.class)).isZero();
        assertThat(jdbc.queryForMap(
                "SELECT attempted_rivers, solved_rivers FROM cl_api_schema.user_river_stats WHERE user_id = 1",
                Map.of())).containsEntry("attempted_rivers", 2L).containsEntry("solved_rivers", 2L);
    }

    @Test
    void guestClaimReplacesCurrentQuestionWhenSameRiverWasSolvedElsewhere() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'river_guest', 'USER', TRUE, TRUE, 'river-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.river_game_question
                    (id, country_id, river_key, name_en, rank_in_country, is_active)
                VALUES (103, 11, 'q584', 'Rhine', 1, TRUE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress
                    (user_id, question_id, first_attempt_at, first_solved_at,
                     attempt_count, solve_count, best_hints_used, xp_awarded)
                VALUES (2, 103, NOW(), NOW(), 1, 1, 0, 200)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress (user_id, question_id)
                VALUES (1, 101)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_player_state (user_id, current_question_id)
                VALUES (1, 101)
                """);

        assertThat(jdbc.queryForObject(
                "SELECT cl_api_schema.river_game_claim_guest(:token, :target)",
                Map.of("token", "river-token", "target", 1L), Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT current_question_id FROM cl_api_schema.river_game_player_state WHERE user_id = 1",
                Map.of(), Long.class)).isEqualTo(102L);
    }

    @Test
    void guestClaimPreservesRevealedHintsAndRemainingSkipDelayAcrossDifferentRounds() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'river_guest', 'USER', TRUE, TRUE, 'river-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress
                    (user_id, question_id, deferred_until_round)
                VALUES (1, 101, 0), (1, 102, 22), (2, 102, 8)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_player_state
                    (user_id, current_question_id, current_hints_used, round_number)
                VALUES (1, 101, 1, 20), (2, 102, 2, 5)
                """);

        assertThat(jdbc.queryForObject(
                "SELECT cl_api_schema.river_game_claim_guest(:token, :target)",
                Map.of("token", "river-token", "target", 1L), Boolean.class)).isTrue();
        assertThat(jdbc.queryForMap("""
                SELECT current_question_id, current_hints_used, round_number
                FROM cl_api_schema.river_game_player_state WHERE user_id = 1
                """, Map.of()))
                .containsEntry("current_question_id", 101L)
                .containsEntry("current_hints_used", 1)
                .containsEntry("round_number", 20L);
        assertThat(jdbc.queryForMap("""
                SELECT hints_revealed, deferred_until_round
                FROM cl_api_schema.river_game_progress
                WHERE user_id = 1 AND question_id = 102
                """, Map.of()))
                .containsEntry("hints_revealed", 2)
                .containsEntry("deferred_until_round", 23L);

        guess(101L, "Rhine");
        assertThat(nextQuestion()).isEqualTo(102L);
        assertThat(jdbc.queryForObject("""
                SELECT current_hints_used FROM cl_api_schema.river_game_player_state WHERE user_id = 1
                """, Map.of(), Short.class)).isEqualTo((short) 2);
        assertThat(guess(102L, "Danube").get("xp_awarded")).isEqualTo(140);
    }

    @Test
    void guestClaimCombinesHintsAndCurrentAttemptsForTheSameQuestion() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'river_guest', 'USER', TRUE, TRUE, 'river-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress
                    (user_id, question_id, first_attempt_at, attempt_count)
                VALUES (1, 101, NOW(), 2), (2, 101, NOW(), 1)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_player_state
                    (user_id, current_question_id, current_hints_used, current_attempts, round_number)
                VALUES (1, 101, 1, 2, 3), (2, 101, 2, 1, 4)
                """);

        assertThat(jdbc.queryForObject(
                "SELECT cl_api_schema.river_game_claim_guest(:token, :target)",
                Map.of("token", "river-token", "target", 1L), Boolean.class)).isTrue();
        assertThat(jdbc.queryForMap("""
                SELECT current_hints_used, current_attempts
                FROM cl_api_schema.river_game_player_state WHERE user_id = 1
                """, Map.of()))
                .containsEntry("current_hints_used", 2)
                .containsEntry("current_attempts", 3);
        assertThat(guess(101L, "Rhine").get("xp_awarded")).isEqualTo(140);
    }

    @Test
    void guestClaimKeepsTargetSkipDelayWhenGuestIsFurtherAhead() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'river_guest', 'USER', TRUE, TRUE, 'river-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress
                    (user_id, question_id, deferred_until_round)
                VALUES (1, 102, 8), (2, 101, 0)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_player_state
                    (user_id, current_question_id, round_number)
                VALUES (1, NULL, 5), (2, 101, 20)
                """);

        assertThat(jdbc.queryForObject(
                "SELECT cl_api_schema.river_game_claim_guest(:token, :target)",
                Map.of("token", "river-token", "target", 1L), Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT deferred_until_round FROM cl_api_schema.river_game_progress
                WHERE user_id = 1 AND question_id = 102
                """, Map.of(), Long.class)).isEqualTo(23L);
        assertThat(jdbc.queryForObject("""
                SELECT current_question_id FROM cl_api_schema.river_game_player_state
                WHERE user_id = 1
                """, Map.of(), Long.class)).isEqualTo(101L);
    }

    @Test
    void guestClaimKeepsHintPenaltyAcrossCountryVariantsOfOneRiver() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'river_guest', 'USER', TRUE, TRUE, 'river-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.river_game_question
                    (id, country_id, river_key, name_en, rank_in_country, is_active)
                VALUES (103, 11, 'q584', 'Rhine', 1, TRUE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress (user_id, question_id)
                VALUES (1, 101), (2, 103)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_player_state
                    (user_id, current_question_id, current_hints_used)
                VALUES (1, 101, 0), (2, 103, 2)
                """);

        assertThat(jdbc.queryForObject(
                "SELECT cl_api_schema.river_game_claim_guest(:token, :target)",
                Map.of("token", "river-token", "target", 1L), Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT current_hints_used FROM cl_api_schema.river_game_player_state WHERE user_id = 1
                """, Map.of(), Short.class)).isEqualTo((short) 2);
        assertThat(guess(101L, "Rhine").get("xp_awarded")).isEqualTo(140);
    }

    @Test
    void guestClaimDoesNotRecordUntransferredXpForTheSameRiverInAnotherCountry() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'river_guest', 'USER', TRUE, TRUE, 'river-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.river_game_question
                    (id, country_id, river_key, name_en, rank_in_country, is_active)
                VALUES (103, 11, 'q584', 'Rhine', 1, TRUE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.river_game_progress
                    (user_id, question_id, first_attempt_at, first_solved_at,
                     attempt_count, solve_count, best_hints_used, xp_awarded)
                VALUES (1, 101, NOW(), NOW(), 1, 1, 0, 200),
                       (2, 103, NOW(), NOW(), 1, 1, 1, 170)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.user_profiles (user_id, total_xp, current_level)
                VALUES (1, 200, 2), (2, 170, 2)
                """);
        assertThat(jdbc.queryForObject("""
                SELECT cl_api_schema.record_user_xp_event(-101, 1, NULL, 200,
                    'RIVER_GAME', 584, NULL, NOW())
                """, Map.of(), Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT cl_api_schema.record_user_xp_event(-102, 2, NULL, 170,
                    'RIVER_GAME', 584, NULL, NOW())
                """, Map.of(), Boolean.class)).isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT cl_api_schema.river_game_claim_guest(:token, :target)",
                Map.of("token", "river-token", "target", 1L), Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT xp_awarded FROM cl_api_schema.river_game_progress
                WHERE user_id = 1 AND question_id = 103
                """, Map.of(), Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT total_xp FROM cl_api_schema.user_profiles WHERE user_id = 1
                """, Map.of(), Long.class)).isEqualTo(200L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM cl_api_schema.user_xp_events
                WHERE user_id = 1 AND source_type = 'RIVER_GAME'
                """, Map.of(), Integer.class)).isEqualTo(1);
    }

    @Test
    void scheduledRiverBadgeEvaluationReadsTheSharedStatsView() {
        jdbc.getJdbcTemplate().execute("UPDATE cl_api_schema.badges SET is_active = FALSE");
        jdbc.getJdbcTemplate().execute("""
                UPDATE cl_api_schema.badges
                SET is_active = TRUE,
                    grant_config = '{"trigger":"RIVER_UNIQUE_SOLVED","threshold":1}'::JSONB
                WHERE slug = 'river-5-guessed'
                """);
        long questionId = nextQuestion();
        guess(questionId, questionId == 101L ? "Rhine" : "Danube");

        BadgeEngineJdbcRepository badgeRepository = new BadgeEngineJdbcRepository(jdbc);
        BadgeEvaluationService evaluation = new BadgeEvaluationService(
                badgeRepository, jdbc, new ObjectMapper(), mock(SettingsService.class));
        evaluation.evaluateTemplateBadgesInBatch("NIGHTLY");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM cl_api_schema.user_badges granted
                JOIN cl_api_schema.badges badge ON badge.id = granted.badge_id
                WHERE granted.user_id = 1 AND badge.slug = 'river-5-guessed'
                """, Map.of(), Integer.class)).isEqualTo(1);
    }

    @Test
    void riverBadgeMigrationSeedsDistinctGuessedAndExploredMilestones() {
        assertThat(jdbc.query("""
                SELECT badge.slug, badge.grant_config ->> 'trigger' AS trigger,
                       (badge.grant_config ->> 'threshold')::INTEGER AS threshold
                FROM cl_api_schema.badges badge
                JOIN cl_api_schema.badge_categories category ON category.id = badge.category_id
                WHERE category.name = 'Rivers' AND badge.is_active
                ORDER BY badge.display_order
                """, Map.of(), (rs, rowNum) -> List.of(
                rs.getString("slug"), rs.getString("trigger"), rs.getString("threshold"))))
                .containsExactly(
                        List.of("river-5-guessed", "RIVER_UNIQUE_SOLVED", "5"),
                        List.of("river-50-guessed", "RIVER_UNIQUE_SOLVED", "50"),
                        List.of("river-100-guessed", "RIVER_UNIQUE_SOLVED", "100"),
                        List.of("river-50-explored", "RIVER_UNIQUE_ATTEMPTED", "50"),
                        List.of("river-100-explored", "RIVER_UNIQUE_ATTEMPTED", "100"),
                        List.of("river-250-explored", "RIVER_UNIQUE_ATTEMPTED", "250"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM cl_api_schema.badge_translations translation
                JOIN cl_api_schema.badges badge ON badge.id = translation.badge_id
                JOIN cl_api_schema.badge_categories category ON category.id = badge.category_id
                WHERE category.name = 'Rivers' AND translation.locale IN ('en', 'ru', 'cs')
                """, Map.of(), Integer.class)).isEqualTo(18);
    }

    @Test
    void showAnswerDefersTheRiverWithoutCountingAnAttempt() {
        long questionId = nextQuestion();
        RiverGameJdbcRepository repository = new RiverGameJdbcRepository(jdbc);

        repository.showAnswer(1L, questionId);

        assertThat(jdbc.queryForMap("""
                SELECT attempt_count, reveal_count, solve_count, deferred_until_round
                FROM cl_api_schema.river_game_progress WHERE user_id = 1 AND question_id = :questionId
                """, Map.of("questionId", questionId)))
                .containsEntry("attempt_count", 0).containsEntry("reveal_count", 1)
                .containsEntry("solve_count", 0).containsEntry("deferred_until_round", 13L);
        assertThat(jdbc.queryForObject(
                "SELECT current_question_id FROM cl_api_schema.river_game_player_state WHERE user_id = 1",
                Map.of(), Long.class)).isNull();
        assertThat(nextQuestion()).isNotEqualTo(questionId);
    }

    @Test
    void revealIncludesAllFactsAndOnlyAnActiveRiverPhoto() {
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.countries (id, iso_alpha2, name_en) VALUES (10, 'DE', 'Germany')
                ON CONFLICT (id) DO NOTHING
                """);
        jdbc.getJdbcTemplate().execute("""
                UPDATE cl_schema.river_game_question
                SET length_km = 1230, facts_source_url = 'https://www.wikidata.org/wiki/Q584',
                    borders_image_key = 'river-game/%s.webp'
                WHERE id = 101
                """.formatted("b".repeat(64)));
        jdbc.getJdbcTemplate().execute("""
                DELETE FROM cl_media_schema.image_resource WHERE id = 9001;
                INSERT INTO cl_media_schema.image_resource (id, provider, source_key, lifecycle_status)
                VALUES (9001, 'WIKIMEDIA_COMMONS', 'file:loreley.jpg', 'ACTIVE');
                INSERT INTO cl_media_schema.image_rights_revision
                    (id, image_resource_id, source_page_url, author_name, license_label, license_url)
                VALUES (9101, 9001, 'https://commons.wikimedia.org/wiki/File:Loreley.jpg',
                        'Jane Doe', 'CC BY-SA 4.0', 'https://creativecommons.org/licenses/by-sa/4.0');
                UPDATE cl_media_schema.image_resource SET current_rights_revision_id = 9101 WHERE id = 9001;
                INSERT INTO cl_media_schema.image_rendition
                    (id, image_resource_id, rights_revision_id, storage_bucket, object_key, mime_type)
                VALUES (9201, 9001, 9101, 'hint-images', 'river-photos/%s.jpg', 'image/jpeg');
                INSERT INTO cl_schema.river_game_river_media (river_key, wikipedia_url, photo_resource_id)
                VALUES ('q584', 'https://en.wikipedia.org/wiki/Rhine', 9001)
                ON CONFLICT (river_key) DO UPDATE SET photo_resource_id = EXCLUDED.photo_resource_id;
                """.formatted("c".repeat(64)));
        RiverGameJdbcRepository repository = new RiverGameJdbcRepository(jdbc);

        assertThat(repository.findReveal(101L).orElseThrow())
                .extracting("name", "countryCode", "photoObjectKey", "photoAuthorName", "wikipediaUrl")
                .containsExactly("Rhine", "DE", "river-photos/" + "c".repeat(64) + ".jpg", "Jane Doe",
                        "https://en.wikipedia.org/wiki/Rhine");
        assertThat(repository.findReveal(101L).orElseThrow().facts()).contains("\"LENGTH\"");
        assertThat(repository.findRiverPhotoMimeType("river-photos/" + "c".repeat(64) + ".jpg"))
                .contains("image/jpeg");

        jdbc.getJdbcTemplate().execute(
                "UPDATE cl_media_schema.image_resource SET lifecycle_status = 'QUARANTINED' WHERE id = 9001");

        assertThat(repository.findReveal(101L).orElseThrow().photoObjectKey()).isNull();
        assertThat(repository.findRiverPhotoMimeType("river-photos/" + "c".repeat(64) + ".jpg")).isEmpty();
    }

    private long nextQuestion() {
        return jdbc.queryForObject("SELECT cl_api_schema.river_game_next_question(:userId)",
                Map.of("userId", 1L), Long.class);
    }

    private Map<String, Object> guess(long questionId, String answer) {
        return jdbc.queryForMap("""
                SELECT * FROM cl_api_schema.river_game_guess(:userId, :questionId, :answer)
                """, Map.of("userId", 1L, "questionId", questionId, "answer", answer));
    }
}
