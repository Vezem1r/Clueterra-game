package io.clueterra.questions.repository;

import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import io.clueterra.questions.repository.util.SqlLoader;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class HintRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    private static final String UPSERT_SQL = SqlLoader.load(HintRepository.class, "upsert");
    private static final String UPSERT_WITH_ARCHIVE_SQL = SqlLoader.load(HintRepository.class, "upsertWithArchive");
    private static final String FIND_BY_ID_SQL = SqlLoader.load(HintRepository.class, "findById");
    private static final String FIND_BY_IDS_SQL = SqlLoader.load(HintRepository.class, "findByIds");
    private static final String SEARCH_ACTIVE_HINTS_SQL = SqlLoader.load(HintRepository.class, "searchActiveHints");
    private static final String MARK_REVIEWED_SQL = SqlLoader.load(HintRepository.class, "markReviewed");
    private static final String MARK_REVIEW_ACTION_PENDING_SQL = SqlLoader.load(HintRepository.class, "markReviewActionPending");
    private static final String MARK_REVIEW_FAILED_SQL = SqlLoader.load(HintRepository.class, "markReviewFailed");
    private static final String APPLY_AI_REVIEW_CHANGE_SQL = SqlLoader.load(HintRepository.class, "applyAiReviewChange");
    private static final String FIND_CATEGORY_COUNTS_BY_COUNTRY_ID_SQL = SqlLoader.load(HintRepository.class, "findCategoryCountsByCountryId");

    public HintSaveResult save(Hint hint) {
        MapSqlParameterSource params = hintParams(hint, Instant.now(clock));
        HintSaveResult result = jdbc.queryForObject(UPSERT_SQL, params, (rs, rowNum) -> new HintSaveResult(
                mapRow(rs),
                rs.getBoolean("was_inserted") ? HintUpsertAction.INSERTED : HintUpsertAction.SOFT_UPDATED));
        if (result == null) {
            throw new IllegalStateException("Hint upsert returned no row for countryId=" + hint.getCountryId());
        }
        return result;
    }

    /**
     * Inserts a hint, atomically archiving any existing hint with the same
     * (country_id, category, hint_type, source_indicator) key before inserting.
     * Used for dynamic hints that replace themselves on each data refresh.
     * Returns the action taken: SOFT_UPDATED, REPLACED, INSERTED, or NOOP.
     */
    public HintUpsertAction saveWithArchive(Hint hint) {
        Instant now = Instant.now(clock);
        MapSqlParameterSource params = hintParams(hint, now).addValue("archivedAt", Timestamp.from(now));

        String action = jdbc.queryForObject(UPSERT_WITH_ARCHIVE_SQL, params, String.class);
        return HintUpsertAction.valueOf(action != null ? action : "NOOP");
    }

    private MapSqlParameterSource hintParams(Hint hint, Instant now) {
        if (hint.getId() == null) {
            hint.setId(TSID.Factory.getTsid().toLong());
            hint.setCreatedAt(now);
        }
        hint.setUpdatedAt(now);

        return new MapSqlParameterSource()
                .addValue("id", hint.getId())
                .addValue("countryId", hint.getCountryId())
                .addValue("category", hint.getCategory().name())
                .addValue("hintType", hint.getHintType().name())
                .addValue("hintText", hint.getHintText())
                .addValue("imageUrl", hint.getImageUrl())
                .addValue("originalImageUrl", hint.getOriginalImageUrl())
                .addValue("imageSourcePageUrl", hint.getImageSourcePageUrl())
                .addValue("imageAuthorName", hint.getImageAuthorName())
                .addValue("imageAttributionText", hint.getImageAttributionText())
                .addValue("imageLicenseLabel", hint.getImageLicenseLabel())
                .addValue("imageLicenseUrl", hint.getImageLicenseUrl())
                .addValue("difficulty", hint.getDifficulty())
                .addValue("qualityScore", hint.getQualityScore())
                .addValue("source", hint.getSource().name())
                .addValue("sourceIndicator", hint.getSourceIndicator())
                .addValue("sourceYear", hint.getSourceYear())
                .addValue("contentHash", hint.getContentHash())
                .addValue("contentRevisionHash", hint.getContentRevisionHash())
                .addValue("ambiguityCount", hint.getAmbiguityCount())
                .addValue("timesUsed", hint.getTimesUsed())
                .addValue("timesCorrect", hint.getTimesCorrect())
                .addValue("isActive", hint.getIsActive())
                .addValue("reviewStatus", hint.getReviewStatus() != null
                        ? hint.getReviewStatus().name() : HintReviewStatus.NOT_REVIEWED.name())
                .addValue("lastReviewedAt", hint.getLastReviewedAt() != null
                        ? Timestamp.from(hint.getLastReviewedAt()) : null)
                .addValue("lastReviewRunId", hint.getLastReviewRunId())
                .addValue("reviewRetryAfterAt", hint.getReviewRetryAfterAt() != null
                        ? Timestamp.from(hint.getReviewRetryAfterAt()) : null)
                .addValue("createdAt", Timestamp.from(hint.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(hint.getUpdatedAt()));
    }

    public List<Hint> findActiveByCountryId(Long countryId) {
        return jdbc.query("""
                SELECT * FROM cl_media_schema.hint_projection
                WHERE country_id = :countryId AND is_active = true
                ORDER BY quality_score DESC, times_used, difficulty
                """, Map.of("countryId", countryId), rowMapper());
    }

    public List<Hint> findActiveByCountryIdAndSource(Long countryId, DataSource source) {
        return jdbc.query("""
                SELECT * FROM cl_media_schema.hint_projection
                WHERE country_id = :countryId
                  AND source = :source
                  AND is_active = true
                ORDER BY quality_score DESC, difficulty
                """, Map.of(
                "countryId", countryId,
                "source", source.name()
        ), rowMapper());
    }

    public HintCoverageStats findActiveCoverageByCountryId(Long countryId) {
        return jdbc.queryForObject("""
                SELECT ROUND(AVG(difficulty), 2) AS avg_difficulty,
                       ROUND(AVG(quality_score), 4) AS avg_quality_score,
                       COUNT(DISTINCT category) AS categories_covered,
                       COUNT(DISTINCT hint_type) AS hint_types_covered
                FROM hints
                WHERE country_id = :countryId AND is_active = true
                """, Map.of("countryId", countryId), (rs, rowNum) -> new HintCoverageStats(
                rs.getBigDecimal("avg_difficulty"),
                rs.getBigDecimal("avg_quality_score"),
                rs.getInt("categories_covered"),
                rs.getInt("hint_types_covered")));
    }

    public Set<String> findContentHashesByCountryId(Long countryId) {
        List<String> hashes = jdbc.queryForList(
                "SELECT content_hash FROM hints WHERE country_id = :countryId",
                Map.of("countryId", countryId),
                String.class
        );
        return new HashSet<>(hashes);
    }

    public List<String> findActiveTextsByCountryId(Long countryId) {
        return jdbc.queryForList(
                "SELECT hint_text FROM hints WHERE country_id = :countryId AND is_active = true",
                Map.of("countryId", countryId),
                String.class
        );
    }

    public Map<String, Integer> findCategoryCountsByCountryId(Long countryId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        jdbc.query(FIND_CATEGORY_COUNTS_BY_COUNTRY_ID_SQL, Map.of("countryId", countryId), rs -> {
            result.put(rs.getString("category"), rs.getInt("cnt"));
        });
        return result;
    }

    public long countActive() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM hints WHERE is_active = true", Map.of(), Long.class);
        return count != null ? count : 0;
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM hints", Map.of(), Long.class);
        return count != null ? count : 0;
    }

    public Optional<Hint> findById(Long hintId) {
        List<Hint> hints = jdbc.query(FIND_BY_ID_SQL, Map.of("hintId", hintId), rowMapper());
        return hints.isEmpty() ? Optional.empty() : Optional.of(hints.getFirst());
    }

    public List<Hint> findByIds(List<Long> hintIds) {
        if (hintIds == null || hintIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query(
                FIND_BY_IDS_SQL,
                new MapSqlParameterSource("hintIds", hintIds),
                rowMapper()
        );
    }

    public List<Hint> searchActiveHints(String query, Long countryId, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("query", "%" + query.trim().toLowerCase(Locale.ROOT) + "%")
                .addValue("countryId", countryId)
                .addValue("limit", limit);

        return jdbc.query(SEARCH_ACTIVE_HINTS_SQL, params, (rs, rowNum) -> {
            Hint hint = mapRow(rs);
            hint.setCountry(Country.builder()
                    .isoAlpha2(rs.getString("country_code"))
                    .nameEn(rs.getString("country_name"))
                    .build());
            return hint;
        });
    }

    public void markReviewed(Long hintId, Long reviewRunId, Instant reviewedAt) {
        markReviewed(hintId, reviewRunId, reviewedAt, null);
    }

    public void markReviewed(Long hintId, Long reviewRunId, Instant reviewedAt, Integer retryAfterDays) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hintId", hintId)
                .addValue("reviewRunId", reviewRunId)
                .addValue("reviewedAt", Timestamp.from(reviewedAt))
                .addValue("reviewRetryAfterAt", retryAfterDays != null
                        ? Timestamp.from(reviewedAt.plusSeconds(retryAfterDays.longValue() * 86400)) : null);
        jdbc.update(MARK_REVIEWED_SQL, params);
    }

    public void markReviewActionPending(Long hintId, Long reviewRunId, Instant reviewedAt) {
        updateReviewLifecycle(MARK_REVIEW_ACTION_PENDING_SQL, hintId, reviewRunId, reviewedAt);
    }

    public void markReviewFailed(Long hintId, Long reviewRunId, Instant reviewedAt) {
        updateReviewLifecycle(MARK_REVIEW_FAILED_SQL, hintId, reviewRunId, reviewedAt);
    }

    private void updateReviewLifecycle(String sql, Long hintId, Long reviewRunId, Instant reviewedAt) {
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("hintId", hintId)
                .addValue("reviewRunId", reviewRunId)
                .addValue("reviewedAt", Timestamp.from(reviewedAt)));
    }

    public Optional<Hint> applyAiReviewChange(Hint hint, Long reviewRunId, Instant reviewedAt,
                                               String expectedRevisionHash, String expectedHintText,
                                               Long expectedImageResourceId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hintId", hint.getId())
                .addValue("hintText", hint.getHintText())
                .addValue("imageUrl", hint.getImageUrl())
                .addValue("difficulty", hint.getDifficulty())
                .addValue("qualityScore", hint.getQualityScore())
                .addValue("isActive", hint.getIsActive())
                .addValue("contentRevisionHash", hint.getContentRevisionHash())
                .addValue("expectedRevisionHash", expectedRevisionHash)
                .addValue("expectedHintText", expectedHintText)
                .addValue("expectedImageResourceId", expectedImageResourceId)
                .addValue("reviewRunId", reviewRunId)
                .addValue("reviewRetryAfterAt", hint.getReviewRetryAfterAt() != null
                        ? Timestamp.from(hint.getReviewRetryAfterAt()) : null)
                .addValue("editorialTextLocked", hint.getEditorialTextLocked())
                .addValue("reviewedAt", Timestamp.from(reviewedAt));

        return jdbc.query(APPLY_AI_REVIEW_CHANGE_SQL, params, rowMapper()).stream().findFirst();
    }

    public int refreshMetadata(Long countryId, String contentHash, String hintText,
                               int ambiguityCount, int difficulty, BigDecimal qualityScore) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("countryId", countryId)
                .addValue("contentHash", contentHash)
                .addValue("hintText", hintText)
                .addValue("ambiguityCount", ambiguityCount)
                .addValue("difficulty", difficulty)
                .addValue("qualityScore", qualityScore)
                .addValue("updatedAt", Timestamp.from(Instant.now(clock)));
        return jdbc.update("""
                UPDATE hints SET ambiguity_count = :ambiguityCount,
                                 difficulty      = :difficulty,
                                 quality_score   = :qualityScore,
                                 content_hash    = :contentHash,
                                 updated_at      = :updatedAt
                WHERE country_id = :countryId
                  AND is_active = true
                  AND (content_hash = :contentHash OR hint_text = :hintText)
                  AND NOT (editorial_text_locked AND review_retry_after_at IS NULL)
                """, params);
    }

    public int archiveAndDeleteByIds(List<Long> hintIds, String archiveReason) {
        if (hintIds == null || hintIds.isEmpty()) {
            return 0;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", hintIds)
                .addValue("now", Timestamp.from(Instant.now(clock)))
                .addValue("archiveReason", archiveReason);

        jdbc.update("""
                INSERT INTO hints_history (
                    id, country_id, category, hint_type, hint_text, difficulty,
                    quality_score, source, source_indicator, source_year,
                    content_hash, ambiguity_count, times_used, times_correct,
                    is_active, image_resource_id, original_created_at, archived_at,
                    archive_reason
                )
                SELECT h.id, h.country_id, h.category, h.hint_type, h.hint_text,
                       h.difficulty, h.quality_score, h.source, h.source_indicator,
                       h.source_year, h.content_hash, h.ambiguity_count, h.times_used,
                       h.times_correct, h.is_active, h.image_resource_id,
                       h.created_at, :now, :archiveReason
                FROM hints h
                WHERE h.id IN (:ids)
                ON CONFLICT (id) DO NOTHING
                """, params);

        return jdbc.update("""
                DELETE FROM hints
                WHERE id IN (:ids)
                """, params);
    }

    public void batchMarkUsed(List<Long> hintIds) {
        if (hintIds == null || hintIds.isEmpty()) return;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", hintIds)
                .addValue("updatedAt", Timestamp.from(Instant.now(clock)));

        jdbc.update("UPDATE hints SET times_used = times_used + 1, updated_at = :updatedAt WHERE id IN (:ids)", params);
    }

    public void batchUnmarkUsed(List<Long> hintIds) {
        if (hintIds == null || hintIds.isEmpty()) return;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", hintIds)
                .addValue("updatedAt", Timestamp.from(Instant.now(clock)));

        jdbc.update("UPDATE hints SET times_used = GREATEST(times_used - 1, 0), updated_at = :updatedAt WHERE id IN (:ids)", params);
    }

    private RowMapper<Hint> rowMapper() {
        return (rs, rn) -> mapRow(rs);
    }

    private Hint mapRow(ResultSet rs) throws SQLException {
        return Hint.builder()
                .id(rs.getLong("id"))
                .countryId(rs.getLong("country_id"))
                .category(Category.valueOf(rs.getString("category")))
                .hintType(HintType.valueOf(rs.getString("hint_type")))
                .hintText(rs.getString("hint_text"))
                .imageUrl(rs.getString("image_url"))
                .originalImageUrl(rs.getString("original_image_url"))
                .imageSourcePageUrl(rs.getString("image_source_page_url"))
                .imageAuthorName(rs.getString("image_author_name"))
                .imageAttributionText(rs.getString("image_attribution_text"))
                .imageLicenseLabel(rs.getString("image_license_label"))
                .imageLicenseUrl(rs.getString("image_license_url"))
                .imageResourceId(getLong(rs, "image_resource_id"))
                .difficulty(rs.getInt("difficulty"))
                .qualityScore(rs.getBigDecimal("quality_score"))
                .source(DataSource.valueOf(rs.getString("source")))
                .sourceIndicator(rs.getString("source_indicator"))
                .sourceYear(getInt(rs, "source_year"))
                .contentHash(rs.getString("content_hash"))
                .contentRevisionHash(rs.getString("content_revision_hash"))
                .ambiguityCount(getInt(rs, "ambiguity_count"))
                .timesUsed(rs.getInt("times_used"))
                .timesCorrect(getInt(rs, "times_correct"))
                .isActive(rs.getBoolean("is_active"))
                .reviewStatus(HintReviewStatus.valueOf(rs.getString("review_status")))
                .lastReviewedAt(getInstant(rs, "last_reviewed_at"))
                .lastReviewRunId(getLong(rs, "last_review_run_id"))
                .reviewRetryAfterAt(getInstant(rs, "review_retry_after_at"))
                .editorialTextLocked(rs.getBoolean("editorial_text_locked"))
                .createdAt(getInstant(rs, "created_at"))
                .updatedAt(getInstant(rs, "updated_at"))
                .build();
    }

    private Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private Instant getInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }
}
