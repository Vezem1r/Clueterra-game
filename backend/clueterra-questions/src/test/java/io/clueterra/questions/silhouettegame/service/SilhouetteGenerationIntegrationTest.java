package io.clueterra.questions.silhouettegame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import io.clueterra.questions.silhouettegame.repository.SilhouetteCatalogRepository;

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
class SilhouetteGenerationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private SilhouetteGenerationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        SilhouetteCatalogRepository repository = new SilhouetteCatalogRepository(new NamedParameterJdbcTemplate(jdbc), mapper);
        service = new SilhouetteGenerationService(mock(AdminOperationRunner.class), repository,
                new CountryCatalogRepository(new NamedParameterJdbcTemplate(jdbc)), new CountryFactValidator(),
                new SilhouetteQuestionBuilder(new CountryTiers(), mapper), new CountryCatalogReader(mapper),
                mock(ConsoleLogWriter.class));
        jdbc.update("""
                INSERT INTO cl_schema.country_statistics (id, country_id, source, indicator_code, indicator_name, category, value, value_text, year)
                SELECT 990000000 + row_number() OVER (), country.id, data.source, data.code, data.code, 'GEOGRAPHY', data.value, data.text, data.year
                FROM cl_schema.countries country
                CROSS JOIN (VALUES
                    ('WORLD_BANK', 'SP.POP.TOTL', 68170000.0, NULL, 2023),
                    ('GEONAMES', 'GEO_CAPITAL', NULL, 'Paris', NULL),
                    ('GEONAMES', 'GEO_AREA', 547030.0, NULL, NULL)
                ) AS data(source, code, value, text, year)
                WHERE country.iso_alpha2 = 'FR'
                """);
    }

    @Test
    void generatesEveryCountryWithOrderedCluesAndVerifiedFacts() throws Exception {
        service.generate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cl_schema.silhouette_game_question WHERE is_active", Integer.class))
                .isEqualTo(197);
        assertThat(jdbc.queryForList("""
                SELECT hint.kind FROM cl_schema.silhouette_game_hint hint
                JOIN cl_schema.silhouette_game_question question ON question.id = hint.question_id
                WHERE question.iso2 = 'FR' ORDER BY hint.position
                """, String.class)).containsExactly("REGION", "LOCATION", "CAPITAL");
        assertThat(jdbc.queryForList("""
                SELECT hint.kind FROM cl_schema.silhouette_game_hint hint
                JOIN cl_schema.silhouette_game_question question ON question.id = hint.question_id
                WHERE question.iso2 = 'IL' ORDER BY hint.position
                """, String.class)).containsExactly("REGION", "LOCATION", "FLAG_COLORS");
        assertThat(jdbc.queryForMap("""
                SELECT facts -> 'capital' ->> 'en' AS capital, (facts -> 'population' ->> 'value')::BIGINT AS population,
                       (facts -> 'areaKm2' ->> 'value')::NUMERIC AS area, tier
                FROM cl_schema.silhouette_game_question WHERE iso2 = 'FR'
                """))
                .containsEntry("capital", "Paris")
                .containsEntry("population", 68170000L)
                .containsEntry("tier", 1);
        assertThat(jdbc.queryForObject(
                "SELECT facts -> 'population' FROM cl_schema.silhouette_game_question WHERE iso2 = 'DE'", String.class))
                .isEqualTo("null");

        assertThat(Math.abs(jdbc.queryForObject("""
                SELECT (hint.payload -> 'center' ->> 0)::DOUBLE PRECISION FROM cl_schema.silhouette_game_hint hint
                JOIN cl_schema.silhouette_game_question question ON question.id = hint.question_id
                WHERE question.iso2 = 'TO' AND hint.kind = 'REGION'
                """, Double.class))).isGreaterThan(160);

        String hash = jdbc.queryForObject("SELECT content_hash FROM cl_schema.silhouette_game_question WHERE iso2 = 'FR'", String.class);
        jdbc.update("UPDATE cl_schema.silhouette_game_question SET photo_selection = 'MANUAL', photo_selected_by = 'admin', "
                + "landmark_qid = 'Q243' WHERE iso2 = 'FR'");
        service.generate();
        assertThat(jdbc.queryForMap("SELECT photo_selection, landmark_qid FROM cl_schema.silhouette_game_question WHERE iso2 = 'FR'"))
                .containsEntry("photo_selection", "MANUAL")
                .containsEntry("landmark_qid", "Q243");
        assertThat(jdbc.queryForObject("SELECT content_hash FROM cl_schema.silhouette_game_question WHERE iso2 = 'FR'", String.class))
                .isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cl_schema.silhouette_game_hint", Integer.class))
                .isEqualTo(197 * 3);
    }
}
