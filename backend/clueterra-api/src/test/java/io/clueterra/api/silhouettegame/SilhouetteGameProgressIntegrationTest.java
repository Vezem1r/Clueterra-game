package io.clueterra.api.silhouettegame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
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
import io.clueterra.api.silhouettegame.dto.SilhouetteMissDto;
import io.clueterra.api.silhouettegame.dto.SilhouettePlayerStatsDto;
import io.clueterra.api.silhouettegame.model.SilhouetteGuessRow;
import io.clueterra.api.silhouettegame.model.SilhouettePhotoRow;
import io.clueterra.api.silhouettegame.model.SilhouetteQuestionRow;
import io.clueterra.api.silhouettegame.repository.SilhouetteGameJdbcRepository;

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
class SilhouetteGameProgressIntegrationTest {

    private static final long PLAYER = 1L;
    private static final long GUEST = 2L;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private SilhouetteGameJdbcRepository repository;

    @BeforeEach
    void seed() {
        repository = new SilhouetteGameJdbcRepository(jdbc);
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_api_schema.users CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users (id, email, username, password_hash, role, enabled, is_guest)
                VALUES (1, 'shape@example.com', 'shape_player', 'hash', 'USER', TRUE, FALSE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users
                    (id, username, role, enabled, is_guest, guest_token, guest_token_expires_at)
                VALUES (2, 'shape_guest', 'USER', TRUE, TRUE, 'shape-token', NOW() + INTERVAL '7 days')
                """);
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_schema.silhouette_game_hint, cl_schema.silhouette_game_question CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.silhouette_game_question (id, iso2, tier, is_active)
                SELECT position, code, (position - 1) % 5 + 1, code <> 'ZZ'
                FROM UNNEST(ARRAY['FR', 'DE', 'IT', 'ES', 'PT', 'NL', 'BE', 'PL', 'CZ', 'AT',
                                  'CH', 'SE', 'NO', 'FI', 'DK', 'IE', 'GR', 'HU', 'RO', 'BG', 'ZZ'])
                    WITH ORDINALITY AS catalog(code, position)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.silhouette_game_hint (question_id, position, kind, cost_units, payload)
                SELECT question.id, clue.position, clue.kind, clue.cost_units, clue.payload::JSONB
                FROM cl_schema.silhouette_game_question question
                CROSS JOIN (VALUES
                    (1, 'REGION', 1, '{"region":"Europe","subregion":"Western Europe","center":[5,48]}'),
                    (2, 'LOCATION', 1, '{"center":[2.3,46.6],"bounds":[-5,41,9,51]}'),
                    (3, 'CAPITAL', 2, '{"capitals":[{"en":"Paris","ru":"Париж","cs":"Paříž"}]}'),
                    (4, 'LANDMARK', 2, '{}')
                ) AS clue(position, kind, cost_units, payload)
                """);
    }

    @Test
    void theCountryAndTheCurrentSilhouetteCarryTheirLandmarkPhoto() {
        jdbc.getJdbcTemplate().execute("""
                TRUNCATE cl_media_schema.image_rendition, cl_media_schema.image_rights_revision,
                    cl_media_schema.image_resource CASCADE;
                INSERT INTO cl_media_schema.image_resource (id, provider, source_key) VALUES (10, 'WIKIMEDIA_COMMONS', 'eiffel');
                INSERT INTO cl_media_schema.image_rights_revision
                    (id, image_resource_id, source_page_url, author_name, license_label, width, height)
                VALUES (20, 10, 'https://commons.wikimedia.org/wiki/File:Eiffel.jpg', 'Benh', 'CC BY-SA 3.0', 2900, 5367);
                UPDATE cl_media_schema.image_resource SET current_rights_revision_id = 20 WHERE id = 10;
                INSERT INTO cl_media_schema.image_rendition (id, image_resource_id, rights_revision_id, storage_bucket, object_key)
                VALUES (30, 10, 20, 'hint-images', 'country-photos/eiffel.jpg');
                UPDATE cl_schema.silhouette_game_question
                SET photo_resource_id = 10, landmark_names = '{"en":"Eiffel Tower"}'
                WHERE iso2 = 'FR';
                """);

        assertThat(repository.findCountry(1L).orElseThrow().photo())
                .extracting(SilhouettePhotoRow::objectKey, SilhouettePhotoRow::authorName, SilhouettePhotoRow::landmarkNames)
                .containsExactly("country-photos/eiffel.jpg", "Benh", "{\"en\": \"Eiffel Tower\"}");
        assertThat(repository.findCountry(2L).orElseThrow().photo()).isNull();

        startRound(PLAYER);
        SilhouetteQuestionRow current = repository.findCurrent(PLAYER).orElseThrow();
        assertThat(current.photo() != null).isEqualTo("FR".equals(current.iso2()));
    }

