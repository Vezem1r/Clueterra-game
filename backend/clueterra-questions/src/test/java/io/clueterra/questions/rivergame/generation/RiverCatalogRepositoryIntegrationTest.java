package io.clueterra.questions.rivergame.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.TestcontainersConfiguration;
import io.clueterra.questions.image.MediaGarbageCollectionRepository;
import io.clueterra.questions.rivergame.catalog.RiverCatalogCourse;
import io.clueterra.questions.rivergame.catalog.RiverCatalogEntry;
import io.clueterra.questions.rivergame.catalog.RiverCatalogFact;
import io.clueterra.questions.rivergame.catalog.RiverCatalogHint;
import io.clueterra.questions.rivergame.catalog.RiverCatalogPlace;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
class RiverCatalogRepositoryIntegrationTest {

    private static final String SOURCE = "https://www.wikidata.org/wiki/Q123";
    private static final RiverCatalogPlace BONN = new RiverCatalogPlace("Bonn", 7.1, 50.73);
    private static final RiverCatalogCourse COURSE = new RiverCatalogCourse(List.of(7.0, 47.5, 8.5, 51.9),
            List.of(List.of(List.of(8.5, 47.5), List.of(7.6, 49.7), List.of(7.0, 51.9))));

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void importsAliasesAndHintsThenSafelySwapsRanks() {
        RiverCatalogRepository repository = new RiverCatalogRepository(
                new NamedParameterJdbcTemplate(jdbc.getDataSource()), new ObjectMapper());
        assertThat(repository.findMissingCountryCodes(Set.of("DE", "ZZ"))).containsExactly("ZZ");
        assertThat(repository.replaceCountry("DE", List.of(prepared("q123", 1), prepared("q456", 2))))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cl_schema.river_game_hint WHERE question_id IN "
                        + "(SELECT id FROM cl_schema.river_game_question WHERE river_key IN ('q123', 'q456'))",
                Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT answer_aliases[1] FROM cl_schema.river_game_question WHERE river_key = 'q123'",
                String.class)).isEqualTo("Sample River");
        assertThat(jdbc.queryForObject(
                "SELECT course -> 'coordinates' -> 0 -> 1 ->> 1 FROM cl_schema.river_game_question WHERE river_key = 'q123'",
                String.class)).isEqualTo("49.7");
        assertThat(jdbc.queryForObject(
                "SELECT hint.place ->> 'name' FROM cl_schema.river_game_hint hint JOIN cl_schema.river_game_question question "
                        + "ON question.id = hint.question_id WHERE question.river_key = 'q123' AND hint.position = 2",
                String.class)).isEqualTo("Bonn");
        assertThat(jdbc.queryForObject(
                "SELECT jsonb_array_length(facts) FROM cl_schema.river_game_question WHERE river_key = 'q123'",
                Integer.class)).isEqualTo(2);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cl_media_schema.image_resource WHERE source_key LIKE 'river-game:%'",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cl_media_schema.image_rendition WHERE object_key LIKE 'river-game/%'",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForMap("SELECT format, mime_type, width, height "
                + "FROM cl_media_schema.image_rendition WHERE object_key = ?", imageKey("q123", false)))
                .containsEntry("format", "webp")
                .containsEntry("mime_type", "image/webp")
                .containsEntry("width", 1800)
                .containsEntry("height", 1200);
        assertThat(repository.replaceCountry("DE", List.of(prepared("q123", 1), prepared("q456", 2))))
                .isZero();

        jdbc.update("UPDATE cl_media_schema.image_rendition SET storage_status = 'DELETED' "
                + "WHERE object_key = ?", imageKey("q123", false));
        assertThat(repository.replaceCountry("DE", List.of(prepared("q123", 1), prepared("q456", 2))))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT storage_status FROM cl_media_schema.image_rendition "
                + "WHERE object_key = ?", String.class, imageKey("q123", false)))
                .isEqualTo("AVAILABLE");

        assertThat(repository.replaceCountry("DE", List.of(prepared("q123", 2), prepared("q456", 1))))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT rank_in_country FROM cl_schema.river_game_question WHERE river_key = 'q123'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cl_schema.river_game_question WHERE river_key IN ('q123', 'q456') "
                        + "AND is_active", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cl_media_schema.image_rendition WHERE object_key LIKE 'river-game/%'",
                Integer.class)).isEqualTo(4);
    }

    @Test
    void activeMapsStayRegisteredAndRetiredQuestionMapsLoseActiveReferences() {
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbc.getDataSource());
        RiverCatalogRepository repository = new RiverCatalogRepository(namedJdbc, new ObjectMapper());
        MediaGarbageCollectionRepository garbageCollection = new MediaGarbageCollectionRepository(namedJdbc);
        repository.replaceCountry("DE", List.of(prepared("q123", 1), prepared("q456", 2)));

        assertThat(garbageCollection.findRegisteredObjectKeys("hint-images"))
                .contains(imageKey("q123", false), imageKey("q456", false));

        repository.replaceCountry("DE", List.of(prepared("q123", 1)));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM cl_media_schema.image_rendition rendition
                JOIN cl_schema.river_game_question question
                  ON rendition.image_resource_id IN
                     (question.plain_image_resource_id, question.borders_image_resource_id)
                WHERE question.river_key = 'q123' AND question.is_active
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM cl_media_schema.image_rendition rendition
                JOIN cl_schema.river_game_question question
                  ON rendition.image_resource_id IN
                     (question.plain_image_resource_id, question.borders_image_resource_id)
                WHERE question.river_key = 'q456' AND question.is_active
                """, Integer.class)).isZero();
    }

    private RiverCatalogPreparedEntry prepared(String riverKey, int rank) {
        return new RiverCatalogPreparedEntry(entry(riverKey, rank), 1000, 1200);
    }

    private RiverCatalogEntry entry(String riverKey, int rank) {
        return new RiverCatalogEntry("DE", riverKey, "Sample River", List.of("Sample River"), rank, 100.0,
                imageKey(riverKey, false), imageKey(riverKey, true), COURSE,
                SOURCE, "Natural Earth", SOURCE,
                List.of(new RiverCatalogHint(1, "GEOGRAPHY", "Its waters flow into the North Sea.", SOURCE, null),
                        new RiverCatalogHint(2, "CITY", "Bonn lies on its banks.", SOURCE, BONN),
                        new RiverCatalogHint(3, "NAME", "Its name starts with “S” and has 6 letters.", SOURCE, null)),
                List.of(new RiverCatalogFact("LENGTH", "Its reported length is about 100 km.", null),
                        new RiverCatalogFact("CITY", "Bonn lies on its banks.", BONN)),
                rank == 1 ? "b".repeat(64) : "c".repeat(64));
    }

    private String imageKey(String riverKey, boolean borders) {
        char digit = riverKey.equals("q123") ? (borders ? 'b' : 'a') : (borders ? 'd' : 'c');
        return "river-game/" + String.valueOf(digit).repeat(64) + ".webp";
    }
}
