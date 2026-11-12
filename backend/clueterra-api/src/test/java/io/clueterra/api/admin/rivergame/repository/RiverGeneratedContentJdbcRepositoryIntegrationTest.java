package io.clueterra.api.admin.rivergame.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.api.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
class RiverGeneratedContentJdbcRepositoryIntegrationTest {

    @Autowired private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_schema.river_game_question, cl_schema.countries CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.countries (id, iso_alpha2, name_en)
                VALUES (10, 'DE', 'Germany'), (20, 'FR', 'France')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.river_game_question
                    (id, country_id, river_key, name_en, rank_in_country, length_km,
                     plain_image_key, borders_image_key, is_active)
                VALUES (101, 10, 'rhine', 'Rhine', 1, 1230.0,
                        'river-game/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.webp',
                        'river-game/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.webp', TRUE),
                       (102, 10, 'danube', 'Danube', 2, 2850.0,
                        'river-game/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.webp',
                        'river-game/dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd.webp', FALSE),
                       (201, 20, 'seine', 'Seine', 1, 777.0,
                        'river-game/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee.webp',
                        'river-game/ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff.webp', TRUE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.river_game_hint (question_id, position, kind, hint_text, source_url)
                VALUES (101, 1, 'LENGTH', 'About 1,230 km', 'https://example.com/length'),
                       (101, 2, 'COUNTRIES', 'Crosses several countries', 'https://example.com/countries')
                """);
    }

    @Test
    void filtersAndPaginatesQuestionsWithMapsAndEvidence() {
        RiverGeneratedContentJdbcRepository repository = new RiverGeneratedContentJdbcRepository(jdbc, new ObjectMapper());

        assertThat(repository.countQuestions("DE", "", null)).isEqualTo(2);
        assertThat(repository.countQuestions("", "", null)).isEqualTo(3);
        assertThat(repository.countQuestions("DE' OR 1=1 --", "", null)).isZero();
        assertThat(repository.findQuestions("DE' OR 1=1 --", "", null, "COUNTRY", 10, 0)).isEmpty();
        var first = repository.findQuestions("DE", "", null, "COUNTRY", 1, 0);
        var second = repository.findQuestions("DE", "", null, "COUNTRY", 1, 1);

        assertThat(first).hasSize(1);
        assertThat(first.getFirst().name()).isEqualTo("Rhine");
        assertThat(first.getFirst().plainImageUrl()).isEqualTo("/assets/rivers/" + "a".repeat(64) + ".webp");
        assertThat(first.getFirst().hints()).extracting("position").containsExactly(1, 2);
        assertThat(first.getFirst().hints().getFirst().sourceUrl()).isEqualTo("https://example.com/length");
        assertThat(second).hasSize(1);
        assertThat(second.getFirst().name()).isEqualTo("Danube");
        assertThat(second.getFirst().active()).isFalse();
        assertThat(second.getFirst().hints()).isEqualTo(List.of());

        assertThat(repository.countQuestions("", "Rhi", true)).isEqualTo(1);
        assertThat(repository.findQuestions("", "", null, "LONGEST", 3, 0))
                .extracting("name").containsExactly("Danube", "Rhine", "Seine");
        assertThat(repository.findQuestions("", "", false, "COUNTRY", 3, 0))
                .extracting("name").containsExactly("Danube");
    }
}
