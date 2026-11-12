package io.clueterra.api.flaggame;

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
import io.clueterra.api.flaggame.dto.FlagPlayerStatsDto;
import io.clueterra.api.flaggame.model.FlagGuessRow;
import io.clueterra.api.flaggame.model.FlagQuestionRow;
import io.clueterra.api.flaggame.repository.FlagGameJdbcRepository;

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
class FlagGameProgressIntegrationTest {

    private static final long PLAYER = 1L;
    private static final long GUEST = 2L;
    private static final int ACTIVE_FLAGS = 20;
    private static final int BASE_XP = 150;
    private static final int ALL_CLUES_XP = 102;
    private static final int LIGHT_CLUES_XP = 126;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private FlagGameJdbcRepository repository;

    @BeforeEach
    void seed() {
        repository = new FlagGameJdbcRepository(jdbc);
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_api_schema.users CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users (id, email, username, password_hash, role, enabled, is_guest)
                VALUES (1, 'flag@example.com', 'flag_player', 'hash', 'USER', TRUE, FALSE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'flag_guest', 'USER', TRUE, TRUE, 'flag-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_schema.flag_game_hint, cl_schema.flag_game_question CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.flag_game_question (id, iso2, tier, is_active)
                SELECT position, code, (position - 1) % 5 + 1, code <> 'ZZ'
                FROM UNNEST(ARRAY['FR', 'DE', 'IT', 'ES', 'PT', 'NL', 'BE', 'PL', 'CZ', 'AT',
                                  'CH', 'SE', 'NO', 'FI', 'DK', 'IE', 'GR', 'HU', 'RO', 'BG', 'ZZ'])
                    WITH ORDINALITY AS catalog(code, position)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.flag_game_hint (question_id, position, kind, cost_units, payload)
                SELECT question.id, clue.position, clue.kind, clue.cost_units, clue.payload::JSONB
                FROM cl_schema.flag_game_question question
                CROSS JOIN (VALUES
                    (1, 'REGION', 1, '{"region":"Europe","subregion":"Western Europe"}'),
                    (2, 'INITIAL', 1, '{"letters":{"en":"F","ru":"Ф","cs":"F"}}'),
                    (3, 'CAPITAL', 2, '{"capitals":[{"en":"Paris","ru":"Париж","cs":"Paříž"}]}')
                ) AS clue(position, kind, cost_units, payload)
                """);
    }

    @Test
    void solvingRaisesTheFlagOnceAndAwardsHintAdjustedXp() {
        long round = startRound(PLAYER);
        for (int clue = 0; clue < 4; clue++) {
            repository.revealHint(PLAYER, round);
        }
        FlagQuestionRow current = repository.findCurrent(PLAYER).orElseThrow();
        assertThat(current.hintsUsed()).isEqualTo(3);
        assertThat(current.hintPenaltyPercent()).isEqualTo(8);
        String answer = currentIso(PLAYER);
        assertThat(repository.findRoundFlag(PLAYER, round)).contains(answer);
        assertThat(repository.findRoundFlag(PLAYER, round + 1)).isEmpty();

        FlagGuessRow wrong = repository.guess(PLAYER, round, "FR".equals(answer) ? "DE" : "FR");
        FlagGuessRow right = repository.guess(PLAYER, round, answer);

        assertThat(wrong.correct()).isFalse();
        assertThat(right.correct()).isTrue();
        assertThat(right.attempts()).isEqualTo(2);
        assertThat(right.xpAwarded()).isEqualTo(ALL_CLUES_XP);
        assertThat(repository.findPlayerSession(PLAYER).solved()).containsExactly(answer);
        FlagPlayerStatsDto stats = repository.findPlayerStats(PLAYER);
        assertThat(stats.solvedFlags()).isEqualTo(1);
        assertThat(stats.answerAttempts()).isEqualTo(2);
        assertThat(stats.totalFlags()).isEqualTo(ACTIVE_FLAGS);
        assertThat(stats.xpAwarded()).isEqualTo(ALL_CLUES_XP);
        assertThat(repository.findDiscoveredCountry(PLAYER, answer).orElseThrow().capitals()).contains("Paris");
        assertThat(repository.findRoundFlag(PLAYER, round)).isEmpty();

        long nextRound = startRound(PLAYER);
        assertThat(nextRound).isGreaterThan(round);
        assertThat(currentIso(PLAYER)).isNotEqualTo(answer);
    }

    @Test
    void wrongGuessesOfTheRoundAreKeptInOrder() {
        long round = startRound(PLAYER);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.flag_game_progress (user_id, question_id) VALUES (1, 1)
                ON CONFLICT DO NOTHING
                """);
        jdbc.getJdbcTemplate().execute("UPDATE cl_api_schema.flag_game_player_state SET current_question_id = 1 WHERE user_id = 1");

        repository.guess(PLAYER, round, "IT");
        repository.guess(PLAYER, round, "DE");
        repository.guess(PLAYER, round, "IT");

        assertThat(repository.findRoundMisses(PLAYER, round)).containsExactly("IT", "DE");
        assertThat(repository.findCurrent(PLAYER).orElseThrow().attempts()).isEqualTo(3);

        repository.guess(PLAYER, round, "FR");

        assertThat(repository.findRoundMisses(PLAYER, startRound(PLAYER))).isEmpty();
    }

