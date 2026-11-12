package io.clueterra.questions.quizgame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.TestcontainersConfiguration;
import io.clueterra.questions.countrycatalog.repository.CountryCatalogRepository;
import io.clueterra.questions.countrycatalog.service.CountryCatalogReader;
import io.clueterra.questions.countrycatalog.service.CountryFactValidator;
import io.clueterra.questions.countrycatalog.service.CountryTiers;
import io.clueterra.questions.quizgame.repository.QuizCatalogRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.operation.AdminOperationRunner;

@JdbcTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=cl_schema,cl_sch_schema,cl_staging",
        "spring.flyway.default-schema=cl_schema",
        "spring.flyway.create-schemas=true",
        "spring.datasource.hikari.connection-init-sql=SET search_path TO cl_schema"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class QuizGenerationIntegrationTest {

    private static final int POOL_SIZE = 197;
    private static final int OPTION_COUNT = 4;
    private static final String QUESTION = """
            SELECT question.options::TEXT AS options, question.answer_index, question.fact::TEXT AS fact
            FROM cl_schema.quiz_game_question question
            JOIN cl_schema.quiz_game_country country ON country.id = question.quiz_country_id
            WHERE country.iso2 = ? AND question.kind = ? AND question.is_active
            """;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();
    private QuizGenerationService service;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbc);
        service = new QuizGenerationService(mock(AdminOperationRunner.class), new CountryCatalogReader(mapper),
                new CountryCatalogRepository(named), new CountryFactValidator(),
                new CountryTiers(), new QuizFactChecker(),
                new QuizQuestionBuilder(new QuizDistractors(), new QuizFacts(mapper), mapper),
                new QuizCatalogRepository(named, mapper), mock(ConsoleLogWriter.class));
        jdbc.update("""
                INSERT INTO cl_schema.country_statistics (id, country_id, source, indicator_code, indicator_name, category, value, value_text, year)
                SELECT 990000000 + row_number() OVER (), country.id, data.source, data.code, data.code, 'GEOGRAPHY', data.value, data.text, data.year
                FROM cl_schema.countries country
                JOIN (VALUES
                    ('FR', 'WORLD_BANK', 'SP.POP.TOTL', 68170000.0, NULL, 2023),
                    ('FR', 'GEONAMES', 'GEO_CAPITAL', NULL, 'Paris', NULL),
                    ('FR', 'GEONAMES', 'GEO_AREA', 547030.0, NULL, NULL),
                    ('FR', 'CIA_FACTBOOK', 'HIGHEST_POINT', NULL, 'Mont Blanc 4,810 m', NULL),
                    ('FR', 'WIKIDATA', 'WIKIDATA_DRIVING_SIDE', NULL, 'right', NULL),
                    ('FR', 'GEONAMES', 'GEO_CURRENCY_CODE', NULL, 'EUR', NULL),
                    ('FR', 'CIA_FACTBOOK', 'COASTLINE', 4853.0, NULL, NULL),
                    ('DE', 'GEONAMES', 'GEO_CAPITAL', NULL, 'Berlin', NULL),
                    ('DE', 'CIA_FACTBOOK', 'HIGHEST_POINT', NULL, 'Zugspitze 2,963 m', NULL),
                    ('ES', 'CIA_FACTBOOK', 'HIGHEST_POINT', NULL, 'Pico de Teide (Tenerife) on Canary Islands 3,718 m', NULL),
                    ('CH', 'CIA_FACTBOOK', 'HIGHEST_POINT', NULL, 'Dufourspitze 4,634 m', NULL),
                    ('IT', 'CIA_FACTBOOK', 'HIGHEST_POINT', NULL, 'Mont Blanc 4,810 m', NULL),
                    ('ES', 'GEONAMES', 'GEO_CAPITAL', NULL, 'Madrid', NULL),
                    ('IT', 'GEONAMES', 'GEO_CAPITAL', NULL, 'Rome', NULL),
                    ('BE', 'GEONAMES', 'GEO_CAPITAL', NULL, 'Brussels', NULL),
                    ('DE', 'GEONAMES', 'GEO_CURRENCY_CODE', NULL, 'EUR', NULL),
                    ('CH', 'GEONAMES', 'GEO_CURRENCY_CODE', NULL, 'CHF', NULL),
                    ('CZ', 'GEONAMES', 'GEO_CURRENCY_CODE', NULL, 'CZK', NULL),
                    ('PL', 'GEONAMES', 'GEO_CURRENCY_CODE', NULL, 'PLN', NULL),
                    ('GB', 'GEONAMES', 'GEO_CURRENCY_CODE', NULL, 'GBP', NULL),
                    ('JP', 'WIKIDATA', 'WIKIDATA_DRIVING_SIDE', NULL, 'left', NULL),
                    ('KR', 'WIKIDATA', 'WIKIDATA_DRIVING_SIDE', NULL, 'right', NULL),
                    ('CN', 'WIKIDATA', 'WIKIDATA_DRIVING_SIDE', NULL, 'right', NULL),
                    ('MN', 'WIKIDATA', 'WIKIDATA_DRIVING_SIDE', NULL, 'right', NULL),
                    ('FR', 'WIKIDATA', 'WIKIDATA_TOP_LEVEL_DOMAIN', NULL, '.fr', NULL),
                    ('DE', 'WIKIDATA', 'WIKIDATA_TOP_LEVEL_DOMAIN', NULL, '.de', NULL),
                    ('BE', 'WIKIDATA', 'WIKIDATA_TOP_LEVEL_DOMAIN', NULL, '.be', NULL),
                    ('CH', 'WIKIDATA', 'WIKIDATA_TOP_LEVEL_DOMAIN', NULL, '.ch, .swiss', NULL),
                    ('GB', 'WIKIDATA', 'WIKIDATA_TOP_LEVEL_DOMAIN', NULL, '.uk', NULL),
                    ('JP', 'GEONAMES', 'GEO_CURRENCY_CODE', NULL, 'JPY', NULL),
                    ('JP', 'CIA_FACTBOOK', 'CURRENCY', NULL, 'euros (EUR) per US dollar -', NULL)
                ) AS data(iso, source, code, value, text, year) ON data.iso = country.iso_alpha2
                """);
    }

    @Test
    void generatesTheWholeTourWithVerifiedAnswersAndPlausibleOptions() throws Exception {
        service.generate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cl_schema.quiz_game_country WHERE is_active", Integer.class))
                .isEqualTo(POOL_SIZE);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM cl_schema.quiz_game_country country
                WHERE (SELECT COUNT(*) FROM cl_schema.quiz_game_question question
                       WHERE question.quiz_country_id = country.id AND question.is_active) < 2
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT neighbours::TEXT FROM cl_schema.quiz_game_country WHERE iso2 = 'FR'", String.class))
                .singleElement().asString().contains("DE", "ES", "IT").doesNotContain("BR");

        Map<String, Object> capital = jdbc.queryForMap(QUESTION, "FR", "CAPITAL");
        List<Map<String, Object>> options = options(capital);
        assertThat(options).hasSize(OPTION_COUNT);
        assertThat(names(options.get(answer(capital)))).containsEntry("en", "Paris");
        assertThat(options).extracting(option -> names(option).get("en")).doesNotHaveDuplicates().contains("Berlin");

        Map<String, Object> flag = jdbc.queryForMap(QUESTION, "FR", "FLAG");
        assertThat(options(flag)).extracting(option -> option.get("iso")).doesNotHaveDuplicates().hasSize(OPTION_COUNT);
        assertThat(options(flag).get(answer(flag))).containsEntry("iso", "FR");

        Map<String, Object> currency = jdbc.queryForMap(QUESTION, "FR", "CURRENCY");
        assertThat(options(currency).get(answer(currency))).containsEntry("code", "EUR");
        assertThat(options(currency)).extracting(option -> option.get("code")).doesNotHaveDuplicates().hasSize(OPTION_COUNT);
        assertThat(names(options(currency).get(answer(currency)))).containsEntry("ru", "Евро");
        assertThat(jdbc.queryForList(QUESTION, "JP", "CURRENCY")).isEmpty();

        Map<String, Object> driving = jdbc.queryForMap(QUESTION, "JP", "LEFT_DRIVING");
        assertThat(options(driving).get(answer(driving))).containsEntry("iso", "JP");
        assertThat(options(driving)).extracting(option -> option.get("iso")).doesNotHaveDuplicates().hasSize(OPTION_COUNT);

        Map<String, Object> domain = jdbc.queryForMap(QUESTION, "FR", "DOMAIN");
        assertThat(options(domain).get(answer(domain))).containsEntry("text", ".fr");
        assertThat(options(domain)).extracting(option -> option.get("text")).doesNotContain(".uk").doesNotHaveDuplicates();
        assertThat(jdbc.queryForList(QUESTION, "GB", "DOMAIN")).isEmpty();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM cl_schema.quiz_game_country country
                WHERE country.is_active AND country.levels <> (
                    SELECT MAX(question.level) FROM cl_schema.quiz_game_question question
                    WHERE question.quiz_country_id = country.id AND question.is_active)
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT question.quiz_country_id, question.level FROM cl_schema.quiz_game_question question
                    WHERE question.is_active GROUP BY 1, 2 HAVING COUNT(*) < 2) thin
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT question.level FROM cl_schema.quiz_game_question question
                JOIN cl_schema.quiz_game_country country ON country.id = question.quiz_country_id
                WHERE country.iso2 = 'FR' AND question.kind = 'FLAG'
                """, Integer.class)).isEqualTo(1);

        Map<String, Object> neighbour = jdbc.queryForMap(QUESTION, "DE", "NEIGHBOUR");
        String bordering = jdbc.queryForObject("SELECT neighbours::TEXT FROM cl_schema.quiz_game_country WHERE iso2 = 'DE'", String.class);
        List<Map<String, Object>> neighbourOptions = options(neighbour);
        assertThat(bordering).contains((String) neighbourOptions.get(answer(neighbour)).get("iso"));
        neighbourOptions.remove(answer(neighbour));
        neighbourOptions.forEach(option -> assertThat(bordering).doesNotContain((String) option.get("iso")));

        Map<String, Object> peak = jdbc.queryForMap(QUESTION, "FR", "HIGHEST_POINT");
        assertThat(names(options(peak).get(answer(peak)))).containsEntry("en", "Mont Blanc");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM cl_schema.quiz_game_question question
                JOIN cl_schema.quiz_game_country country ON country.id = question.quiz_country_id
                WHERE country.iso2 = 'AT' AND question.kind = 'LANDLOCKED'
                """, Integer.class)).isZero();

        String hash = jdbc.queryForObject("SELECT content_hash FROM cl_schema.quiz_game_country WHERE iso2 = 'FR'", String.class);
        Long capitalId = jdbc.queryForObject("""
                SELECT question.id FROM cl_schema.quiz_game_question question
                JOIN cl_schema.quiz_game_country country ON country.id = question.quiz_country_id
                WHERE country.iso2 = 'FR' AND question.kind = 'CAPITAL'
                """, Long.class);
        service.generate();
        assertThat(jdbc.queryForObject("SELECT content_hash FROM cl_schema.quiz_game_country WHERE iso2 = 'FR'", String.class))
                .isEqualTo(hash);
        assertThat(jdbc.queryForObject("""
                SELECT question.id FROM cl_schema.quiz_game_question question
                JOIN cl_schema.quiz_game_country country ON country.id = question.quiz_country_id
                WHERE country.iso2 = 'FR' AND question.kind = 'CAPITAL'
                """, Long.class)).isEqualTo(capitalId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> options(Map<String, Object> question) {
        try {
            return mapper.readValue((String) question.get("options"), List.class);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> names(Map<String, Object> option) {
        return (Map<String, String>) option.get("names");
    }

    private int answer(Map<String, Object> question) {
        return ((Number) question.get("answer_index")).intValue();
    }
}
