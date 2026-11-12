package io.clueterra.api.quizgame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import io.clueterra.api.TestcontainersConfiguration;
import io.clueterra.api.exception.GameSqlState;
import io.clueterra.api.quizgame.dto.QuizCountryLevelDto;
import io.clueterra.api.quizgame.dto.QuizPlayerStatsDto;
import io.clueterra.api.quizgame.model.QuizAnswerOutcome;
import io.clueterra.api.quizgame.model.QuizStopRow;
import io.clueterra.api.quizgame.repository.QuizGameJdbcRepository;

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
class QuizGameProgressIntegrationTest {

    private static final long PLAYER = 1L;
    private static final long GUEST = 2L;
    private static final long FRANCE = 1L;
    private static final long JAPAN = 5L;
    private static final int BASE_XP = 50;
    private static final int ACTIVE_COUNTRIES = 5;
    private static final int TOTAL_LEVELS = 6;
    private static final int TRAIL = 60;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private QuizGameJdbcRepository repository;

    @BeforeEach
    void seed() {
        repository = new QuizGameJdbcRepository(jdbc);
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_api_schema.users CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users (id, email, username, password_hash, role, enabled, is_guest)
                VALUES (1, 'quiz@example.com', 'quiz_player', 'hash', 'USER', TRUE, FALSE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'quiz_guest', 'USER', TRUE, TRUE, 'quiz-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_schema.quiz_game_question, cl_schema.quiz_game_country CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.quiz_game_country (id, iso2, tier, levels, longitude, latitude, neighbours, subregion, names)
                VALUES (1, 'FR', 1, 2, 2.4, 47.0, '{DE,BE,ES}', 'Western Europe', '{"en":"France","ru":"Франция","cs":"Francie"}'),
                       (2, 'DE', 1, 1, 10.4, 51.0, '{FR,BE}', 'Western Europe', '{"en":"Germany","ru":"Германия","cs":"Německo"}'),
                       (3, 'BE', 1, 1, 4.6, 50.6, '{FR,DE}', 'Western Europe', '{"en":"Belgium","ru":"Бельгия","cs":"Belgie"}'),
                       (4, 'ES', 1, 1, -3.7, 40.2, '{FR}', 'Southern Europe', '{"en":"Spain","ru":"Испания","cs":"Španělsko"}'),
                       (5, 'JP', 1, 1, 138.3, 36.2, '{}', 'Eastern Asia', '{"en":"Japan","ru":"Япония","cs":"Japonsko"}')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.quiz_game_question (id, quiz_country_id, kind, level, options, answer_index)
                SELECT country.id * 10 + question.position, country.id, question.kind, question.level,
                       '[{"type":"PLACE"},{"type":"PLACE"},{"type":"PLACE"},{"type":"PLACE"}]'::JSONB, question.answer_index
                FROM cl_schema.quiz_game_country country
                CROSS JOIN (VALUES
                    (1, 'FLAG', 1, 1),
                    (2, 'CAPITAL', 1, 2),
                    (3, 'WEALTH', 2, 0),
                    (4, 'FOREST', 2, 3)
                ) AS question(position, kind, level, answer_index)
                WHERE question.level <= country.levels
                """);
    }

    @Test
    void finishingALevelExploresTheCountryAwardsXpOnceAndPicksTheNextStop() {
        arrive(PLAYER, FRANCE);
        long stop = stopOf(PLAYER);

        rejects(() -> repository.answer(PLAYER, stop, 2, 0), GameSqlState.ROUND_NOT_CURRENT);

        QuizAnswerOutcome first = repository.answer(PLAYER, stop, 1, answerIndex(PLAYER, 1));
        QuizAnswerOutcome repeated = repository.answer(PLAYER, stop, 1, wrongIndex(PLAYER, 1));
        QuizAnswerOutcome second = repository.answer(PLAYER, stop, 2, wrongIndex(PLAYER, 2));

        assertThat(first.correct()).isTrue();
        assertThat(first.xpAwarded()).isEqualTo(BASE_XP);
        assertThat(first.explored()).isFalse();
        assertThat(repeated.correct()).isTrue();
        assertThat(repeated.xpAwarded()).isEqualTo(BASE_XP);
        assertThat(second.correct()).isFalse();
        assertThat(second.xpAwarded()).isZero();
        assertThat(second.explored()).isTrue();

        QuizPlayerStatsDto stats = repository.findPlayerStats(PLAYER);
        assertThat(stats.exploredCountries()).isEqualTo(1);
        assertThat(stats.masteredCountries()).isZero();
        assertThat(stats.totalCountries()).isEqualTo(ACTIVE_COUNTRIES);
        assertThat(stats.completedLevels()).isEqualTo(1);
        assertThat(stats.totalLevels()).isEqualTo(TOTAL_LEVELS);
        assertThat(stats.answeredQuestions()).isEqualTo(2);
        assertThat(stats.correctAnswers()).isEqualTo(1);
        assertThat(stats.xpAwarded()).isEqualTo(BASE_XP);
        assertThat(repository.findCountries(PLAYER)).containsExactly(new QuizCountryLevelDto("FR", 1, 2));

        QuizStopRow finished = repository.findStop(PLAYER).orElseThrow();
        assertThat(finished.level()).isEqualTo(1);
        assertThat(finished.levels()).isEqualTo(2);
        assertThat(finished.next()).isNotNull();
        assertThat(finished.next().countryCode()).isNotEqualTo("FR");
        assertThat(finished.nextLevel()).isEqualTo(1);
    }

    @Test
    void aReturnVisitPlaysTheNextLevelForDoubleXpAndMastersTheCountry() {
        arrive(PLAYER, FRANCE);
        finishStop(PLAYER);
        long first = stopOf(PLAYER);
        jdbc.update("""
                UPDATE cl_api_schema.quiz_game_player_state
                SET next_country_id = 1, next_level = 2, next_mode = 'DRIVE', next_route = '{BE,LU}' WHERE user_id = 1
                """, Map.of());

        repository.travel(PLAYER, first);

        QuizStopRow revisit = repository.findStop(PLAYER).orElseThrow();
        assertThat(revisit.country().countryCode()).isEqualTo("FR");
        assertThat(revisit.level()).isEqualTo(2);
        assertThat(revisit.answerXp()).isEqualTo(2 * BASE_XP);
        assertThat(revisit.questions()).contains("WEALTH", "FOREST").doesNotContain("\"FLAG\"");
        assertThat(revisit.questions()).doesNotContain("selectedIndex");

        QuizAnswerOutcome answer = repository.answer(PLAYER, revisit.stop(), 1, answerIndex(PLAYER, 1));
        repository.answer(PLAYER, revisit.stop(), 2, answerIndex(PLAYER, 2));

        assertThat(answer.xpAwarded()).isEqualTo(2 * BASE_XP);
        QuizPlayerStatsDto stats = repository.findPlayerStats(PLAYER);
        assertThat(stats.masteredCountries()).isEqualTo(1);
        assertThat(stats.completedLevels()).isEqualTo(2);
        assertThat(repository.findCountries(PLAYER)).containsExactly(new QuizCountryLevelDto("FR", 2, 2));
        assertThat(repository.findTrail(PLAYER, TRAIL))
                .extracting(step -> step.countryCode() + ":" + step.level() + ":" + String.join(",", step.route()))
                .containsExactly("FR:1:", "FR:2:BE,LU");
    }

    @Test
    void travellingNeedsAFinishedStopAndRecordsTheTrail() {
        arrive(PLAYER, JAPAN);
        long stop = stopOf(PLAYER);

        rejects(() -> repository.travel(PLAYER, stop), GameSqlState.ROUND_NOT_CURRENT);

        finishStop(PLAYER);
        QuizStopRow finished = repository.findStop(PLAYER).orElseThrow();
        assertThat(finished.nextMode()).isEqualTo("FLY");
        String destination = finished.next().countryCode();

        repository.travel(PLAYER, stop);

        QuizStopRow arrived = repository.findStop(PLAYER).orElseThrow();
        assertThat(arrived.stop()).isEqualTo(stop + 1);
        assertThat(arrived.country().countryCode()).isEqualTo(destination);
        assertThat(arrived.arrivalMode()).isEqualTo("FLY");
        assertThat(arrived.fromCountryCode()).isEqualTo("JP");
        assertThat(arrived.next()).isNull();
        assertThat(repository.findTrail(PLAYER, TRAIL))
                .extracting(step -> step.countryCode() + ":" + step.fromCountryCode())
                .containsExactly("JP:null", destination + ":JP");
    }

    @Test
    void staleStopsAndUnknownOptionsAreRejected() {
        long stop = start(PLAYER);

        rejects(() -> repository.answer(PLAYER, stop + 1, 1, 0), GameSqlState.ROUND_NOT_CURRENT);
        rejects(() -> repository.answer(PLAYER, stop, 1, 4), GameSqlState.INVALID_ANSWER);
        rejects(() -> repository.answer(PLAYER, stop, 3, 0), GameSqlState.INVALID_ANSWER);
    }

    @Test
    void theTourEndsWhenEveryLevelIsDone() {
        jdbc.update("UPDATE cl_schema.quiz_game_country SET is_active = FALSE WHERE iso2 <> 'DE'", Map.of());
        long stop = start(PLAYER);
        finishStop(PLAYER);

        assertThat(repository.findStop(PLAYER).orElseThrow().next()).isNull();
        repository.travel(PLAYER, stop);
        assertThat(stopOf(PLAYER)).isEqualTo(stop);
        QuizPlayerStatsDto stats = repository.findPlayerStats(PLAYER);
        assertThat(stats.completedLevels()).isEqualTo(stats.totalLevels()).isEqualTo(1);
    }

    @Test
    void guestClaimMovesLevelsTheTrailTheOpenStopAndXp() {
        long stop = start(GUEST);
        String explored = repository.findStop(GUEST).orElseThrow().country().countryCode();
        repository.answer(GUEST, stop, 1, answerIndex(GUEST, 1));
        repository.answer(GUEST, stop, 2, answerIndex(GUEST, 2));
        repository.travel(GUEST, stop);
        String open = repository.findStop(GUEST).orElseThrow().country().countryCode();
        repository.answer(GUEST, stopOf(GUEST), 1, wrongIndex(GUEST, 1));

        assertThat(repository.claimGuest("quiz-token", PLAYER)).isTrue();

        QuizStopRow current = repository.findStop(PLAYER).orElseThrow();
        assertThat(current.country().countryCode()).isEqualTo(open);
        assertThat(current.questions()).contains("\"selectedIndex\"");
        assertThat(repository.findTrail(PLAYER, TRAIL)).extracting(step -> step.countryCode()).containsExactly(explored, open);
        assertThat(repository.findCountries(PLAYER)).extracting(QuizCountryLevelDto::countryCode).containsExactly(explored);
        QuizPlayerStatsDto stats = repository.findPlayerStats(PLAYER);
        assertThat(stats.exploredCountries()).isEqualTo(1);
        assertThat(stats.answeredQuestions()).isEqualTo(3);
        assertThat(stats.xpAwarded()).isEqualTo(2L * BASE_XP);
        assertThat(repository.findPlayerStats(GUEST).xpAwarded()).isZero();
        assertThat(repository.findStop(GUEST)).isEmpty();
        assertThat(repository.claimGuest("quiz-token", PLAYER)).isFalse();
    }

    private void rejects(Runnable call, GameSqlState expected) {
        jdbc.getJdbcTemplate().execute("SAVEPOINT quiz_rejection");
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(DataAccessException.class,
                        error -> assertThat(GameSqlState.from(error)).contains(expected));
        jdbc.getJdbcTemplate().execute("ROLLBACK TO SAVEPOINT quiz_rejection");
    }

    private void arrive(long userId, long countryId) {
        jdbc.update("INSERT INTO cl_api_schema.quiz_game_player_state (user_id) VALUES (:userId)", Map.of("userId", userId));
        jdbc.queryForObject("SELECT cl_api_schema.quiz_game_arrive(:userId, :country, 1, NULL, NULL, '{}')",
                Map.of("userId", userId, "country", countryId), Long.class);
    }

    private long start(long userId) {
        repository.start(userId);
        return stopOf(userId);
    }

    private long stopOf(long userId) {
        return repository.findStop(userId).orElseThrow().stop();
    }

    private void finishStop(long userId) {
        long stop = stopOf(userId);
        repository.answer(userId, stop, 1, answerIndex(userId, 1));
        repository.answer(userId, stop, 2, answerIndex(userId, 2));
    }

    private int answerIndex(long userId, int position) {
        return jdbc.queryForObject("""
                SELECT question.answer_index FROM cl_api_schema.quiz_game_player_state state
                JOIN cl_schema.quiz_game_question question ON question.id = state.question_ids[:position]
                WHERE state.user_id = :userId
                """, Map.of("userId", userId, "position", position), Integer.class);
    }

    private int wrongIndex(long userId, int position) {
        return answerIndex(userId, position) == 0 ? 1 : 0;
    }
}