    @Test
    void unsolvedFlagsStayHidden() {
        startRound(PLAYER);

        assertThat(repository.findDiscoveredCountry(PLAYER, currentIso(PLAYER))).isEmpty();
    }

    @Test
    void staleRoundsAreRejected() {
        long round = startRound(PLAYER);

        assertThatThrownBy(() -> repository.guess(PLAYER, round + 1, "FR"))
                .isInstanceOfSatisfying(DataAccessException.class,
                        error -> assertThat(GameSqlState.from(error)).contains(GameSqlState.ROUND_NOT_CURRENT));
    }

    @Test
    void countriesOutsideTheCatalogAreRejected() {
        long round = startRound(PLAYER);

        assertThatThrownBy(() -> repository.guess(PLAYER, round, "EH"))
                .isInstanceOfSatisfying(DataAccessException.class,
                        error -> assertThat(GameSqlState.from(error)).contains(GameSqlState.INVALID_ANSWER));
    }

    @Test
    void retiredFlagsAreNeitherServedNorAccepted() {
        long round = startRound(PLAYER);

        assertThatThrownBy(() -> repository.guess(PLAYER, round, "ZZ")).hasMessageContaining("Choose a country");
    }

    @Test
    void lightCluesCostHalfOfTheCapitalClue() {
        long round = startRound(PLAYER);
        repository.revealHint(PLAYER, round);
        repository.revealHint(PLAYER, round);

        assertThat(repository.guess(PLAYER, round, currentIso(PLAYER)).xpAwarded()).isEqualTo(LIGHT_CLUES_XP);
    }

    @Test
    void skippedAndRevealedFlagsAreDeferredWithoutBeingRaised() {
        long round = startRound(PLAYER);
        String skipped = currentIso(PLAYER);
        repository.skip(PLAYER, round);
        String next = currentIso(PLAYER);
        long revealedId = repository.showAnswer(PLAYER, repository.findCurrent(PLAYER).orElseThrow().round());

        assertThat(next).isNotEqualTo(skipped);
        assertThat(repository.findCountry(revealedId).orElseThrow().iso2()).isEqualTo(next);
        assertThat(repository.findPlayerSession(PLAYER).solved()).isEmpty();
        assertThat(repository.findDiscoveredCountry(PLAYER, next)).isPresent();
        assertThat(repository.findPlayerStats(PLAYER).skips()).isEqualTo(1);
    }

    @Test
    void guestClaimMovesRaisedFlagsAndXpToTheRegisteredPlayer() {
        long round = startRound(GUEST);
        String answer = currentIso(GUEST);
        repository.guess(GUEST, round, answer);
        long open = startRound(GUEST);
        String wrong = "FR".equals(currentIso(GUEST)) ? "DE" : "FR";
        repository.guess(GUEST, open, wrong);

        assertThat(repository.claimGuest("flag-token", PLAYER)).isTrue();

        long merged = repository.findCurrent(PLAYER).orElseThrow().round();
        assertThat(repository.findRoundMisses(PLAYER, merged)).containsExactly(wrong);
        assertThat(repository.findPlayerSession(PLAYER).solved()).containsExactly(answer);
        assertThat(repository.findPlayerSession(GUEST).solved()).isEmpty();
        assertThat(repository.findPlayerStats(PLAYER).xpAwarded()).isEqualTo(BASE_XP);
        assertThat(repository.findPlayerStats(GUEST).xpAwarded()).isZero();
        assertThat(jdbc.queryForObject("SELECT total_xp FROM cl_api_schema.user_profiles WHERE user_id = 1",
                Map.of(), Long.class)).isEqualTo((long) BASE_XP);
        assertThat(repository.claimGuest("flag-token", PLAYER)).isFalse();
    }

    private long startRound(long userId) {
        repository.selectNext(userId);
        return repository.findCurrent(userId).orElseThrow().round();
    }

    private String currentIso(long userId) {
        return jdbc.queryForObject("""
                SELECT question.iso2 FROM cl_api_schema.flag_game_player_state state
                JOIN cl_schema.flag_game_question question ON question.id = state.current_question_id
                WHERE state.user_id = :userId
                """, Map.of("userId", userId), String.class);
    }
}
