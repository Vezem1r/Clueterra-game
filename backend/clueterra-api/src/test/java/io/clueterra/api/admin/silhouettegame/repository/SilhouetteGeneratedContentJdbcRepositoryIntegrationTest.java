package io.clueterra.api.admin.silhouettegame.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.api.TestcontainersConfiguration;
import io.clueterra.api.admin.silhouettegame.dto.SilhouetteQuestionPreviewDto;
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
class SilhouetteGeneratedContentJdbcRepositoryIntegrationTest {

    @Autowired private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_api_schema.users CASCADE");
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_schema.silhouette_game_hint, cl_schema.silhouette_game_question CASCADE");
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.users (id, email, username, password_hash, role, enabled, is_guest)
                VALUES (1, 'a@example.com', 'player_a', 'hash', 'USER', TRUE, FALSE),
                       (2, 'b@example.com', 'player_b', 'hash', 'USER', TRUE, FALSE)
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.silhouette_game_question (id, iso2, tier, names, is_active, facts)
                VALUES (1, 'FR', 1, '{"en":"France","ru":"Франция","cs":"Francie"}', TRUE, '{"landlocked":false}'),
                       (2, 'LI', 5, '{"en":"Liechtenstein","ru":"Лихтенштейн","cs":"Lichtenštejnsko"}', TRUE, '{}'),
                       (3, 'TV', 5, '{"en":"Tuvalu","ru":"Тувалу","cs":"Tuvalu"}', FALSE, '{}')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_schema.silhouette_game_hint (question_id, position, kind, cost_units, payload)
                VALUES (1, 1, 'REGION', 1, '{"subregion":"Western Europe"}'),
                       (1, 2, 'LOCATION', 1, '{}'),
                       (1, 3, 'CAPITAL', 2, '{"capitals":[{"en":"Paris"}]}'),
                       (1, 4, 'LANDMARK', 2, '{}')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO cl_api_schema.silhouette_game_progress
                    (user_id, question_id, first_attempt_at, first_solved_at, attempt_count, skip_count, best_hints_used)
                VALUES (1, 1, NOW(), NOW(), 2, 0, 1),
                       (2, 1, NOW(), NOW(), 1, 1, 3),
                       (1, 2, NULL, NULL, 0, 3, NULL)
                """);
    }

    @Test
    void listsQuestionsWithCluesAndHowPlayersDidOnThem() {
        SilhouetteGeneratedContentJdbcRepository repository =
                new SilhouetteGeneratedContentJdbcRepository(jdbc, new ObjectMapper());

        SilhouetteQuestionPreviewDto france = repository.findQuestions("FR", "", null, null, "ALL", "COUNTRY", 10, 0).getFirst();
        assertThat(france.name()).isEqualTo("France");
        assertThat(france.hints()).extracting("kind").containsExactly("REGION", "LOCATION", "CAPITAL", "LANDMARK");
        assertThat(france.hints().get(2).payload().path("capitals").path(0).path("en").asText()).isEqualTo("Paris");
        assertThat(france.players()).isEqualTo(2);
        assertThat(france.solved()).isEqualTo(2);
        assertThat(france.skips()).isEqualTo(1);
        assertThat(france.averageHints()).isEqualTo(2.0);
        assertThat(france.photoUrl()).isNull();

        assertThat(repository.findQuestions("", "", true, null, "ALL", "COUNTRY", 10, 0)).hasSize(2);
        assertThat(repository.findQuestions("", "лихт", null, null, "ALL", "COUNTRY", 10, 0)).hasSize(1);
        assertThat(repository.findQuestions("", "", null, 5, "ALL", "COUNTRY", 10, 0)).hasSize(2);
        assertThat(repository.findQuestions("FR' OR 1=1 --", "", null, null, "ALL", "COUNTRY", 10, 0)).isEmpty();
        assertThat(repository.findQuestions("", "", null, null, "ALL", "MOST_SKIPPED", 3, 0))
                .extracting("countryCode").startsWith("LI");
        assertThat(repository.findSummary()).satisfies(summary -> {
            assertThat(summary.activeQuestions()).isEqualTo(2);
            assertThat(summary.landmarkClues()).isEqualTo(1);
            assertThat(summary.players()).isEqualTo(2);
            assertThat(summary.solves()).isEqualTo(2);
            assertThat(summary.guesses()).isEqualTo(3);
        });
    }

    @Test
    void showsTheLandmarkPhotoAndHandsAManualChoiceBackToTheSync() {
        SilhouetteGeneratedContentJdbcRepository repository =
                new SilhouetteGeneratedContentJdbcRepository(jdbc, new ObjectMapper());
        jdbc.getJdbcTemplate().execute("""
                TRUNCATE cl_media_schema.image_rendition, cl_media_schema.image_rights_revision,
                    cl_media_schema.image_resource CASCADE;
                INSERT INTO cl_media_schema.image_resource (id, provider, source_key) VALUES (10, 'WIKIMEDIA_COMMONS', 'eiffel');
                INSERT INTO cl_media_schema.image_rights_revision
                    (id, image_resource_id, source_page_url, author_name, license_label, width, height)
                VALUES (20, 10, 'https://commons.wikimedia.org/wiki/File:Eiffel.jpg', 'Benh', 'CC BY-SA 3.0', 2900, 5367);
                UPDATE cl_media_schema.image_resource SET current_rights_revision_id = 20 WHERE id = 10;
                INSERT INTO cl_media_schema.image_rendition (id, image_resource_id, rights_revision_id, storage_bucket, object_key)
                VALUES (30, 10, 20, 'hint-images', 'country-photos/abc.jpg');
                UPDATE cl_schema.silhouette_game_question
                SET photo_resource_id = 10, landmark_qid = 'Q243', landmark_names = '{"en":"Eiffel Tower"}',
                    landmark_sitelinks = 191, photo_selection = 'MANUAL', photo_selected_by = 'admin'
                WHERE iso2 = 'FR';
                """);

        SilhouetteQuestionPreviewDto france = repository.findQuestion("FR").orElseThrow();
        assertThat(france.photoUrl()).isEqualTo("/api/assets/countries/photos/abc.jpg");
        assertThat(france.photoLicenseLabel()).isEqualTo("CC BY-SA 3.0");
        assertThat(france.photoWidth()).isEqualTo(2900);
        assertThat(france.landmarkName()).isEqualTo("Eiffel Tower");
        assertThat(france.landmarkSitelinks()).isEqualTo(191);
        assertThat(france.photoSelection()).isEqualTo("MANUAL");
        assertThat(repository.findQuestions("", "", null, null, "MISSING", "COUNTRY", 10, 0))
                .extracting(SilhouetteQuestionPreviewDto::countryCode).containsExactly("LI", "TV");
        assertThat(repository.findQuestions("", "", null, null, "MANUAL", "COUNTRY", 10, 0))
                .extracting(SilhouetteQuestionPreviewDto::countryCode).containsExactly("FR");

        assertThat(repository.release("FR")).isTrue();
        assertThat(repository.release("FR")).isFalse();
        assertThat(repository.findQuestion("FR").orElseThrow())
                .extracting(SilhouetteQuestionPreviewDto::photoSelection, SilhouetteQuestionPreviewDto::photoSelectedBy)
                .containsExactly("AUTO", null);
    }
}
