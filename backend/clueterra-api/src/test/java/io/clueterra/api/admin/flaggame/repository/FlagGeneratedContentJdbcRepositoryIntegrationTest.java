package io.clueterra.api.admin.flaggame.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.TestcontainersConfiguration;
import io.clueterra.api.admin.flaggame.dto.FlagQuestionPreviewDto;
import io.clueterra.api.admin.flaggame.dto.FlagQuestionSummaryDto;

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
class FlagGeneratedContentJdbcRepositoryIntegrationTest {

    private static final int PAGE = 10;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private FlagGeneratedContentJdbcRepository repository;

    @BeforeEach
    void seed() {
        repository = new FlagGeneratedContentJdbcRepository(jdbc, new ObjectMapper());
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_api_schema.users CASCADE");
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_schema.flag_game_hint, cl_schema.flag_game_question CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users (id, email, username, password_hash, role, enabled, is_guest)
                VALUES (1, 'flag@example.com', 'flag_player', 'hash', 'USER', TRUE, FALSE);
                INSERT INTO cl_schema.flag_game_question (id, iso2, tier, names, is_active) VALUES
                    (1, 'TD', 4, '{"en":"Chad","ru":"Чад","cs":"Čad"}', TRUE),
                    (2, 'RO', 2, '{"en":"Romania","ru":"Румыния","cs":"Rumunsko"}', TRUE),
                    (3, 'BO', 3, '{"en":"Bolivia","ru":"Боливия","cs":"Bolívie"}', FALSE);
                INSERT INTO cl_schema.flag_game_hint (question_id, position, kind, cost_units, payload) VALUES
                    (1, 1, 'REGION', 1, '{"region":"Africa","subregion":"Middle Africa"}'),
                    (2, 1, 'REGION', 1, '{"region":"Europe","subregion":"Eastern Europe"}'),
                    (2, 3, 'CAPITAL', 2, '{"capitals":[{"en":"Bucharest"}]}');
                INSERT INTO cl_api_schema.flag_game_progress (user_id, question_id, attempt_count, first_attempt_at)
                VALUES (1, 1, 3, NOW());
                INSERT INTO cl_api_schema.flag_game_attempt (user_id, round_number, guess_iso2, question_id, correct) VALUES
                    (1, 1, 'RO', 1, FALSE),
                    (1, 1, 'AD', 1, FALSE);
                """);
    }

    @Test
    void filtersByCapitalClueAndSortsTheMostConfusedFlagsFirst() {
        assertThat(repository.findQuestions("", "", true, null, "WITH_CAPITAL", "COUNTRY", PAGE, 0))
                .extracting(FlagQuestionPreviewDto::countryCode).containsExactly("RO");
        assertThat(repository.findQuestions("", "", null, null, "NO_CAPITAL", "COUNTRY", PAGE, 0))
                .extracting(FlagQuestionPreviewDto::countryCode).containsExactly("BO", "TD");
        FlagQuestionPreviewDto confused = repository.findQuestions("", "", true, null, "ALL", "MOST_CONFUSED", PAGE, 0).getFirst();
        assertThat(confused.countryCode()).isEqualTo("TD");
        assertThat(confused.wrongGuesses()).isEqualTo(2);
        assertThat(confused.players()).isEqualTo(1);
        assertThat(confused.flagUrl()).isEqualTo("/api/assets/flags/td.svg");
        assertThat(repository.findQuestions("", "рум", null, null, "ALL", "COUNTRY", PAGE, 0))
                .extracting(FlagQuestionPreviewDto::countryCode).containsExactly("RO");
    }

    @Test
    void summaryCountsActiveQuestionsCapitalCluesAndPlay() {
        FlagQuestionSummaryDto summary = repository.findSummary();

        assertThat(summary.activeQuestions()).isEqualTo(2);
        assertThat(summary.capitalClues()).isEqualTo(1);
        assertThat(summary.players()).isEqualTo(1);
        assertThat(summary.guesses()).isEqualTo(3);
    }
}
