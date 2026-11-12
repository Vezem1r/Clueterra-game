package io.clueterra.questions.ai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.ai.model.changeset.AiChangeItem;
import io.clueterra.questions.ai.model.changeset.AiChangeItemStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeItemType;
import io.clueterra.questions.ai.model.changeset.AiChangeSet;
import io.clueterra.questions.ai.model.changeset.AiChangeSetStatus;
import io.clueterra.questions.ai.model.changeset.AiChangeSetType;
import io.clueterra.questions.ai.model.changeset.AiGeneratedHintProposal;
import io.clueterra.questions.ai.model.review.AiHintReviewResult;
import io.clueterra.questions.ai.model.quota.AiModelUsageWindowAggregate;
import io.clueterra.questions.ai.model.changeset.AiProposalStatus;
import io.clueterra.questions.ai.model.run.AiRunUsage;
import io.clueterra.questions.ai.tooling.dto.AiGeneratedHintProposalToolSummary;
import io.clueterra.questions.repository.util.SqlLoader;
import io.hypersistence.tsid.TSID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Slf4j
public class AiExecutionRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final String INSERT_CHANGE_SET_SQL = SqlLoader.load(AiExecutionRepository.class, "insertChangeSet");
    private static final String INSERT_CHANGE_ITEM_SQL = SqlLoader.load(AiExecutionRepository.class, "insertChangeItem");
    private static final String INSERT_RUN_USAGE_SQL = SqlLoader.load(AiExecutionRepository.class, "insertRunUsage");
    private static final String INSERT_HINT_REVIEW_RESULT_SQL = SqlLoader.load(AiExecutionRepository.class, "insertHintReviewResult");
    private static final String INSERT_GENERATED_HINT_PROPOSAL_SQL = SqlLoader.load(AiExecutionRepository.class, "insertGeneratedHintProposal");
    private static final String FIND_CHANGE_SET_BY_ID_FOR_UPDATE_SQL = SqlLoader.load(AiExecutionRepository.class, "findChangeSetByIdForUpdate");
    private static final String FIND_CHANGE_SET_BY_RUN_ID_SQL = SqlLoader.load(AiExecutionRepository.class, "findChangeSetByRunId");
    private static final String FIND_CHANGE_ITEMS_BY_CHANGE_SET_ID_SQL = SqlLoader.load(AiExecutionRepository.class, "findChangeItemsByChangeSetId");
    private static final String FIND_CHANGE_ITEMS_BY_CHANGE_SET_ID_FOR_UPDATE_SQL = SqlLoader.load(AiExecutionRepository.class, "findChangeItemsByChangeSetIdForUpdate");
    private static final String FIND_PROPOSAL_BY_ID_SQL = SqlLoader.load(AiExecutionRepository.class, "findProposalById");
    private static final String FIND_PROPOSAL_TEXTS_BY_COUNTRY_IDS_SQL = SqlLoader.load(AiExecutionRepository.class, "findProposalTextsByCountryIds");
    private static final String FIND_PROPOSAL_SUMMARIES_BY_COUNTRY_ID_SQL = SqlLoader.load(AiExecutionRepository.class, "findProposalSummariesByCountryId");
    private static final String FIND_PROPOSAL_CATEGORY_COUNTS_BY_COUNTRY_ID_SQL = SqlLoader.load(AiExecutionRepository.class, "findProposalCategoryCountsByCountryId");
    private static final String FIND_USAGE_SNAPSHOT_FOR_MODEL_SQL = SqlLoader.load(AiExecutionRepository.class, "findUsageSnapshotForModel");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AiExecutionRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public AiChangeSet saveChangeSet(AiChangeSet changeSet) {
        long id = changeSet.id() != null ? changeSet.id() : TSID.Factory.getTsid().toLong();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("runId", changeSet.runId())
                .addValue("changeSetType", changeSet.changeSetType().name())
                .addValue("status", changeSet.status().name())
                .addValue("title", changeSet.title())
                .addValue("summary", changeSet.summary())
                .addValue("totalItems", changeSet.totalItems())
                .addValue("appliedItems", changeSet.appliedItems())
                .addValue("rejectedItems", changeSet.rejectedItems())
                .addValue("metadata", serialize(changeSet.metadata()))
                .addValue("createdAt", toTimestamp(changeSet.createdAt()))
                .addValue("appliedAt", toTimestamp(changeSet.appliedAt()))
                .addValue("rejectedAt", toTimestamp(changeSet.rejectedAt()));
        jdbc.update(INSERT_CHANGE_SET_SQL, params);
        return new AiChangeSet(
                id,
                changeSet.runId(),
                changeSet.changeSetType(),
                changeSet.status(),
                changeSet.title(),
                changeSet.summary(),
                changeSet.totalItems(),
                changeSet.appliedItems(),
                changeSet.rejectedItems(),
                changeSet.metadata(),
                changeSet.createdAt(),
                changeSet.appliedAt(),
                changeSet.rejectedAt()
        );
    }

    public AiChangeItem saveChangeItem(AiChangeItem item) {
        long id = item.id() != null ? item.id() : TSID.Factory.getTsid().toLong();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("changeSetId", item.changeSetId())
                .addValue("runId", item.runId())
                .addValue("proposalId", item.proposalId())
                .addValue("itemType", item.itemType().name())
                .addValue("status", item.status().name())
                .addValue("hintId", item.hintId())
                .addValue("countryId", item.countryId())
                .addValue("reasonSummary", item.reasonSummary())
                .addValue("diffSummary", item.diffSummary())
                .addValue("originalSnapshot", serialize(item.originalSnapshot()))
                .addValue("proposedSnapshot", serialize(item.proposedSnapshot()))
                .addValue("createdAt", toTimestamp(item.createdAt()))
                .addValue("decidedAt", toTimestamp(item.decidedAt()));
        jdbc.update(INSERT_CHANGE_ITEM_SQL, params);
        return new AiChangeItem(
                id,
                item.changeSetId(),
                item.runId(),
                item.proposalId(),
                item.itemType(),
                item.status(),
                item.hintId(),
                item.countryId(),
                item.reasonSummary(),
                item.diffSummary(),
                item.originalSnapshot(),
                item.proposedSnapshot(),
                item.createdAt(),
                item.decidedAt()
        );
    }

    public void saveRunUsage(AiRunUsage usage) {
        long id = usage.id() != null ? usage.id() : TSID.Factory.getTsid().toLong();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("runId", usage.runId())
                .addValue("stageId", usage.stageId())
                .addValue("requestIndex", usage.requestIndex())
                .addValue("providerRequestId", usage.providerRequestId())
                .addValue("finishReason", usage.finishReason())
                .addValue("estimatedInputTokens", usage.estimatedInputTokens())
                .addValue("promptTokens", usage.promptTokens())
                .addValue("completionTokens", usage.completionTokens())
                .addValue("totalTokens", usage.totalTokens())
                .addValue("toolCallCount", usage.toolCallCount())
                .addValue("imageInputCount", usage.imageInputCount())
                .addValue("latencyMs", usage.latencyMs())
                .addValue("metadata", serialize(usage.metadata()))
                .addValue("createdAt", toTimestamp(usage.createdAt()));
        jdbc.update(INSERT_RUN_USAGE_SQL, params);
        new AiRunUsage(
                id,
                usage.runId(),
                usage.stageId(),
                usage.requestIndex(),
                usage.providerRequestId(),
                usage.finishReason(),
                usage.estimatedInputTokens(),
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.toolCallCount(),
                usage.imageInputCount(),
                usage.latencyMs(),
                usage.metadata(),
                usage.createdAt()
        );
    }

    public void saveHintReviewResult(AiHintReviewResult result) {
        long id = result.id() != null ? result.id() : TSID.Factory.getTsid().toLong();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("runId", result.runId())
                .addValue("stageId", result.stageId())
                .addValue("hintId", result.hintId())
                .addValue("sourceRevisionHash", result.sourceRevisionHash())
                .addValue("decision", result.decision().name())
                .addValue("confidence", result.confidence())
                .addValue("summaryReason", result.summaryReason())
                .addValue("evidenceSummary", result.evidenceSummary())
                .addValue("proposedText", result.proposedText())
                .addValue("proposedDifficulty", result.proposedDifficulty())
                .addValue("proposedImageUrl", result.proposedImageUrl())
                .addValue("disableReason", result.disableReason())
                .addValue("retryAfterDays", result.retryAfterDays())
                .addValue("changeSetId", result.changeSetId())
                .addValue("createdAt", toTimestamp(result.createdAt()));
        jdbc.update(INSERT_HINT_REVIEW_RESULT_SQL, params);
    }

    public AiGeneratedHintProposal saveGeneratedHintProposal(AiGeneratedHintProposal proposal) {
        long id = proposal.id() != null ? proposal.id() : TSID.Factory.getTsid().toLong();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("runId", proposal.runId())
                .addValue("stageId", proposal.stageId())
                .addValue("changeSetId", proposal.changeSetId())
                .addValue("countryId", proposal.countryId())
                .addValue("status", proposal.status().name())
                .addValue("category", proposal.category())
                .addValue("hintType", proposal.hintType())
                .addValue("hintText", proposal.hintText())
                .addValue("imageSourceUrl", proposal.imageSourceUrl())
                .addValue("storedImageUrl", proposal.storedImageUrl())
                .addValue("difficulty", proposal.difficulty())
                .addValue("qualityScore", proposal.qualityScore())
                .addValue("source", proposal.source())
                .addValue("sourceIndicator", proposal.sourceIndicator())
                .addValue("sourceYear", proposal.sourceYear())
                .addValue("factSummary", proposal.factSummary())
                .addValue("evidenceSummary", proposal.evidenceSummary())
                .addValue("duplicateCheckSummary", proposal.duplicateCheckSummary())
                .addValue("searchTerms", serialize(proposal.searchTerms()))
                .addValue("createdAt", toTimestamp(proposal.createdAt()));
        jdbc.update(INSERT_GENERATED_HINT_PROPOSAL_SQL, params);
        return new AiGeneratedHintProposal(
                id,
                proposal.runId(),
                proposal.stageId(),
                proposal.changeSetId(),
                proposal.countryId(),
                proposal.status(),
                proposal.category(),
                proposal.hintType(),
                proposal.hintText(),
                proposal.imageSourceUrl(),
                proposal.storedImageUrl(),
                proposal.difficulty(),
                proposal.qualityScore(),
                proposal.source(),
                proposal.sourceIndicator(),
                proposal.sourceYear(),
                proposal.factSummary(),
                proposal.evidenceSummary(),
                proposal.duplicateCheckSummary(),
                proposal.searchTerms(),
                proposal.createdAt()
        );
    }

    public Optional<AiChangeSet> findChangeSetByIdForUpdate(Long changeSetId) {
        List<AiChangeSet> changeSets = jdbc.query(FIND_CHANGE_SET_BY_ID_FOR_UPDATE_SQL,
                new MapSqlParameterSource("changeSetId", changeSetId),
                (rs, rowNum) -> mapChangeSet(rs));
        return changeSets.stream().findFirst();
    }

    public Optional<AiChangeSet> findChangeSetByRunId(Long runId) {
        List<AiChangeSet> changeSets = jdbc.query(FIND_CHANGE_SET_BY_RUN_ID_SQL,
                new MapSqlParameterSource("runId", runId),
                (rs, rowNum) -> mapChangeSet(rs)
        );
        return changeSets.isEmpty() ? Optional.empty() : Optional.of(changeSets.getFirst());
    }

    public List<AiChangeItem> findItemsByChangeSetId(Long changeSetId) {
        return jdbc.query(FIND_CHANGE_ITEMS_BY_CHANGE_SET_ID_SQL,
                new MapSqlParameterSource("changeSetId", changeSetId),
                (rs, rowNum) -> mapChangeItem(rs)
        );
    }

    public List<AiChangeItem> findItemsByChangeSetIdForUpdate(Long changeSetId) {
        return jdbc.query(FIND_CHANGE_ITEMS_BY_CHANGE_SET_ID_FOR_UPDATE_SQL,
                new MapSqlParameterSource("changeSetId", changeSetId),
                (rs, rowNum) -> mapChangeItem(rs));
    }

    public List<Long> findApplicableProposalIds(Long changeSetId) {
        return jdbc.queryForList("""
                SELECT proposal_id
                FROM cl_ai_schema.ai_hint_change_item
                WHERE change_set_id = :changeSetId
                  AND proposal_id IS NOT NULL
                  AND status IN ('PENDING', 'APPROVED')
                """, new MapSqlParameterSource("changeSetId", changeSetId), Long.class);
    }

    public Optional<AiGeneratedHintProposal> findProposalById(Long proposalId) {
        List<AiGeneratedHintProposal> proposals = jdbc.query(FIND_PROPOSAL_BY_ID_SQL,
                new MapSqlParameterSource("proposalId", proposalId),
                (rs, rowNum) -> mapProposal(rs)
        );
        return proposals.isEmpty() ? Optional.empty() : Optional.of(proposals.getFirst());
    }

    public Map<Long, List<String>> findProposalTextsByCountryIds(List<Long> countryIds) {
        if (countryIds == null || countryIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> textsByCountryId = new LinkedHashMap<>();
        jdbc.query(FIND_PROPOSAL_TEXTS_BY_COUNTRY_IDS_SQL,
                new MapSqlParameterSource("countryIds", countryIds),
                (RowCallbackHandler) rs -> textsByCountryId
                        .computeIfAbsent(rs.getLong("country_id"), ignored -> new ArrayList<>())
                        .add(rs.getString("hint_text"))
        );
        return textsByCountryId;
    }

    public List<AiGeneratedHintProposalToolSummary> findProposalSummariesByCountryId(Long countryId, int limit) {
        return jdbc.query(FIND_PROPOSAL_SUMMARIES_BY_COUNTRY_ID_SQL,
                new MapSqlParameterSource()
                        .addValue("countryId", countryId)
                        .addValue("limit", limit),
                (rs, rowNum) -> new AiGeneratedHintProposalToolSummary(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getString("category"),
                        rs.getString("hint_type"),
                        rs.getString("hint_text"),
                        rs.getString("image_url"),
                        getInteger(rs, "difficulty"),
                        getDouble(rs),
                        rs.getString("source_indicator"),
                        getInstant(rs, "created_at")
                )
        );
    }

    public Map<String, Integer> findProposalCategoryCountsByCountryId(Long countryId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        jdbc.query(FIND_PROPOSAL_CATEGORY_COUNTS_BY_COUNTRY_ID_SQL,
                new MapSqlParameterSource().addValue("countryId", countryId),
                (rs, rowNum) -> {
                    result.put(rs.getString("category"), rs.getInt("cnt"));
                    return null;
                });
        return result;
    }

    public AiModelUsageWindowAggregate findUsageSnapshotForModel(
            String provider,
            String model,
            Instant minuteWindowStart,
            Instant earliestWindowStart,
            Instant dayWindowStart,
            Instant dayWindowEnd) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("provider", provider)
                .addValue("model", model)
                .addValue("minuteWindowStart", toTimestamp(minuteWindowStart))
                .addValue("earliestWindowStart", toTimestamp(earliestWindowStart))
                .addValue("dayWindowStart", toTimestamp(dayWindowStart))
                .addValue("dayWindowEnd", toTimestamp(dayWindowEnd));
        return jdbc.query(FIND_USAGE_SNAPSHOT_FOR_MODEL_SQL,
                params,
                rs -> rs.next()
                        ? new AiModelUsageWindowAggregate(
                        rs.getInt("requests_last_minute"),
                        rs.getLong("input_tokens_last_minute"),
                        rs.getInt("requests_today"),
                        rs.getInt("grounded_prompts_today"),
                        rs.getInt("failed_requests_today"),
                        rs.getLong("input_tokens_today"),
                        rs.getLong("output_tokens_today"))
                        : new AiModelUsageWindowAggregate(0, 0, 0, 0, 0, 0, 0)
        );
    }

    private AiChangeSet mapChangeSet(ResultSet rs) throws SQLException {
        return new AiChangeSet(
                rs.getLong("id"),
                rs.getLong("run_id"),
                AiChangeSetType.valueOf(rs.getString("change_set_type")),
                AiChangeSetStatus.valueOf(rs.getString("status")),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getInt("total_items"),
                rs.getInt("applied_items"),
                rs.getInt("rejected_items"),
                deserializeMap(rs.getString("metadata")),
                getInstant(rs, "created_at"),
                getInstant(rs, "applied_at"),
                getInstant(rs, "rejected_at")
        );
    }

    private AiChangeItem mapChangeItem(ResultSet rs) throws SQLException {
        return new AiChangeItem(
                rs.getLong("id"),
                rs.getLong("change_set_id"),
                rs.getLong("run_id"),
                getLong(rs, "proposal_id"),
                AiChangeItemType.valueOf(rs.getString("item_type")),
                AiChangeItemStatus.valueOf(rs.getString("status")),
                getLong(rs, "hint_id"),
                getLong(rs, "country_id"),
                rs.getString("reason_summary"),
                rs.getString("diff_summary"),
                deserializeMap(rs.getString("original_snapshot")),
                deserializeMap(rs.getString("proposed_snapshot")),
                getInstant(rs, "created_at"),
                getInstant(rs, "decided_at")
        );
    }

    private AiGeneratedHintProposal mapProposal(ResultSet rs) throws SQLException {
        return new AiGeneratedHintProposal(
                rs.getLong("id"),
                rs.getLong("run_id"),
                getLong(rs, "stage_id"),
                getLong(rs, "change_set_id"),
                rs.getLong("country_id"),
                AiProposalStatus.valueOf(rs.getString("status")),
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
                getInstant(rs, "created_at")
        );
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize AI execution payload", e);
            return "{}";
        }
    }

    private Map<String, Object> deserializeMap(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (IOException e) {
            log.warn("Failed to deserialize AI map payload", e);
            return Map.of();
        }
    }

    private List<String> deserializeList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (IOException e) {
            log.warn("Failed to deserialize AI list payload", e);
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
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
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
