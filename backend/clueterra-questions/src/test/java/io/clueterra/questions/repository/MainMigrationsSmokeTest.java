package io.clueterra.questions.repository;

import io.clueterra.questions.TestcontainersConfiguration;
import io.clueterra.questions.ai.repository.AiReviewQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

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
class MainMigrationsSmokeTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reviewerClaimsAreExclusiveAndRecoverable() {
        Long countryId = jdbcTemplate.queryForObject(
                "select id from countries order by id limit 1",
                Long.class);
        jdbcTemplate.update(
                """
                        insert into cl_ai_schema.ai_run (
                            id, run_type, trigger_type, status, provider, model, prompt_version
                        ) values
                            (9100000001, 'REVIEW', 'SCHEDULED', 'PREPARED', 'gemini', 'test', 'v1'),
                            (9100000002, 'REVIEW', 'SCHEDULED', 'PREPARED', 'gemini', 'test', 'v1')
                        """);
        jdbcTemplate.update(
                """
                        insert into hints (
                            id, country_id, category, hint_type, hint_text, difficulty,
                            quality_score, source, source_indicator, content_hash,
                            content_revision_hash, review_status
                        ) values (?, ?, 'GEOGRAPHY', 'ABSOLUTE_FACT', 'Exclusive reviewer claim',
                                  50, 0.90, 'MANUAL', 'REVIEW_CLAIM_TEST',
                                  'review-claim-content', 'review-claim-revision', 'NOT_REVIEWED')
                        """,
                9_100_000_003L,
                countryId);

