package io.clueterra.questions.flaggame.service;

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
import io.clueterra.questions.flaggame.repository.FlagCatalogRepository;
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
class FlagGenerationIntegrationTest {

    private static final int POOL_SIZE = 197;

    @Autowired
    private JdbcTemplate jdbc;

    private FlagGenerationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbc);
        service = new FlagGenerationService(mock(AdminOperationRunner.class), new CountryCatalogReader(mapper),
                new CountryCatalogRepository(named), new CountryFactValidator(),
                new FlagQuestionBuilder(new CountryTiers(), mapper),
                new FlagCatalogRepository(named, mapper), mock(ConsoleLogWriter.class));
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
    void generatesEveryFlagWithRegionInitialAndVerifiedCapitalClues() throws Exception {
        service.generate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cl_schema.flag_game_question WHERE is_active", Integer.class))
                .isEqualTo(POOL_SIZE);
        assertThat(jdbc.queryForList("""
                SELECT hint.kind FROM cl_schema.flag_game_hint hint
                JOIN cl_schema.flag_game_question question ON question.id = hint.question_id
                WHERE question.iso2 = 'FR' ORDER BY hint.position
                """, String.class)).containsExactly("REGION", "INITIAL", "CAPITAL");
        assertThat(jdbc.queryForList("""
                SELECT hint.kind FROM cl_schema.flag_game_hint hint
                JOIN cl_schema.flag_game_question question ON question.id = hint.question_id
                WHERE question.iso2 = 'DE' ORDER BY hint.position
                """, String.class)).containsExactly("REGION", "INITIAL");
        assertThat(jdbc.queryForMap("""
                SELECT hint.payload -> 'letters' ->> 'en' AS english, hint.payload -> 'letters' ->> 'ru' AS russian
                FROM cl_schema.flag_game_hint hint
                JOIN cl_schema.flag_game_question question ON question.id = hint.question_id
                WHERE question.iso2 = 'FR' AND hint.kind = 'INITIAL'
                """))
                .containsEntry("english", "F")
                .containsEntry("russian", "Ф");
        assertThat(jdbc.queryForObject("""
                SELECT hint.payload -> 'capitals' -> 0 ->> 'en' FROM cl_schema.flag_game_hint hint
                JOIN cl_schema.flag_game_question question ON question.id = hint.question_id
                WHERE question.iso2 = 'FR' AND hint.kind = 'CAPITAL'
                """, String.class)).isEqualTo("Paris");

        String hash = jdbc.queryForObject("SELECT content_hash FROM cl_schema.flag_game_question WHERE iso2 = 'FR'", String.class);
        service.generate();
        assertThat(jdbc.queryForObject("SELECT content_hash FROM cl_schema.flag_game_question WHERE iso2 = 'FR'", String.class))
                .isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cl_schema.flag_game_question", Integer.class))
                .isEqualTo(POOL_SIZE);
    }
}