    @Test
    void solvingPaintsTheCountryOnceAndAwardsHintAdjustedXp() {
        long round = startRound(PLAYER);
        for (int clue = 0; clue < 5; clue++) {
            repository.revealHint(PLAYER, round);
        }
        SilhouetteQuestionRow current = repository.findCurrent(PLAYER).orElseThrow();
        assertThat(current.hintsUsed()).isEqualTo(4);
        assertThat(current.hintPenaltyPercent()).isEqualTo(8);
        String answer = currentIso(PLAYER);

        SilhouetteGuessRow wrong = repository.guess(PLAYER, round, "FR".equals(answer) ? "DE" : "FR");
        SilhouetteGuessRow right = repository.guess(PLAYER, round, answer);

        assertThat(wrong.correct()).isFalse();
        assertThat(right.correct()).isTrue();
        assertThat(right.attempts()).isEqualTo(2);
        assertThat(right.xpAwarded()).isEqualTo(78);
        assertThat(repository.findPlayerSession(PLAYER).solved()).containsExactly(answer);
        SilhouettePlayerStatsDto stats = repository.findPlayerStats(PLAYER);
        assertThat(stats.solvedCountries()).isEqualTo(1);
        assertThat(stats.answerAttempts()).isEqualTo(2);
        assertThat(stats.totalCountries()).isEqualTo(20);
        assertThat(stats.xpAwarded()).isEqualTo(78);
        assertThat(repository.findDiscoveredCountry(PLAYER, answer)).isPresent();

        long nextRound = startRound(PLAYER);
        assertThat(nextRound).isGreaterThan(round);
        assertThat(currentIso(PLAYER)).isNotEqualTo(answer);
    }