        AiReviewQueueRepository repository = new AiReviewQueueRepository(
                new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource()));

        assertThat(repository.claimPendingCandidates(9_100_000_001L, 1))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.reviewStatus().name()).isEqualTo("IN_REVIEW"));
        assertThat(repository.claimPendingCandidates(9_100_000_002L, 1)).isEmpty();
        assertThat(repository.releaseRunClaims(9_100_000_001L)).isOne();
        assertThat(repository.claimPendingCandidates(9_100_000_002L, 1))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.hintId()).isEqualTo(9_100_000_003L));
    }

    @Test
    void reviewerSkipsPreviouslyReviewedAndGeneratedHints() {
        Long countryId = jdbcTemplate.queryForObject(
                "select id from countries order by id limit 1",
                Long.class);
        AiReviewQueueRepository repository = new AiReviewQueueRepository(
                new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource()));
        var coverageBefore = repository.findReviewCoverage();
        jdbcTemplate.update(
                """
                        insert into cl_ai_schema.ai_run (
                            id, run_type, trigger_type, status, provider, model, prompt_version
                        ) values
                            (9100000101, 'REVIEW', 'SCHEDULED', 'COMPLETED', 'gemini', 'test', 'review-v1'),
                            (9100000102, 'REVIEW', 'SCHEDULED', 'COMPLETED', 'gemini', 'test', 'review-v2'),
                            (9100000103, 'REVIEW', 'SCHEDULED', 'PREPARED', 'gemini', 'test', 'review-v2')
                        """);
        jdbcTemplate.update(
                """
                        insert into hints (
                            id, country_id, category, hint_type, hint_text, difficulty,
                            quality_score, source, source_indicator, content_hash,
                            content_revision_hash, review_status, last_review_run_id
                        ) values
                            (9100000111, ?, 'GEOGRAPHY', 'ABSOLUTE_FACT', 'Old prompt review.',
                             50, 0.90, 'MANUAL', 'OLD_PROMPT_REVIEW',
                             'old-prompt-content', 'old-prompt-revision', 'REVIEWED', 9100000101),
                            (9100000112, ?, 'GEOGRAPHY', 'ABSOLUTE_FACT', 'Current prompt review.',
                             50, 0.90, 'MANUAL', 'CURRENT_PROMPT_REVIEW',
                             'current-prompt-content', 'current-prompt-revision', 'REVIEWED', 9100000102),
                            (9100000113, ?, 'GEOGRAPHY', 'ABSOLUTE_FACT', 'Generated hint.',
                             50, 0.90, 'AI_GENERATED', 'GENERATED_REVIEW_TEST',
                             'generated-content', 'generated-revision', 'NOT_REVIEWED', null),
                            (9100000114, ?, 'GEOGRAPHY', 'ABSOLUTE_FACT', 'Unreviewed manual hint.',
                             50, 0.90, 'MANUAL', 'UNREVIEWED_MANUAL_TEST',
                             'unreviewed-content', 'unreviewed-revision', 'NOT_REVIEWED', null),
                            (9100000115, ?, 'GEOGRAPHY', 'ABSOLUTE_FACT', 'Published generated hint.',
                             50, 0.90, 'AI_GENERATED', 'PUBLISHED_GENERATED_REVIEW_TEST',
                             'published-generated-content', 'published-generated-revision', 'REVIEWED', null)
                        """,
                countryId,
                countryId,
                countryId,
                countryId,
                countryId);

        var coverageAfter = repository.findReviewCoverage();
        assertThat(coverageAfter.totalHints()).isEqualTo(coverageBefore.totalHints() + 3);
        assertThat(coverageAfter.processedHints()).isEqualTo(coverageBefore.processedHints() + 2);
        assertThat(coverageAfter.pendingHints()).isEqualTo(coverageBefore.pendingHints() + 1);

        assertThat(repository.claimPendingCandidates(9_100_000_103L, 10))
                .extracting(candidate -> candidate.hintId())
                .containsExactly(9_100_000_114L);
    }

    @Test
    void reviewerReclaimsTimeSensitiveHintOnlyAfterRetryDate() {
        Long countryId = jdbcTemplate.queryForObject(
                "select id from countries order by id limit 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO cl_ai_schema.ai_run
                    (id, run_type, trigger_type, status, provider, model, prompt_version)
                VALUES (9100000201, 'REVIEW', 'SCHEDULED', 'PREPARED', 'gemini', 'test', 'review-v2')
                """);
        jdbcTemplate.update("""
                INSERT INTO hints
                    (id, country_id, category, hint_type, hint_text, difficulty, quality_score,
                     source, source_indicator, content_hash, content_revision_hash, review_status,
                     review_retry_after_at)
                VALUES
                    (9100000202, ?, 'HISTORY', 'ABSOLUTE_FACT', 'Current president clue.', 50, 0.90,
                     'CIA_FACTBOOK', 'CIA_CHIEF_OF_STATE_1', 'retry-due-content', 'retry-due-revision',
                     'REVIEWED', NOW() - INTERVAL '1 day'),
                    (9100000203, ?, 'HISTORY', 'ABSOLUTE_FACT', 'Another president clue.', 50, 0.90,
                     'CIA_FACTBOOK', 'CIA_CHIEF_OF_STATE_2', 'retry-later-content', 'retry-later-revision',
                     'REVIEWED', NOW() + INTERVAL '90 days')
                """, countryId, countryId);

        AiReviewQueueRepository repository = new AiReviewQueueRepository(
                new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource()));
        assertThat(repository.claimPendingCandidates(9_100_000_201L, 10))
                .extracting(candidate -> candidate.hintId())
                .containsExactly(9_100_000_202L);
    }

    @Test
    void flyway_bootstraps_core_tables_and_history() {
        assertThat(tableExists("countries")).isTrue();
        assertThat(tableExists("country_statistics")).isTrue();
        assertThat(tableExists("hints")).isTrue();
        assertThat(tableExists("hints_history")).isTrue();
        assertThat(tableExists("cl_media_schema", "image_resource")).isTrue();
        assertThat(tableExists("cl_media_schema", "image_rights_revision")).isTrue();
        assertThat(tableExists("cl_media_schema", "image_rendition")).isTrue();
        assertThat(tableExists("cl_media_schema", "image_finding")).isTrue();
        assertThat(tableExists("hint_suppression")).isTrue();
        assertThat(tableExists("hint_image_suppression")).isTrue();
        assertThat(tableExists("statistic_suppression")).isTrue();
        assertThat(tableExists("indicator_config")).isTrue();
        assertThat(tableExists("cl_staging", "global_brands")).isTrue();
        assertThat(tableExists("cl_staging", "capital_coordinates")).isTrue();

        Integer countryRows = jdbcTemplate.queryForObject(
                "select count(*) from countries",
                Integer.class
        );
        Integer prepopulatedProviderFields = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from countries
                        where name_native is not null
                           or capital is not null
                           or flag_emoji is not null
                           or flag_url is not null
                        """,
                Integer.class
        );
        String valueTextType = jdbcTemplate.queryForObject(
                """
                        select data_type
                        from information_schema.columns
                        where table_schema = 'cl_schema'
                          and table_name = 'country_statistics'
                          and column_name = 'value_text'
                        """,
                String.class
        );
        assertThat(countryRows).isEqualTo(251);
        assertThat(prepopulatedProviderFields).isZero();
        assertThat(valueTextType).isEqualTo("text");
    }

    @Test
    void flyway_seeds_live_indicator_templates() {
        Integer v11Rows = jdbcTemplate.queryForObject(
                "select count(*) from indicator_config where indicator_code like 'V11_%'",
                Integer.class
        );
        Integer coreRows = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from indicator_config
                        where indicator_code in (
                            'CIA_AREA_COMPARATIVE_1',
                            'CIA_BORDER_COUNTRIES_1',
                            'GEONAMES_LARGEST_CITY_1',
                            'WIKIDATA_NATIONAL_ANTHEM_1',
                            'WB_PROSPERITY_STACK_1'
                        )
                        """,
                Integer.class
        );
        Integer riverSkeletonRows = jdbcTemplate.queryForObject(
                "select count(*) from indicator_config where indicator_code like 'HYDRORIVERS_RIVER_SKELETON_%'",
                Integer.class
        );
        Integer cinemaRows = jdbcTemplate.queryForObject(
                "select count(*) from indicator_config where indicator_code like 'WIKIDATA_CINEMA_%'",
                Integer.class
        );

        assertThat(v11Rows).isZero();
        assertThat(coreRows).isEqualTo(5);
        assertThat(riverSkeletonRows).isEqualTo(3);
        assertThat(cinemaRows).isEqualTo(20);
    }

    @Test
    void flyway_excludesOwidDatasetsWithoutCommerciallyReviewedTerms() {
        Integer unsafeTemplates = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from indicator_config
                        where indicator_code ~ '^OWID_(PRISON|TOURIST|MILITARY|OBESITY|SMOKING|MEAT)_'
                           or indicator_code in ('OWID_COMBO_OBESE_HAPPY', 'OWID_COMBO_MEAT_TOURIST')
                        """,
                Integer.class);
        Integer tourismCombos = jdbcTemplate.queryForObject(
                "select count(*) from combo_indicator_config where indicator_code like '%TOURISM%'",
                Integer.class);
        Integer approvedCombos = jdbcTemplate.queryForObject(
                "select count(*) from combo_indicator_config",
                Integer.class);

        assertThat(unsafeTemplates).isZero();
        assertThat(tourismCombos).isZero();
        assertThat(approvedCombos).isEqualTo(271);
    }

    @Test
    void deletedHintIdentityCannotBeRecreatedByASubsequentSynchronization() {
        Long countryId = jdbcTemplate.queryForObject(
                "select id from countries order by id limit 1",
                Long.class);
        jdbcTemplate.update(
                """
                        insert into hint_suppression (
                            country_id, source, source_indicator,
                            category, hint_type, action, reason
                        ) values (?, ?, ?, ?, ?, 'QUARANTINE', 'test deactivation')
                        """,
                countryId,
                "TEST_SOURCE",
                "TEST_INDICATOR",
                "GEOGRAPHY",
                "ABSOLUTE_FACT");
        jdbcTemplate.update(
                """
                        insert into hint_suppression (
                            country_id, source, source_indicator,
                            category, hint_type, action, reason
                        ) values (?, ?, ?, ?, ?, 'DELETE', 'test deletion')
                        on conflict (
                            country_id, source, coalesce(source_indicator, ''),
                            category, hint_type
                        ) where is_active = true
                        do update set action = excluded.action, reason = excluded.reason
                        """,
                countryId,
                "TEST_SOURCE",
                "TEST_INDICATOR",
                "GEOGRAPHY",
                "ABSOLUTE_FACT");

        assertThat(jdbcTemplate.queryForObject(
                "select action from hint_suppression where reason = 'test deletion'",
                String.class)).isEqualTo("DELETE");

        int inserted = jdbcTemplate.update(
                """
                        insert into hints (
                            id, country_id, category, hint_type, hint_text,
                            difficulty, quality_score, source, source_indicator, content_hash
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                9_000_000_001L,
                countryId,
                "GEOGRAPHY",
                "ABSOLUTE_FACT",
                "This hint must remain deleted.",
                50,
                0.90,
                "TEST_SOURCE",
                "TEST_INDICATOR",
                "deleted-hint-test-hash");

        assertThat(inserted).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from hints where id = 9000000001",
                Integer.class)).isZero();
    }

    @Test
    void permanentlyQuarantinedResourceStopsServingAndQueuesStoredBytesForDeletion() {
        Long resourceId = jdbcTemplate.queryForObject(
                """
                        select cl_media_schema.register_image(
                            'MANUAL', 'hint-images', 'manual/test/resource.jpg',
                            'https://example.com/resource.jpg', 'https://example.com/source',
                            'Example author', 'Example attribution', 'CC BY 4.0',
                            'https://creativecommons.org/licenses/by/4.0/')
                        """,
                Long.class);

        jdbcTemplate.queryForObject(
                "select cl_media_schema.quarantine_image_resource(?, 'restricted license', true)",
                Integer.class,
                resourceId);

        assertThat(jdbcTemplate.queryForObject(
                "select lifecycle_status from cl_media_schema.image_resource where id = ?",
                String.class,
                resourceId)).isEqualTo("RETIRED");
        assertThat(jdbcTemplate.queryForObject(
                "select storage_status from cl_media_schema.image_rendition where image_resource_id = ?",
                String.class,
                resourceId)).isEqualTo("PENDING_DELETE");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from cl_media_schema.image_resource_suppression "
                        + "where image_resource_id = ? and is_active = true",
                Integer.class,
                resourceId)).isOne();
    }

    @Test
    void rightsHistoryAppendsANewRevisionWhenMetadataReturnsToAnOlderValue() {
        String sql = """
                select cl_media_schema.register_image(
                    'MANUAL', 'hint-images', 'manual/test/history.jpg',
                    'https://example.com/history.jpg', 'https://example.com/history-source',
                    ?, 'Attribution', 'CC BY 4.0',
                    'https://creativecommons.org/licenses/by/4.0/')
                """;
        Long resourceId = jdbcTemplate.queryForObject(sql, Long.class, "Author A");
        jdbcTemplate.queryForObject(sql, Long.class, "Author B");
        jdbcTemplate.queryForObject(sql, Long.class, "Author A");

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from cl_media_schema.image_rights_revision "
                        + "where image_resource_id = ?",
                Integer.class,
                resourceId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select author_name from cl_media_schema.image_rights_revision "
                        + "where image_resource_id = ? and is_current = true",
                String.class,
                resourceId)).isEqualTo("Author A");
    }

    @Test
    void metadataRefreshKeepsCanonicalIdentityWhenStoredObjectDoesNotChange() {
        Long countryId = jdbcTemplate.queryForObject(
                "select id from countries order by id limit 1",
                Long.class);
        Long originalResourceId = jdbcTemplate.queryForObject(
                """
                        select cl_media_schema.register_image(
                            'IDENTITY_TEST', 'hint-images', 'identity/test.jpg',
                            'https://upload.wikimedia.org/wikipedia/commons/a/a1/Identity_test.jpg',
                            'https://commons.wikimedia.org/wiki/File:Identity_test.jpg',
                            'Example author', 'Example attribution', 'CC BY 4.0',
                            'https://creativecommons.org/licenses/by/4.0/')
                        """,
                Long.class);
        jdbcTemplate.update(
                """
                        insert into hints (
                            id, country_id, category, hint_type, hint_text,
                            difficulty, quality_score, source, source_indicator, content_hash,
                            image_resource_id
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                9_000_000_002L,
                countryId,
                "GEOGRAPHY",
                "IMAGE_FACT",
                "Canonical identity test hint.",
                50,
                0.90,
                "IDENTITY_TEST",
                "IDENTITY_TEST_1",
                "canonical-identity-test-hash",
                originalResourceId);

        Long refreshedResourceId = jdbcTemplate.queryForObject(
                """
                        select cl_media_schema.register_image(
                            'IDENTITY_TEST', 'hint-images', 'identity/test.jpg',
                            'https://upload.wikimedia.org/wikipedia/commons/a/a1/Identity_test.jpg',
                            'https://commons.wikimedia.org/wiki/File:Renamed_identity_test.jpg',
                            'Example author', 'Example attribution', 'CC BY 4.0',
                            'https://creativecommons.org/licenses/by/4.0/',
                            p_source_key_override => (
                                select source_key
                                from cl_media_schema.image_resource
                                where id = ?))
                        """,
                Long.class,
                originalResourceId);
        jdbcTemplate.update(
                "update hints set image_resource_id = ? where id = 9000000002",
                refreshedResourceId);

        assertThat(jdbcTemplate.queryForObject(
                "select image_resource_id from hints where id = 9000000002",
                Long.class)).isEqualTo(originalResourceId);
        assertThat(jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from cl_media_schema.image_rights_revision
                        where image_resource_id = ?
                        """,
                Integer.class,
                originalResourceId)).isEqualTo(2);
    }

    @Test
    void wikimediaOriginalUrlAndCommonsFilePageResolveToOneResource() {
        Long fromOriginalUrl = jdbcTemplate.queryForObject(
                """
                        select cl_media_schema.register_image(
                            'AI_GENERATED', 'hint-images', 'ai/test/shared.jpg',
                            'https://upload.wikimedia.org/wikipedia/commons/a/a1/Shared_image.jpg',
                            null, null, null, null, null)
                        """,
                Long.class);
        Long fromFilePage = jdbcTemplate.queryForObject(
                """
                        select cl_media_schema.register_image(
                            'WIKIMEDIA_LANDMARKS', 'hint-images', 'ai/test/shared.jpg',
                            'https://upload.wikimedia.org/wikipedia/commons/a/a1/Shared_image.jpg',
                            'https://commons.wikimedia.org/wiki/File:Shared_image.jpg',
                            'Example author', 'Example attribution', 'CC BY 4.0',
                            'https://creativecommons.org/licenses/by/4.0/')
                        """,
                Long.class);

        assertThat(fromFilePage).isEqualTo(fromOriginalUrl);
        assertThat(jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from cl_media_schema.image_resource
                        where provider = 'WIKIMEDIA_COMMONS'
                          and source_key = 'file:shared image.jpg'
                        """,
                Integer.class)).isOne();
    }

    @Test
    void differentLocalFlagAssetsResolveToDifferentResources() {
        Long andorra = jdbcTemplate.queryForObject(
                """
                        select cl_media_schema.register_image(
                            'MANUAL', 'hint-images', '/api/assets/flags/ad.svg',
                            '/api/assets/flags/ad.svg', null, null, null, null, null)
                        """,
                Long.class);
        Long emirates = jdbcTemplate.queryForObject(
                """
                        select cl_media_schema.register_image(
                            'MANUAL', 'hint-images', '/api/assets/flags/ae.svg',
                            '/api/assets/flags/ae.svg', null, null, null, null, null)
                        """,
                Long.class);

        assertThat(andorra).isNotEqualTo(emirates);
        assertThat(jdbcTemplate.queryForObject(
                """
                        select count(distinct source_key)
                        from cl_media_schema.image_resource
                        where id in (?, ?)
                        """,
                Integer.class,
                andorra,
                emirates)).isEqualTo(2);
    }

    @Test
    void differentBundledObjectKeysResolveToDifferentResources() {
        Long andorra = jdbcTemplate.queryForObject(
                """
                        select cl_media_schema.register_image(
                            'NATURAL_EARTH', 'hint-images', 'natural-earth/ad.svg',
                            null, 'https://www.naturalearthdata.com/',
                            'Natural Earth', 'Made with Natural Earth.',
                            'Public domain',
                            'https://www.naturalearthdata.com/about/terms-of-use/')
                        """,
                Long.class);
        Long emirates = jdbcTemplate.queryForObject(
                """
                        select cl_media_schema.register_image(
                            'NATURAL_EARTH', 'hint-images', 'natural-earth/ae.svg',
                            null, 'https://www.naturalearthdata.com/',
                            'Natural Earth', 'Made with Natural Earth.',
                            'Public domain',
                            'https://www.naturalearthdata.com/about/terms-of-use/')
                        """,
                Long.class);

        assertThat(andorra).isNotEqualTo(emirates);
        assertThat(jdbcTemplate.queryForObject(
                """
                        select count(distinct source_key)
                        from cl_media_schema.image_resource
                        where id in (?, ?)
                        """,
                Integer.class,
                andorra,
                emirates)).isEqualTo(2);
    }

    private boolean tableExists(String tableName) {
        return tableExists("cl_schema", tableName);
    }

    private boolean tableExists(String schemaName, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = ? and table_name = ?",
                Integer.class,
                schemaName,
                tableName
        );
        return count != null && count > 0;
    }
}
