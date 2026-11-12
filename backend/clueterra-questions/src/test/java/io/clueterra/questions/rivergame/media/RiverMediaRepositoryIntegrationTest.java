package io.clueterra.questions.rivergame.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.TestcontainersConfiguration;
import io.clueterra.questions.image.ImageAssetOwnerType;
import io.clueterra.questions.image.ImageAssetRepository;
import io.clueterra.questions.image.ImageAuditCandidate;
import io.clueterra.questions.image.MediaGarbageCollectionRepository;
import io.clueterra.questions.rivergame.catalog.RiverCatalogCourse;
import io.clueterra.questions.rivergame.catalog.RiverCatalogEntry;
import io.clueterra.questions.rivergame.catalog.RiverCatalogFact;
import io.clueterra.questions.rivergame.catalog.RiverCatalogHint;
import io.clueterra.questions.rivergame.catalog.RiverCatalogPlace;
import io.clueterra.questions.rivergame.generation.RiverCatalogPreparedEntry;
import io.clueterra.questions.rivergame.generation.RiverCatalogRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

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
@Sql("/sql/budget-media-owner-stubs.sql")
class RiverMediaRepositoryIntegrationTest {

    private static final String SOURCE = "https://www.wikidata.org/wiki/Q123";
    private static final RiverCatalogPlace BONN = new RiverCatalogPlace("Bonn", 7.1, 50.73);
    private static final RiverCatalogCourse COURSE = new RiverCatalogCourse(List.of(7.0, 47.5, 8.5, 51.9),
            List.of(List.of(List.of(8.5, 47.5), List.of(7.6, 49.7), List.of(7.0, 51.9))));
    private static final String PAGE = "https://commons.wikimedia.org/wiki/File:Sample_River_Valley.jpg";
    private static final String ORIGINAL = "https://upload.wikimedia.org/wikipedia/commons/a/ab/Sample_River_Valley.jpg";
    private static final String OBJECT_KEY = "river-photos/" + "e".repeat(64) + ".jpg";

    @Autowired
    private JdbcTemplate jdbc;

    private NamedParameterJdbcTemplate namedJdbc;
    private RiverMediaRepository repository;

    @BeforeEach
    void seedRiver() {
        namedJdbc = new NamedParameterJdbcTemplate(jdbc.getDataSource());
        repository = new RiverMediaRepository(namedJdbc, new ObjectMapper());
        new RiverCatalogRepository(namedJdbc, new ObjectMapper())
                .replaceCountry("DE", List.of(new RiverCatalogPreparedEntry(entry(), 1000, 1200)));
    }

    @Test
    void savesAttributedPhotoAndExposesItToImageMaintenance() {
        repository.save("q123", "https://en.wikipedia.org/wiki/Sample_River", Optional.of(photo()));

        assertThat(repository.findActiveRiverKeys()).contains("q123");
        assertThat(jdbc.queryForMap("""
                SELECT media.wikipedia_url, resource.provider, resource.lifecycle_status,
                       rights.author_name, rights.license_label, rendition.byte_size, rendition.content_sha256
                FROM cl_schema.river_game_river_media media
                JOIN cl_media_schema.image_resource resource ON resource.id = media.photo_resource_id
                JOIN cl_media_schema.image_rights_revision rights ON rights.id = resource.current_rights_revision_id
                JOIN cl_media_schema.image_rendition rendition ON rendition.image_resource_id = resource.id
                WHERE media.river_key = 'q123'
                """))
                .containsEntry("wikipedia_url", "https://en.wikipedia.org/wiki/Sample_River")
                .containsEntry("provider", "WIKIMEDIA_COMMONS")
                .containsEntry("lifecycle_status", "ACTIVE")
                .containsEntry("author_name", "Jane Doe")
                .containsEntry("license_label", "CC BY-SA 4.0")
                .containsEntry("byte_size", 120_000L)
                .containsEntry("content_sha256", "f".repeat(64));
        assertThat(repository.findPhotoLifecycleStatus(ORIGINAL, PAGE)).contains("ACTIVE");

        ImageAuditCandidate candidate = new ImageAssetRepository(namedJdbc, new ObjectMapper())
                .findAuditCandidates().stream()
                .filter(audit -> OBJECT_KEY.equals(audit.objectPath()))
                .findFirst().orElseThrow();
        assertThat(candidate.ownerType()).isEqualTo(ImageAssetOwnerType.RIVER_PHOTO);
        assertThat(candidate.ownerId()).isEqualTo(123L);
        assertThat(candidate.imageSourcePageUrl()).isEqualTo(PAGE);

        new MediaGarbageCollectionRepository(namedJdbc).refreshOrphanState();
        assertThat(jdbc.queryForObject("SELECT orphaned_at IS NULL FROM cl_media_schema.image_rendition "
                + "WHERE object_key = ?", Boolean.class, OBJECT_KEY)).isTrue();
    }

    @Test
    void savingWithoutPhotoDetachesThePreviousPhotoSoItBecomesOrphaned() {
        repository.save("q123", null, Optional.of(photo()));
        repository.save("q123", null, Optional.empty());

        assertThat(jdbc.queryForObject("SELECT photo_resource_id FROM cl_schema.river_game_river_media "
                + "WHERE river_key = 'q123'", Long.class)).isNull();
        new MediaGarbageCollectionRepository(namedJdbc).refreshOrphanState();
        assertThat(jdbc.queryForObject("SELECT orphaned_at IS NOT NULL FROM cl_media_schema.image_rendition "
                + "WHERE object_key = ?", Boolean.class, OBJECT_KEY)).isTrue();
    }

    private RiverPhoto photo() {
        return new RiverPhoto(OBJECT_KEY, ORIGINAL, PAGE, "Jane Doe", "Jane Doe", "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0", "1".repeat(40), "image/jpeg",
                4000, 3000, 120_000L, "f".repeat(64));
    }

    private RiverCatalogEntry entry() {
        return new RiverCatalogEntry("DE", "q123", "Sample River", List.of("Sample River"), 1, 100.0,
                "river-game/" + "a".repeat(64) + ".webp", "river-game/" + "b".repeat(64) + ".webp", COURSE,
                SOURCE, "Natural Earth", SOURCE,
                List.of(new RiverCatalogHint(1, "GEOGRAPHY", "Its waters flow into the North Sea.", SOURCE, null),
                        new RiverCatalogHint(2, "CITY", "Bonn lies on its banks.", SOURCE, BONN),
                        new RiverCatalogHint(3, "NAME", "Its name starts with “S” and has 6 letters.", SOURCE, null)),
                List.of(new RiverCatalogFact("LENGTH", "Its reported length is about 100 km.", null),
                        new RiverCatalogFact("CITY", "Bonn lies on its banks.", BONN)),
                "b".repeat(64));
    }
}
