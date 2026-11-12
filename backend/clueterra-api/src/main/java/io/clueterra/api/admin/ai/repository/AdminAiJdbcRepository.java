package io.clueterra.api.admin.ai.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.api.admin.ai.dto.AiChangeItemDto;
import io.clueterra.api.admin.ai.dto.AiChangeSetActionResponse;
import io.clueterra.api.admin.ai.dto.AiChangeSetDto;
import io.clueterra.api.admin.ai.dto.AiProposalDto;
import io.clueterra.api.admin.ai.dto.AiRunDto;
import io.clueterra.api.admin.ai.dto.AiRunStageDto;
import io.clueterra.api.common.dto.PageResponse;
import io.clueterra.api.common.utils.database.JdbcUtils;
import io.clueterra.api.common.utils.loader.SqlLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AdminAiJdbcRepository {

    private static final String FIND_RECENT_RUNS_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findRecentRuns");
    private static final String COUNT_RUNS_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "countRuns");
    private static final String FIND_RUN_BY_ID_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findRunById");
    private static final String FIND_STAGES_BY_RUN_ID_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findStagesByRunId");
    private static final String FIND_CHANGE_SET_BY_RUN_ID_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findChangeSetByRunId");
    private static final String FIND_RECENT_CHANGE_SETS_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findRecentChangeSets");
    private static final String COUNT_CHANGE_SETS_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "countChangeSets");
    private static final String FIND_CHANGE_SET_BY_ID_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findChangeSetById");
    private static final String FIND_CHANGE_SET_BY_ID_FOR_UPDATE_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findChangeSetByIdForUpdate");
    private static final String FIND_ITEMS_BY_CHANGE_SET_ID_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findItemsByChangeSetId");
    private static final String FIND_ITEM_BY_ID_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findItemById");
    private static final String FIND_PROPOSALS_BY_CHANGE_SET_ID_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "findProposalsByChangeSetId");
    private static final String UPDATE_ITEM_STATUS_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "updateItemStatus");
    private static final String UPDATE_PROPOSAL_STATUS_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "updateProposalStatus");
    private static final String MARK_HINT_FOR_REVIEW_RETRY_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "markHintForReviewRetry");
    private static final String MARK_HINT_REVIEW_ACTION_PENDING_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "markHintReviewActionPending");
    private static final String UPDATE_CHANGE_SET_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "updateChangeSet");
    private static final String DECIDE_CHANGE_ITEMS_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "decideChangeItems");
    private static final String RETRY_BULK_REJECTED_HINTS_SQL = SqlLoader.load(AdminAiJdbcRepository.class, "retryBulkRejectedHints");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PageResponse<AiRunDto> findRecentRuns(int page, int size) {
        long total = countRuns();
        if (total == 0) {
            return new PageResponse<>(List.of(), 0, 0, page, size);
        }
        List<AiRunDto> content = jdbc.query(
                FIND_RECENT_RUNS_SQL,
                new MapSqlParameterSource()
                        .addValue("limit", size)
                        .addValue("offset", page * size),
                (rs, rowNum) -> mapRun(rs));
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, total, totalPages, page, size);
    }

    public Optional<AiRunDto> findRunById(Long runId) {
        List<AiRunDto> runs = jdbc.query(
                FIND_RUN_BY_ID_SQL,
                new MapSqlParameterSource("runId", runId),
                (rs, rowNum) -> mapRun(rs));
        return runs.isEmpty() ? Optional.empty() : Optional.of(runs.getFirst());
    }

    public List<AiRunStageDto> findStagesByRunId(Long runId) {
        return jdbc.query(
                FIND_STAGES_BY_RUN_ID_SQL,
                new MapSqlParameterSource("runId", runId),
                (rs, rowNum) -> mapStage(rs));
    }

    public Optional<AiChangeSetDto> findChangeSetByRunId(Long runId) {
        List<AiChangeSetDto> sets = jdbc.query(
                FIND_CHANGE_SET_BY_RUN_ID_SQL,
                new MapSqlParameterSource("runId", runId),
                (rs, rowNum) -> mapChangeSet(rs));
        return sets.isEmpty() ? Optional.empty() : Optional.of(sets.getFirst());
    }

    public PageResponse<AiChangeSetDto> findRecentChangeSets(int page, int size) {
        long total = countChangeSets();
        if (total == 0) {
            return new PageResponse<>(List.of(), 0, 0, page, size);
        }
        List<AiChangeSetDto> content = jdbc.query(
                FIND_RECENT_CHANGE_SETS_SQL,
                new MapSqlParameterSource()
                        .addValue("limit", size)
                        .addValue("offset", page * size),
                (rs, rowNum) -> mapChangeSet(rs));
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, total, totalPages, page, size);
    }

    public Optional<AiChangeSetDto> findChangeSetById(Long changeSetId) {
        List<AiChangeSetDto> sets = jdbc.query(
                FIND_CHANGE_SET_BY_ID_SQL,
                new MapSqlParameterSource("changeSetId", changeSetId),
                (rs, rowNum) -> mapChangeSet(rs));
        return sets.isEmpty() ? Optional.empty() : Optional.of(sets.getFirst());
    }

    public Optional<AiChangeSetDto> findChangeSetByIdForUpdate(Long changeSetId) {
        List<AiChangeSetDto> sets = jdbc.query(
                FIND_CHANGE_SET_BY_ID_FOR_UPDATE_SQL,
                new MapSqlParameterSource("changeSetId", changeSetId),
                (rs, rowNum) -> mapChangeSet(rs));
        return sets.stream().findFirst();
    }

    public List<AiChangeItemDto> findItemsByChangeSetId(Long changeSetId) {
        return jdbc.query(
                FIND_ITEMS_BY_CHANGE_SET_ID_SQL,
                new MapSqlParameterSource("changeSetId", changeSetId),
                (rs, rowNum) -> mapChangeItem(rs));
    }

    public Optional<AiChangeItemDto> findItemById(Long itemId) {
        List<AiChangeItemDto> items = jdbc.query(
                FIND_ITEM_BY_ID_SQL,
                new MapSqlParameterSource("itemId", itemId),
                (rs, rowNum) -> mapChangeItem(rs));
        return items.isEmpty() ? Optional.empty() : Optional.of(items.getFirst());
    }

    public List<AiProposalDto> findProposalsByChangeSetId(Long changeSetId) {
        return jdbc.query(
                FIND_PROPOSALS_BY_CHANGE_SET_ID_SQL,
                new MapSqlParameterSource("changeSetId", changeSetId),
                (rs, rowNum) -> mapProposal(rs));
    }

    public void updateItemStatus(Long itemId, String status, Instant decidedAt) {
        jdbc.update(UPDATE_ITEM_STATUS_SQL, new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("status", status)
                .addValue("decidedAt", toTimestamp(decidedAt)));
    }

    public void updateProposalStatus(Long proposalId, String status) {
        jdbc.update(UPDATE_PROPOSAL_STATUS_SQL, new MapSqlParameterSource()
                .addValue("proposalId", proposalId)
                .addValue("status", status));
    }

    public void markHintForReviewRetry(Long hintId, Long runId, Instant now) {
        jdbc.update(MARK_HINT_FOR_REVIEW_RETRY_SQL, new MapSqlParameterSource()
                .addValue("hintId", hintId)
                .addValue("runId", runId)
                .addValue("now", toTimestamp(now)));
    }

    public void markHintReviewActionPending(Long hintId) {
        jdbc.update(MARK_HINT_REVIEW_ACTION_PENDING_SQL, new MapSqlParameterSource("hintId", hintId));
    }

    public void updateChangeSet(Long changeSetId, String status, int totalItems, int appliedItems,
                                int rejectedItems, Instant appliedAt, Instant rejectedAt) {
        jdbc.update(UPDATE_CHANGE_SET_SQL, new MapSqlParameterSource()
                .addValue("changeSetId", changeSetId)
                .addValue("status", status)
                .addValue("totalItems", totalItems)
                .addValue("appliedItems", appliedItems)
                .addValue("rejectedItems", rejectedItems)
                .addValue("appliedAt", toTimestamp(appliedAt))
                .addValue("rejectedAt", toTimestamp(rejectedAt)));
    }

    public AiChangeSetActionResponse decideChangeItems(
            Long changeSetId,
            List<String> itemTypes,
            String decision) {
        String pgArray = "{" + String.join(",", itemTypes) + "}";
        AiChangeSetActionResponse response = jdbc.queryForObject(
                DECIDE_CHANGE_ITEMS_SQL,
                new MapSqlParameterSource()
                        .addValue("changeSetId", changeSetId)
                        .addValue("itemTypes", pgArray)
                        .addValue("decision", decision),
                (rs, rowNum) -> new AiChangeSetActionResponse(
                        String.valueOf(changeSetId),
                        rs.getString("change_set_status"),
                        rs.getInt("total_items"),
                        rs.getInt("applied_items"),
                        rs.getInt("rejected_items"),
                        rs.getInt("affected_items") + " item(s) " + decision.toLowerCase())
        );
        if ("REJECTED".equals(decision)) {
            // The deployed V33 function still marks rejected hints REVIEWED. Normalize them
            // in this transaction without requiring a Flyway repair of an applied migration.
            jdbc.update(RETRY_BULK_REJECTED_HINTS_SQL, new MapSqlParameterSource()
                    .addValue("changeSetId", changeSetId)
                    .addValue("itemTypes", pgArray));
        }
        return response;
    }

    private long countRuns() {
        Long count = jdbc.queryForObject(COUNT_RUNS_SQL, Map.of(), Long.class);
        return count != null ? count : 0;
    }

    private long countChangeSets() {
        Long count = jdbc.queryForObject(COUNT_CHANGE_SETS_SQL, Map.of(), Long.class);
        return count != null ? count : 0;
    }

    private AiRunDto mapRun(ResultSet rs) throws SQLException {
        return new AiRunDto(
                rs.getLong("id"),
                rs.getString("run_type"),
                rs.getString("trigger_type"),
                rs.getString("status"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("prompt_version"),
                rs.getString("requested_by"),
                rs.getInt("total_stages"),
                rs.getInt("prepared_items"),
                rs.getInt("processed_items"),
                rs.getInt("changed_items"),
                rs.getInt("generated_items"),
                rs.getLong("estimated_input_tokens"),
                rs.getLong("estimated_output_tokens"),
                rs.getLong("actual_input_tokens"),
                rs.getLong("actual_output_tokens"),
                rs.getInt("tool_call_count"),
                deserializeMap(rs.getString("configuration")),
                deserializeMap(rs.getString("summary")),
                rs.getString("error_message"),
                getInstant(rs, "created_at"),
                getInstant(rs, "started_at"),
                getInstant(rs, "completed_at"));
    }

    private AiRunStageDto mapStage(ResultSet rs) throws SQLException {
        return new AiRunStageDto(
                rs.getLong("id"),
                rs.getLong("run_id"),
                rs.getInt("stage_index"),
                rs.getString("stage_type"),
                rs.getString("status"),
                rs.getInt("item_count"),
                rs.getLong("estimated_input_tokens"),
                rs.getLong("estimated_output_tokens"),
                rs.getLong("actual_input_tokens"),
                rs.getLong("actual_output_tokens"),
                rs.getInt("image_count"),
                deserializeMap(rs.getString("payload")),
                deserializeMap(rs.getString("result_summary")),
                rs.getString("error_message"),
                rs.getString("provider_request_id"),
                getInstant(rs, "created_at"),
                getInstant(rs, "started_at"),
                getInstant(rs, "completed_at"));
    }

    private AiChangeSetDto mapChangeSet(ResultSet rs) throws SQLException {
        return new AiChangeSetDto(
                rs.getLong("id"),
                rs.getLong("run_id"),
                rs.getString("change_set_type"),
                rs.getString("status"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getInt("total_items"),
                rs.getInt("applied_items"),
                rs.getInt("rejected_items"),
                deserializeMap(rs.getString("metadata")),
                getInstant(rs, "created_at"),
                getInstant(rs, "applied_at"),
                getInstant(rs, "rejected_at"));
    }

    private AiChangeItemDto mapChangeItem(ResultSet rs) throws SQLException {
        return new AiChangeItemDto(
                rs.getLong("id"),
                rs.getLong("change_set_id"),
                rs.getLong("run_id"),
                getLong(rs, "proposal_id"),
                rs.getString("item_type"),
                rs.getString("status"),
                getLong(rs, "hint_id"),
                getLong(rs, "country_id"),
                rs.getString("reason_summary"),
                rs.getString("diff_summary"),
                deserializeMap(rs.getString("original_snapshot")),
                deserializeMap(rs.getString("proposed_snapshot")),
                getInstant(rs, "created_at"),
                getInstant(rs, "decided_at"));
    }

    private AiProposalDto mapProposal(ResultSet rs) throws SQLException {
        return new AiProposalDto(
                rs.getLong("id"),
                rs.getLong("run_id"),
                getLong(rs, "stage_id"),
                getLong(rs, "change_set_id"),
                rs.getLong("country_id"),
                rs.getString("country_code"),
                rs.getString("country_name"),
                rs.getString("status"),
                rs.getString("category"),
                rs.getString("hint_type"),
                rs.getString("hint_text"),
                rs.getString("image_source_url"),
                rs.getString("stored_image_url"),
                getInteger(rs, "difficulty"),
                getDouble(rs),
                rs.getString("source"),
                rs.getString("source_indicator"),
                getInteger(rs, "source_year"),
                rs.getString("fact_summary"),
                rs.getString("evidence_summary"),
                rs.getString("duplicate_check_summary"),
                deserializeList(rs.getString("search_terms")),
                getInstant(rs, "created_at"));
    }

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private Map<String, Object> deserializeMap(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (IOException e) {
            log.warn("Failed to deserialize AI json payload: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<String> deserializeList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, LIST_TYPE);
        } catch (IOException e) {
            log.warn("Failed to deserialize AI list payload: {}", e.getMessage());
            return List.of();
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        return JdbcUtils.nullLong(rs, column);
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Double getDouble(ResultSet rs) throws SQLException {
        double value = rs.getDouble("quality_score");
        return rs.wasNull() ? null : value;
    }
}
