package io.clueterra.questions.countryphotos.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.TestcontainersConfiguration;
import io.clueterra.questions.countryphotos.model.CountryPhoto;
import io.clueterra.questions.image.ImageAssetOwnerType;
import io.clueterra.questions.image.ImageAssetRepository;
import io.clueterra.questions.image.ImageAuditCandidate;
import io.clueterra.questions.image.MediaGarbageCollectionRepository;

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
class CountryPhotoRepositoryIntegrationTest {

    private static final String PAGE = "https://commons.wikimedia.org/wiki/File:Tour_Eiffel_Wikimedia_Commons.jpg";
    private static final String ORIGINAL = "https://upload.wikimedia.org/wikipedia/commons/a/a8/Tour_Eiffel_Wikimedia_Commons.jpg";
    private static final String OBJECT_KEY = "country-photos/" + "c".repeat(64) + ".jpg";

    @Autowired
    private JdbcTemplate jdbc;

    private NamedParameterJdbcTemplate namedJdbc;
    private CountryPhotoRepository repository;

    @BeforeEach
    void setUp() {
        namedJdbc = new NamedParameterJdbcTemplate(jdbc.getDataSource());
        repository = new CountryPhotoRepository(namedJdbc, new ObjectMapper());
        jdbc.update("""
                INSERT INTO cl_schema.silhouette_game_question (country_id, iso2, tier, region, subregion, center_lng, center_lat,
                    bounds, names, shape_path, flag_colors, facts, content_hash, is_active)
                SELECT country.id, 'FR', 1, 'Europe', 'Western Europe', 2.2, 46.2, '[]', '{"en":"France"}', 'M0 0Z', '[]',
                       '{}', REPEAT('a', 64), TRUE
                FROM cl_schema.countries country WHERE country.iso_alpha2 = 'FR'
                """);
    }

    @Test
    void savesTheLandmarkPhotoWithItsNamesAndKeepsItInImageMaintenance() {
        repository.save("FR", Optional.of(photo()));

        assertThat(jdbc.queryForMap("""
                SELECT question.landmark_qid, question.landmark_sitelinks, question.landmark_names ->> 'ru' AS name_ru,
                       resource.lifecycle_status, rights.author_name, rights.license_label
                FROM cl_schema.silhouette_game_question question
                JOIN cl_media_schema.image_resource resource ON resource.id = question.photo_resource_id
                JOIN cl_media_schema.image_rights_revision rights ON rights.id = resource.current_rights_revision_id
                WHERE question.iso2 = 'FR'
                """))
                .containsEntry("landmark_qid", "Q243")
                .containsEntry("landmark_sitelinks", 191)
                .containsEntry("name_ru", "Эйфелева башня")
                .containsEntry("lifecycle_status", "ACTIVE")
                .containsEntry("author_name", "Benh LIEU SONG")
                .containsEntry("license_label", "CC BY-SA 3.0");
        assertThat(repository.findPhotoLifecycleStatus(ORIGINAL, PAGE)).contains("ACTIVE");

        Optional<ImageAuditCandidate> audit = new ImageAssetRepository(namedJdbc, new ObjectMapper())
                .findAuditCandidates().stream()
                .filter(candidate -> OBJECT_KEY.equals(candidate.objectPath()))
                .findFirst();
        assertThat(audit).isPresent();
        assertThat(audit.get().ownerType()).isEqualTo(ImageAssetOwnerType.COUNTRY_PHOTO);
        assertThat(audit.get().countryCode()).isEqualTo("FR");

        new MediaGarbageCollectionRepository(namedJdbc).refreshOrphanState();
        assertThat(jdbc.queryForObject("SELECT orphaned_at IS NULL FROM cl_media_schema.image_rendition "
                + "WHERE object_key = ?", Boolean.class, OBJECT_KEY)).isTrue();
    }

    @Test
    void savingWithoutPhotoDetachesThePreviousPhoto() {
        repository.save("FR", Optional.of(photo()));
        repository.save("FR", Optional.empty());

        assertThat(jdbc.queryForObject("SELECT photo_resource_id FROM cl_schema.silhouette_game_question "
                + "WHERE iso2 = 'FR'", Long.class)).isNull();
        new MediaGarbageCollectionRepository(namedJdbc).refreshOrphanState();
        assertThat(jdbc.queryForObject("SELECT orphaned_at IS NOT NULL FROM cl_media_schema.image_rendition "
                + "WHERE object_key = ?", Boolean.class, OBJECT_KEY)).isTrue();
    }

    @Test
    void theSyncLeavesAManualChoiceAloneUntilItIsReleased() {
        jdbc.queryForObject("SELECT cl_schema.save_silhouette_game_photo('FR', NULL, 'admin')", Boolean.class);

        assertThat(repository.save("FR", Optional.of(photo()))).isFalse();
        assertThat(repository.findManualCountries()).containsExactly("FR");
        assertThat(jdbc.queryForMap("SELECT photo_resource_id, photo_selection, photo_selected_by "
                + "FROM cl_schema.silhouette_game_question WHERE iso2 = 'FR'"))
                .containsEntry("photo_resource_id", null)
                .containsEntry("photo_selection", "MANUAL")
                .containsEntry("photo_selected_by", "admin");

        jdbc.update("UPDATE cl_schema.silhouette_game_question SET photo_selection = 'AUTO', photo_selected_by = NULL "
                + "WHERE iso2 = 'FR'");
        assertThat(repository.save("FR", Optional.of(photo()))).isTrue();
        assertThat(repository.findManualCountries()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT photo_resource_id IS NOT NULL FROM cl_schema.silhouette_game_question "
                + "WHERE iso2 = 'FR'", Boolean.class)).isTrue();
    }

    private CountryPhoto photo() {
        return new CountryPhoto(OBJECT_KEY, ORIGINAL, PAGE, "Benh LIEU SONG", "Benh LIEU SONG", "CC BY-SA 3.0",
                "https://creativecommons.org/licenses/by-sa/3.0", "2".repeat(40), "image/jpeg",
                2900, 5367, 240_000L, "d".repeat(64), "Q243",
                Map.of("en", "Eiffel Tower", "ru", "Эйфелева башня", "cs", "Eiffelova věž"), 191);
    }
}
