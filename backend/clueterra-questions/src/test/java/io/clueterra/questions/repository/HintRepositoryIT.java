package io.clueterra.questions.repository;

import io.clueterra.questions.TestcontainersConfiguration;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.support.ClockTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=cl_schema,cl_ai_schema,cl_sch_schema,cl_staging",
        "spring.flyway.default-schema=cl_schema",
        "spring.flyway.create-schemas=true",
        "spring.datasource.hikari.connection-init-sql=SET search_path TO cl_schema, cl_ai_schema, cl_sch_schema, cl_staging"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ClockTestConfiguration.class, HintRepository.class})
class HintRepositoryIT {

    private static final long COUNTRY_ID = 1001L;

    @Autowired
    private HintRepository hintRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUpCountry() {
        jdbc.update("""
                INSERT INTO countries (id, iso_alpha2, iso_alpha3, name_en, difficulty_modifier, is_active, created_at, updated_at)
                VALUES (:id, :iso2, :iso3, :name, :difficultyModifier, true, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("id", COUNTRY_ID)
                .addValue("iso2", "XZ")
                .addValue("iso3", "XZZ")
                .addValue("name", "Hint Repository Test Country")
                .addValue("difficultyModifier", BigDecimal.ONE)
                .addValue("createdAt", Timestamp.from(ClockTestConfiguration.FIXED_INSTANT))
                .addValue("updatedAt", Timestamp.from(ClockTestConfiguration.FIXED_INSTANT)));
    }

    @Test
    void saveWithArchive_softUpdatesWhenContentHashMatches() {
        Hint initial = buildHint("hash-same", "Original text", 24, new BigDecimal("0.80"), 4);
        Hint updated = buildHint("hash-same", "Original text", 37, new BigDecimal("0.91"), 2);

        assertThat(hintRepository.saveWithArchive(initial)).isEqualTo(HintUpsertAction.INSERTED);
        assertThat(hintRepository.saveWithArchive(updated)).isEqualTo(HintUpsertAction.SOFT_UPDATED);

        assertThat(hintRepository.findActiveByCountryId(COUNTRY_ID)).singleElement().satisfies(hint -> {
            assertThat(hint.getHintText()).isEqualTo("Original text");
            assertThat(hint.getDifficulty()).isEqualTo(37);
            assertThat(hint.getQualityScore()).isEqualByComparingTo("0.91");
            assertThat(hint.getAmbiguityCount()).isEqualTo(2);
            assertThat(hint.getCreatedAt()).isEqualTo(ClockTestConfiguration.FIXED_INSTANT);
            assertThat(hint.getUpdatedAt()).isEqualTo(ClockTestConfiguration.FIXED_INSTANT);
        });
        assertThat(countRows("hints_history")).isZero();
    }

    @Test
    void saveWithArchive_softUpdatesWhenExactHintTextMatchesLegacyHash() {
        Hint initial = buildHint("legacy-hash", "Original text", 24, new BigDecimal("0.80"), 4);
        Hint updated = buildHint("exact-render-hash", "Original text", 37, new BigDecimal("0.91"), 2);

        assertThat(hintRepository.saveWithArchive(initial)).isEqualTo(HintUpsertAction.INSERTED);
        assertThat(hintRepository.saveWithArchive(updated)).isEqualTo(HintUpsertAction.SOFT_UPDATED);

        assertThat(hintRepository.findActiveByCountryId(COUNTRY_ID)).singleElement().satisfies(hint -> {
            assertThat(hint.getHintText()).isEqualTo("Original text");
            assertThat(hint.getContentHash()).isEqualTo("exact-render-hash");
            assertThat(hint.getDifficulty()).isEqualTo(37);
            assertThat(hint.getQualityScore()).isEqualByComparingTo("0.91");
            assertThat(hint.getAmbiguityCount()).isEqualTo(2);
        });
        assertThat(countRows("hints_history")).isZero();
    }

    @Test
    void saveWithArchive_replacesAndArchivesWhenContentHashChanges() {
        Hint initial = buildHint("hash-old", "Old text", 24, new BigDecimal("0.80"), 4);
        Hint replacement = buildHint("hash-new", "New text", 31, new BigDecimal("0.95"), 1);

        assertThat(hintRepository.saveWithArchive(initial)).isEqualTo(HintUpsertAction.INSERTED);
        assertThat(hintRepository.saveWithArchive(replacement)).isEqualTo(HintUpsertAction.REPLACED);

        assertThat(hintRepository.findActiveByCountryId(COUNTRY_ID)).singleElement().satisfies(hint -> {
            assertThat(hint.getHintText()).isEqualTo("New text");
            assertThat(hint.getContentHash()).isEqualTo("hash-new");
            assertThat(hint.getDifficulty()).isEqualTo(31);
        });

        assertThat(countRows("hints_history")).isEqualTo(1);
        ArchivedHint archived = jdbc.queryForObject("""
                SELECT hint_text, content_hash, archive_reason, archived_at
                FROM hints_history
                WHERE country_id = :countryId
                """, Map.of("countryId", COUNTRY_ID), (rs, rowNum) -> new ArchivedHint(
                rs.getString("hint_text"),
                rs.getString("content_hash"),
                rs.getString("archive_reason"),
                rs.getTimestamp("archived_at").toInstant()
        ));

        assertThat(archived).isNotNull();
        assertThat(archived.hintText()).isEqualTo("Old text");
        assertThat(archived.contentHash()).isEqualTo("hash-old");
        assertThat(archived.archiveReason()).isEqualTo("replaced");
        assertThat(archived.archivedAt()).isEqualTo(ClockTestConfiguration.FIXED_INSTANT);
    }

    @Test
    void saveWithArchive_preservesEditorialTextWithoutRetryDate() {
        Hint initial = buildHint("hash-old", "Source text", 24, new BigDecimal("0.80"), 4);
        assertThat(hintRepository.saveWithArchive(initial)).isEqualTo(HintUpsertAction.INSERTED);
        jdbc.update("UPDATE hints SET hint_text = 'Editor text', editorial_text_locked = TRUE WHERE id = :id",
                Map.of("id", initial.getId()));

        Hint replacement = buildHint("hash-new", "Regenerated source text", 31, new BigDecimal("0.95"), 1);
        assertThat(hintRepository.saveWithArchive(replacement)).isEqualTo(HintUpsertAction.NOOP);
        assertThat(hintRepository.findById(initial.getId())).get().satisfies(hint -> {
            assertThat(hint.getHintText()).isEqualTo("Editor text");
            assertThat(hint.getContentHash()).isEqualTo("hash-old");
        });
        assertThat(countRows("hints_history")).isZero();
    }

    @Test
    void saveWithArchive_refreshesBudgetImageWithoutReplacingEditorialText() {
        Hint initial = budgetImageHint("budget-projects/Q1/old.jpg", "Source text");
        assertThat(hintRepository.saveWithArchive(initial)).isEqualTo(HintUpsertAction.INSERTED);
        Long previousImageId = hintRepository.findById(initial.getId()).orElseThrow().getImageResourceId();
        jdbc.update("UPDATE hints SET hint_text = 'Editor text', editorial_text_locked = TRUE "
                        + "WHERE id = :id", Map.of("id", initial.getId()));

        Hint refreshed = budgetImageHint("budget-projects/Q1/new.jpg", "Regenerated source text");
        assertThat(hintRepository.saveWithArchive(refreshed)).isEqualTo(HintUpsertAction.SOFT_UPDATED);
        assertThat(hintRepository.findById(initial.getId())).get().satisfies(hint -> {
            assertThat(hint.getHintText()).isEqualTo("Editor text");
            assertThat(hint.getContentHash()).isEqualTo("budget-hash");
            assertThat(hint.getImageResourceId()).isNotEqualTo(previousImageId);
            assertThat(hint.getContentRevisionHash()).isNotEqualTo("old-revision");
            assertThat(hint.getReviewStatus().name()).isEqualTo("STALE");
        });
        assertThat(countRows("hints_history")).isZero();
    }

    @Test
    void saveWithArchive_refreshesBudgetDifficultyWithoutReopeningEditorialReview() {
        Hint initial = budgetImageHint("budget-projects/Q1/image.jpg", "Source text");
        assertThat(hintRepository.saveWithArchive(initial)).isEqualTo(HintUpsertAction.INSERTED);
        jdbc.update("UPDATE hints SET hint_text = 'Editor text', editorial_text_locked = TRUE, "
                        + "review_status = 'REVIEWED', last_reviewed_at = NOW() WHERE id = :id",
                Map.of("id", initial.getId()));

        Hint refreshed = budgetImageHint("budget-projects/Q1/image.jpg", "Regenerated source text");
        refreshed.setDifficulty(57);
        assertThat(hintRepository.saveWithArchive(refreshed)).isEqualTo(HintUpsertAction.SOFT_UPDATED);
        assertThat(hintRepository.findById(initial.getId())).get().satisfies(hint -> {
            assertThat(hint.getHintText()).isEqualTo("Editor text");
            assertThat(hint.getDifficulty()).isEqualTo(57);
            assertThat(hint.getContentHash()).isEqualTo("budget-hash");
            assertThat(hint.getContentRevisionHash()).isEqualTo("old-revision");
            assertThat(hint.getReviewStatus().name()).isEqualTo("REVIEWED");
            assertThat(hint.getLastReviewedAt()).isNotNull();
        });
        assertThat(countRows("hints_history")).isZero();
    }

    @Test
    void saveWithArchive_canRefreshEditorialTextWhenRetryIsScheduled() {
        Hint initial = buildHint("hash-old", "Source text", 24, new BigDecimal("0.80"), 4);
        assertThat(hintRepository.saveWithArchive(initial)).isEqualTo(HintUpsertAction.INSERTED);
        jdbc.update("UPDATE hints SET hint_text = 'Editor text', editorial_text_locked = TRUE, "
                        + "review_retry_after_at = :retryAt WHERE id = :id",
                new MapSqlParameterSource("id", initial.getId())
                        .addValue("retryAt", Timestamp.from(Instant.now().plusSeconds(90L * 86400))));

        Hint replacement = buildHint("hash-new", "Updated current fact", 31, new BigDecimal("0.95"), 1);
        assertThat(hintRepository.saveWithArchive(replacement)).isEqualTo(HintUpsertAction.REPLACED);
        assertThat(hintRepository.findActiveByCountryId(COUNTRY_ID))
                .extracting(Hint::getHintText).containsExactly("Updated current fact");
    }

    @Test
    void save_preservesEditorialTextOnContentHashConflict() {
        Hint initial = buildHint("hash-same", "Source text", 24, new BigDecimal("0.80"), 4);
        Hint persisted = hintRepository.save(initial).hint();
        jdbc.update("UPDATE hints SET hint_text = 'Editor text', editorial_text_locked = TRUE WHERE id = :id",
                Map.of("id", persisted.getId()));

        Hint refreshed = hintRepository.save(buildHint("hash-same", "Source overwrite", 40,
                new BigDecimal("0.95"), 1)).hint();
        assertThat(refreshed.getHintText()).isEqualTo("Editor text");
        assertThat(refreshed.getDifficulty()).isEqualTo(24);
        assertThat(refreshed.getEditorialTextLocked()).isTrue();
    }

    @Test
    void saveWithArchive_doesNotMutatePermanentlyDeletedIdentity() {
        Hint initial = buildHint("hash-deleted", "Deleted text", 24, new BigDecimal("0.80"), 4);
        assertThat(hintRepository.saveWithArchive(initial)).isEqualTo(HintUpsertAction.INSERTED);
        jdbc.update("""
                INSERT INTO hint_suppression (
                    country_id, source, source_indicator, category, hint_type,
                    action, reason
                ) VALUES (
                    :countryId, 'MANUAL', 'MANUAL_TEST', 'CULTURE', 'CULTURAL',
                    'DELETE', 'test permanent deletion'
                )
                """, Map.of("countryId", COUNTRY_ID));
        jdbc.update(
                "UPDATE hints SET is_active = FALSE WHERE id = :id",
                Map.of("id", initial.getId()));

        Hint regenerated = buildHint(
                "hash-regenerated",
                "Source tried to recreate this hint",
                35,
                new BigDecimal("0.95"),
                1);

        assertThat(hintRepository.saveWithArchive(regenerated)).isEqualTo(HintUpsertAction.NOOP);
        assertThat(jdbc.queryForObject(
                "SELECT hint_text FROM hints WHERE id = :id",
                Map.of("id", initial.getId()),
                String.class)).isEqualTo("Deleted text");
        assertThat(countRows("hints_history")).isZero();
    }

    @Test
    void archiveAndDeleteByIds_movesHintsToHistory() {
        Hint stale = buildHint("hash-stale", "Stale text", 24, new BigDecimal("0.80"), 4);
        hintRepository.save(stale);

        assertThat(hintRepository.archiveAndDeleteByIds(List.of(stale.getId()), "stale_source_regen")).isEqualTo(1);
        assertThat(hintRepository.findActiveByCountryId(COUNTRY_ID)).isEmpty();

        assertThat(countRows("hints_history")).isEqualTo(1);
        ArchivedHint archived = jdbc.queryForObject("""
                SELECT hint_text, content_hash, archive_reason, archived_at
                FROM hints_history
                WHERE id = :id
                """, Map.of("id", stale.getId()), (rs, rowNum) -> new ArchivedHint(
                rs.getString("hint_text"),
                rs.getString("content_hash"),
                rs.getString("archive_reason"),
                rs.getTimestamp("archived_at").toInstant()
        ));

        assertThat(archived).isNotNull();
        assertThat(archived.hintText()).isEqualTo("Stale text");
        assertThat(archived.contentHash()).isEqualTo("hash-stale");
        assertThat(archived.archiveReason()).isEqualTo("stale_source_regen");
        assertThat(archived.archivedAt()).isEqualTo(ClockTestConfiguration.FIXED_INSTANT);
    }

    @Test
    void refreshMetadata_updatesLegacyHashWhenHintTextMatches() {
        Hint initial = buildHint("legacy-hash", "Original text", 24, new BigDecimal("0.80"), 4);
        hintRepository.save(initial);

        int updated = hintRepository.refreshMetadata(
                COUNTRY_ID,
                "exact-render-hash",
                "Original text",
                1,
                31,
                new BigDecimal("0.95")
        );

        assertThat(updated).isEqualTo(1);
        assertThat(hintRepository.findActiveByCountryId(COUNTRY_ID)).singleElement().satisfies(hint -> {
            assertThat(hint.getHintText()).isEqualTo("Original text");
            assertThat(hint.getContentHash()).isEqualTo("exact-render-hash");
            assertThat(hint.getDifficulty()).isEqualTo(31);
            assertThat(hint.getQualityScore()).isEqualByComparingTo("0.95");
            assertThat(hint.getAmbiguityCount()).isEqualTo(1);
        });
    }

    @Test
    void save_returnsPersistedRowWhenUpsertHitsExistingContentHash() {
        Hint initial = buildHint("hash-same", "Original text", 24, new BigDecimal("0.80"), 4);
        initial.setHintType(HintType.IMAGE_FACT);
        Hint persistedInitial = hintRepository.save(initial).hint();

        Hint conflicting = buildHint("hash-same", "Updated text", 29, new BigDecimal("0.92"), 2);
        conflicting.setHintType(HintType.IMAGE_FACT);
        conflicting.setImageUrl("https://example.com/updated.jpg");

        Hint persisted = hintRepository.save(conflicting).hint();

        assertThat(persisted.getId()).isEqualTo(persistedInitial.getId());
        assertThat(persisted.getHintText()).isEqualTo("Updated text");
        assertThat(persisted.getImageUrl()).isEqualTo("https://example.com/updated.jpg");
        assertThat(persisted.getDifficulty()).isEqualTo(29);
        assertThat(persisted.getQualityScore()).isEqualByComparingTo("0.92");
        assertThat(persisted.getAmbiguityCount()).isEqualTo(2);
    }

    @Test
    void applyAiReviewChange_updatesTextHintWithoutImage() {
        Hint persisted = hintRepository.save(buildHint("review-hash", "Original text", 24, new BigDecimal("0.80"), 4)).hint();
        String originalText = persisted.getHintText();
        persisted.setHintText("Reviewed text");
        persisted.setDifficulty(33);

        Hint reviewed = hintRepository.applyAiReviewChange(persisted, null,
                ClockTestConfiguration.FIXED_INSTANT, persisted.getContentRevisionHash(), originalText,
                persisted.getImageResourceId()).orElseThrow();

        assertThat(reviewed.getId()).isEqualTo(persisted.getId());
        assertThat(reviewed.getHintText()).isEqualTo("Reviewed text");
        assertThat(reviewed.getDifficulty()).isEqualTo(33);
        assertThat(reviewed.getImageUrl()).isNull();
    }

    @Test
    void applyAiReviewChange_doesNotOverwriteNewerRevision() {
        Hint persisted = hintRepository.save(buildHint("review-guard-hash", "Original text", 24,
                new BigDecimal("0.80"), 4)).hint();
        persisted.setHintText("Stale AI rewrite");

        assertThat(hintRepository.applyAiReviewChange(persisted, null,
                ClockTestConfiguration.FIXED_INSTANT, "older-revision", "Original text",
                persisted.getImageResourceId())).isEmpty();
        assertThat(hintRepository.findById(persisted.getId()).orElseThrow().getHintText())
                .isEqualTo("Original text");
    }

    @Test
    void searchActiveHints_matchesAcrossCountriesWithoutCountryFilter() {
        hintRepository.save(buildHint("search-hash", "Searchable capital fact", 24, new BigDecimal("0.80"), 4));

        assertThat(hintRepository.searchActiveHints("capital", null, 10))
                .extracting(Hint::getHintText)
                .containsExactly("Searchable capital fact");
    }

    private Hint buildHint(String contentHash, String text, int difficulty, BigDecimal qualityScore, int ambiguityCount) {
        return Hint.builder()
                .countryId(COUNTRY_ID)
                .category(Category.CULTURE)
                .hintType(HintType.CULTURAL)
                .hintText(text)
                .difficulty(difficulty)
                .qualityScore(qualityScore)
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_TEST")
                .sourceYear(2026)
                .contentHash(contentHash)
                .ambiguityCount(ambiguityCount)
                .isActive(true)
                .build();
    }

    private Hint budgetImageHint(String imageUrl, String text) {
        Hint hint = buildHint("budget-hash", text, 43, new BigDecimal("0.89"), 1);
        hint.setCategory(Category.INFRASTRUCTURE);
        hint.setHintType(HintType.IMAGE_FACT);
        hint.setSource(DataSource.BUDGET_PROJECTS);
        hint.setSourceIndicator("BUDGET_PROJECT_Q1:PROJECT");
        hint.setImageUrl(imageUrl);
        hint.setOriginalImageUrl("https://example.org/original.jpg");
        hint.setContentRevisionHash("old-revision");
        return hint;
    }

    private int countRows(String table) {
        Integer count = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count != null ? count : 0;
    }

    private record ArchivedHint(String hintText, String contentHash, String archiveReason,
                                java.time.Instant archivedAt) {
    }
}