    @Test
    void wrongGuessesOfTheRoundAreKeptInOrderAndPointTowardsTheAnswer() {
        jdbc.getJdbcTemplate().execute("""
                UPDATE cl_schema.silhouette_game_question
                SET center_lng = CASE iso2 WHEN 'FR' THEN 2.35 ELSE 13.4 END,
                    center_lat = CASE iso2 WHEN 'FR' THEN 48.86 ELSE 52.52 END
                WHERE iso2 IN ('FR', 'DE')
                """);
        long round = startRound(PLAYER);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.silhouette_game_progress (user_id, question_id) VALUES (1, 1)
                ON CONFLICT DO NOTHING
                """);
        jdbc.getJdbcTemplate().execute("UPDATE cl_api_schema.silhouette_game_player_state SET current_question_id = 1 WHERE user_id = 1");

        repository.guess(PLAYER, round, "IT");
        repository.guess(PLAYER, round, "DE");
        repository.guess(PLAYER, round, "IT");

        assertThat(repository.findRoundMisses(PLAYER, round)).extracting(SilhouetteMissDto::countryCode).containsExactly("IT", "DE");
        assertThat(repository.findRoundMisses(PLAYER, round).getLast()).isEqualTo(new SilhouetteMissDto("DE", 877, 247));
        assertThat(repository.findCurrent(PLAYER).orElseThrow().attempts()).isEqualTo(3);

        repository.guess(PLAYER, round, "FR");

        assertThat(repository.findRoundMisses(PLAYER, round)).extracting(SilhouetteMissDto::countryCode).containsExactly("IT", "DE");
        assertThat(repository.findRoundMisses(PLAYER, startRound(PLAYER))).isEmpty();
    }

    @Test
    void unsolvedCountriesStayHidden() {
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
                .hasMessageContaining("Choose a country");
    }

    @Test
    void retiredQuestionsAreNeitherServedNorAccepted() {
        long round = startRound(PLAYER);

        assertThatThrownBy(() -> repository.guess(PLAYER, round, "ZZ"))
                .hasMessageContaining("Choose a country");
    }

    @Test
    void lighterCluesCostHalfOfTheCapitalClue() {
        long round = startRound(PLAYER);
        repository.revealHint(PLAYER, round);
        repository.revealHint(PLAYER, round);

        assertThat(repository.guess(PLAYER, round, currentIso(PLAYER)).xpAwarded()).isEqualTo(126);
    }

    @Test
    void skippedAndRevealedCountriesAreDeferredWithoutBeingPainted() {
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
    void guestClaimMovesSolvedCountriesAndXpToTheRegisteredPlayer() {
        long round = startRound(GUEST);
        String answer = currentIso(GUEST);
        repository.guess(GUEST, round, answer);
        long open = startRound(GUEST);
        String wrong = "FR".equals(currentIso(GUEST)) ? "DE" : "FR";
        repository.guess(GUEST, open, wrong);

        assertThat(repository.claimGuest("shape-token", PLAYER)).isTrue();

        long merged = repository.findCurrent(PLAYER).orElseThrow().round();
        assertThat(repository.findRoundMisses(PLAYER, merged)).extracting(SilhouetteMissDto::countryCode).containsExactly(wrong);

        assertThat(repository.findPlayerSession(PLAYER).solved()).containsExactly(answer);
        assertThat(repository.findPlayerSession(GUEST).solved()).isEmpty();
        assertThat(repository.findPlayerStats(PLAYER).xpAwarded()).isEqualTo(150);
        assertThat(repository.findPlayerStats(GUEST).xpAwarded()).isZero();
        assertThat(repository.findCurrent(PLAYER)).isPresent();
        assertThat(jdbc.queryForObject("SELECT total_xp FROM cl_api_schema.user_profiles WHERE user_id = 1",
                Map.of(), Long.class)).isEqualTo(150L);
        assertThat(repository.claimGuest("shape-token", PLAYER)).isFalse();
    }

    @Test
    void newPlayersGetThreeEasyTwoMediumAndOneHardCountry() {
        List<String> bands = new ArrayList<>();
        for (int round = 0; round < 12; round++) {
            long current = startRound(PLAYER);
            int tier = jdbc.queryForObject("""
                    SELECT question.tier FROM cl_api_schema.silhouette_game_player_state state
                    JOIN cl_schema.silhouette_game_question question ON question.id = state.current_question_id
                    WHERE state.user_id = :userId
                    """, Map.of("userId", PLAYER), Integer.class);
            bands.add(tier <= 2 ? "E" : tier == 3 ? "M" : "H");
            repository.guess(PLAYER, current, currentIso(PLAYER));
        }

        assertThat(String.join("", bands)).isEqualTo("EEEMMHEEEMMH");
    }

    private long startRound(long userId) {
        repository.selectNext(userId);
        return repository.findCurrent(userId).orElseThrow().round();
    }

    private String currentIso(long userId) {
        return jdbc.queryForObject("""
                SELECT question.iso2 FROM cl_api_schema.silhouette_game_player_state state
                JOIN cl_schema.silhouette_game_question question ON question.id = state.current_question_id
                WHERE state.user_id = :userId
                """, Map.of("userId", userId), String.class);
    }
}
