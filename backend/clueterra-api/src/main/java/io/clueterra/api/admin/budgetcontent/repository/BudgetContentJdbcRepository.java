package io.clueterra.api.admin.budgetcontent.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.budgetcontent.dto.BudgetContentBulkReviewResponse;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentCandidateKey;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentIssue;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentReviewStatus;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentTab;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentUpdateRequest;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentUpdateResponse;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BudgetContentJdbcRepository {

    private static final String BASE_SELECT =
            SqlLoader.load(BudgetContentJdbcRepository.class, "baseSelect");
    private static final String FIND_DETAIL =
            SqlLoader.load(BudgetContentJdbcRepository.class, "findDetail");
    private static final String FIND_FACETS =
            SqlLoader.load(BudgetContentJdbcRepository.class, "findFacets");
    private static final String BULK_REVIEW_CANDIDATES =
            SqlLoader.load(BudgetContentJdbcRepository.class, "bulkReviewCandidates");
    private static final String UPDATE_CANDIDATE =
            SqlLoader.load(BudgetContentJdbcRepository.class, "updateCandidate");
    private static final String KEYSET_CLAUSE =
            """
                     AND (
                       candidate.quality_score < :cursorQuality
                       OR (candidate.quality_score = :cursorQuality AND candidate.monthly_pageviews < :cursorPageviews)
                       OR (candidate.quality_score = :cursorQuality AND candidate.monthly_pageviews = :cursorPageviews AND candidate.wikidata_id > :cursorWikidataId)
                       OR (candidate.quality_score = :cursorQuality AND candidate.monthly_pageviews = :cursorPageviews AND candidate.wikidata_id = :cursorWikidataId AND candidate.category > :cursorCategory)
                     )
                    """;
    private static final String ORDER_BY =
            " ORDER BY candidate.quality_score DESC, candidate.monthly_pageviews DESC,"
                    + " candidate.wikidata_id ASC, candidate.category ASC";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<BudgetContentCandidateRow> findPage(
            BudgetContentFilter filter, BudgetContentCursor cursor, int limit) {
        QueryFilterBuilder filters = buildFilters(filter);
        String cursorClause = addCursor(filters, cursor);
        filters.params().addValue("limit", limit);
        String sql = BASE_SELECT + filters.whereClause() + cursorClause + ORDER_BY + " LIMIT :limit";
        return jdbc.query(sql, filters.params(), rowMapper());
    }

    public Optional<BudgetContentCandidateRow> findDetail(String wikidataId, String category) {
        return jdbc
                .query(
                        FIND_DETAIL,
                        new MapSqlParameterSource()
                                .addValue("wikidataId", wikidataId)
                                .addValue("category", category),
                        rowMapper())
                .stream()
                .findFirst();
    }

    public List<BudgetFacetRow> findFacets(BudgetContentTab tab) {
        return jdbc.query(
                FIND_FACETS,
                new MapSqlParameterSource("reviewStatuses", tab.statuses()),
                (resultSet, rowNumber) ->
                        new BudgetFacetRow(
                                resultSet.getString("dimension"),
                                resultSet.getString("key"),
                                resultSet.getString("label"),
                                resultSet.getLong("count")));
    }

    public BudgetContentBulkReviewResponse reviewCandidates(
            List<BudgetContentCandidateKey> candidates,
            BudgetContentReviewStatus status,
            String note,
            String reviewedBy) {
        Integer updated =
                jdbc.queryForObject(
                        BULK_REVIEW_CANDIDATES,
                        new MapSqlParameterSource()
                                .addValue("candidates", serialize(candidates))
                                .addValue("reviewStatus", status.name())
                                .addValue("reviewNote", note)
                                .addValue("reviewedBy", reviewedBy),
                        Integer.class);
        return new BudgetContentBulkReviewResponse(updated == null ? 0 : updated);
    }

    public BudgetContentUpdateResponse updateCandidate(
            String wikidataId, String category, BudgetContentUpdateRequest request, String editedBy) {
        return jdbc.queryForObject(
                UPDATE_CANDIDATE,
                new MapSqlParameterSource()
                        .addValue("wikidataId", wikidataId)
                        .addValue("category", category)
                        .addValue("editedBy", editedBy)
                        .addValue("content", serialize(request)),
                (resultSet, rowNumber) -> new BudgetContentUpdateResponse(
                        resultSet.getBoolean("updated"),
                        resultSet.getObject("published_question_id", Long.class)));
    }

    private QueryFilterBuilder buildFilters(BudgetContentFilter filter) {
        QueryFilterBuilder filters =
                new QueryFilterBuilder()
                        .in("candidate.review_status", "reviewStatuses", filter.tab().statuses())
                        .eq("candidate.category", "category", filter.category())
                        .eq("candidate.country_code", "countryCode", filter.countryCode())
                        .eq("candidate.difficulty", "difficulty", filter.difficulty())
                        .ilike(
                                "LOWER(candidate.name) LIKE :search OR LOWER(COALESCE(candidate.description, ''))"
                                        + " LIKE :search",
                                "search",
                                filter.search())
                        .gteInt("candidate.quality_score", "minimumQuality", filter.minimumQuality());
        addIssueFilter(filters, filter.issue());
        return filters;
    }

    private void addIssueFilter(QueryFilterBuilder filters, BudgetContentIssue issue) {
        if (issue == null) {
            return;
        }
        String predicate =
                switch (issue) {
                    case DUPLICATE_NAME -> "candidate.duplicate_name_count > 1";
                    case DUPLICATE_IMAGE -> "candidate.duplicate_image_count > 1";
                    case SUSPICIOUS_NAME -> "candidate.name ~* '(^|[[:space:]])(unnamed|unknown)([[:space:]]|$)"
                            + "|^energy project$|^project[[:space:]]*[0-9]+$'";
                    case MISSING_DESCRIPTION ->
                            "candidate.description IS NULL OR LENGTH(BTRIM(candidate.description)) < 60";
                    case LOW_QUALITY -> "candidate.quality_score < 50";
                    case COST_OUTLIER ->
                            "JSONB_EXISTS(candidate.quality_flags, 'CATEGORY_COST_OUTLIER')";
                    case WEAK_COST_EVIDENCE ->
                            "JSONB_EXISTS_ANY(candidate.quality_flags, ARRAY['LEAD_ONLY_COST', 'LOW_AUTHORITY_COST',"
                                    + " 'PROJECT_PART_COST', 'CURRENCY_ERA_MISMATCH',"
                                    + " 'COST_SOURCE_CONFLICT']::TEXT[])";
                    case COST_REVIEW_REQUIRED -> "candidate.quality_status = 'NEEDS_REVIEW'"
                            + " AND candidate.review_status NOT IN ('APPROVED', 'EDITORIAL_APPROVED')";
                };
        filters.predicate(predicate);
    }

    private String addCursor(QueryFilterBuilder filters, BudgetContentCursor cursor) {
        if (cursor == null) {
            return "";
        }
        filters
                .params()
                .addValue("cursorQuality", cursor.qualityScore())
                .addValue("cursorPageviews", cursor.monthlyPageviews())
                .addValue("cursorWikidataId", cursor.wikidataId())
                .addValue("cursorCategory", cursor.category());
        return KEYSET_CLAUSE;
    }

    private RowMapper<BudgetContentCandidateRow> rowMapper() {
        return (resultSet, rowNumber) -> mapCandidate(resultSet);
    }

    private BudgetContentCandidateRow mapCandidate(ResultSet resultSet) throws SQLException {
        return new BudgetContentCandidateRow(
                resultSet.getString("wikidata_id"),
                resultSet.getString("category"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("country_code"),
                resultSet.getString("country_name"),
                resultSet.getString("region_code"),
                resultSet.getObject("project_year", Integer.class),
                resultSet.getString("difficulty"),
                resultSet.getInt("sitelink_count"),
                resultSet.getInt("monthly_pageviews"),
                resultSet.getInt("interest_score"),
                resultSet.getInt("quality_score"),
                resultSet.getInt("source_rank"),
                resultSet.getString("quality_status"),
                resultSet.getString("evidence_tier"),
                resultSet.getInt("cost_confidence_score"),
                json(resultSet.getString("quality_flags")),
                resultSet.getString("source_url"),
                resultSet.getString("review_status"),
                resultSet.getString("review_note"),
                resultSet.getString("reviewed_by"),
                instant(resultSet, "reviewed_at"),
                instant(resultSet, "fetched_at"),
                resultSet.getInt("duplicate_name_count"),
                resultSet.getInt("duplicate_image_count"),
                resultSet.getString("image_source_sha1"),
                resultSet.getString("image_object_key"),
                resultSet.getString("image_original_url"),
                resultSet.getString("image_source_page_url"),
                resultSet.getString("image_file_title"),
                resultSet.getString("image_author_name"),
                resultSet.getString("image_credit"),
                resultSet.getString("image_license_label"),
                resultSet.getString("image_license_url"),
                resultSet.getString("image_description"),
                resultSet.getString("image_mime_type"),
                resultSet.getObject("image_source_bytes", Long.class),
                resultSet.getObject("image_width", Integer.class),
                resultSet.getObject("image_height", Integer.class),
                resultSet.getObject("image_quality_score", Integer.class),
                resultSet.getObject("question_id", Long.class),
                resultSet.getBigDecimal("answer_cost_usd"),
                resultSet.getBigDecimal("source_amount"),
                resultSet.getString("source_currency"),
                resultSet.getObject("cost_year", Integer.class),
                resultSet.getString("normalization_note"),
                resultSet.getString("cost_source_name"),
                resultSet.getString("cost_source_url"),
                resultSet.getString("cost_reference_url"),
                resultSet.getObject("cost_snapshot_at", java.time.LocalDate.class),
                json(resultSet.getString("source_payload")));
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid candidate source payload", exception);
        }
    }

    private String serialize(List<BudgetContentCandidateKey> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize candidate selection", exception);
        }
    }

    private String serialize(BudgetContentUpdateRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize candidate edit", exception);
        }
    }
}
