package io.clueterra.questions.ai.repository;

import java.time.Clock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiRunStatus;
import io.clueterra.questions.ai.model.run.AiRunTriggerType;
import io.clueterra.questions.ai.model.run.AiRunType;
import io.clueterra.questions.ai.model.run.AiStageStatus;
import io.clueterra.questions.ai.model.run.AiStageType;
import io.clueterra.questions.repository.util.SqlLoader;
import io.hypersistence.tsid.TSID;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AiRunRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String INSERT_RUN_SQL = SqlLoader.load(AiRunRepository.class, "insertRun");
    private static final String INSERT_STAGE_SQL = SqlLoader.load(AiRunRepository.class, "insertStage");
    private static final String COUNT_PENDING_SQL = SqlLoader.load(AiRunRepository.class, "countPendingRunsByType");
    private static final String FIND_STALE_PENDING_RUN_IDS_SQL = SqlLoader.load(AiRunRepository.class, "findStalePendingRunIdsByType");
    private static final String FIND_ABANDONED_PREPARED_RUN_IDS_SQL = SqlLoader.load(AiRunRepository.class, "findAbandonedPreparedRunIdsByType");
    private static final String CANCEL_RUNS_SQL = SqlLoader.load(AiRunRepository.class, "cancelRunsByIds");
    private static final String SKIP_PENDING_STAGES_SQL = SqlLoader.load(AiRunRepository.class, "skipPendingStagesByRunIds");
    private static final String FIND_RUN_BY_ID_SQL = SqlLoader.load(AiRunRepository.class, "findRunById");
    private static final String FIND_STAGES_BY_RUN_ID_SQL = SqlLoader.load(AiRunRepository.class, "findStagesByRunId");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public boolean hasPendingRun(AiRunType runType) {
        Integer count = jdbc.queryForObject(
                COUNT_PENDING_SQL,
                new MapSqlParameterSource("runType", runType.name()),
                Integer.class
        );
        return count != null && count > 0;
    }

    public int cancelStalePendingRuns(AiRunType runType, Instant cutoff, String reason) {
        List<Long> runIds = jdbc.queryForList(
                FIND_STALE_PENDING_RUN_IDS_SQL,
                new MapSqlParameterSource()
                        .addValue("runType", runType.name())
                        .addValue("cutoff", Timestamp.from(cutoff)),
                Long.class
        );
        if (runIds.isEmpty()) {
            return 0;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("runIds", runIds)
                .addValue("reason", reason)
                .addValue("completedAt", Timestamp.from(Instant.now(clock)));
        jdbc.update(SKIP_PENDING_STAGES_SQL, params);
        return jdbc.update(CANCEL_RUNS_SQL, params);
    }

    public int cancelAbandonedPreparedRuns(AiRunType runType, Instant cutoff, String reason) {
        List<Long> runIds = jdbc.queryForList(
                FIND_ABANDONED_PREPARED_RUN_IDS_SQL,
                new MapSqlParameterSource()
                        .addValue("runType", runType.name())
                        .addValue("cutoff", Timestamp.from(cutoff)),
                Long.class
        );
        if (runIds.isEmpty()) {
            return 0;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("runIds", runIds)
                .addValue("reason", reason)
                .addValue("completedAt", Timestamp.from(Instant.now(clock)));
        jdbc.update(SKIP_PENDING_STAGES_SQL, params);
        return jdbc.update(CANCEL_RUNS_SQL, params);
    }

    public AiRun save(AiRun run) {
        long id = run.id() != null ? run.id() : TSID.Factory.getTsid().toLong();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("runType", run.runType().name())
                .addValue("triggerType", run.triggerType().name())
                .addValue("status", run.status().name())
                .addValue("provider", run.provider())
                .addValue("model", run.model())
                .addValue("promptVersion", run.promptVersion())
                .addValue("requestedBy", run.requestedBy())
                .addValue("totalStages", run.totalStages())
                .addValue("preparedItems", run.preparedItems())
                .addValue("processedItems", run.processedItems())
                .addValue("changedItems", run.changedItems())
                .addValue("generatedItems", run.generatedItems())
                .addValue("estimatedInputTokens", run.estimatedInputTokens())
                .addValue("estimatedOutputTokens", run.estimatedOutputTokens())
                .addValue("actualInputTokens", run.actualInputTokens())
                .addValue("actualOutputTokens", run.actualOutputTokens())
                .addValue("toolCallCount", run.toolCallCount())
                .addValue("configuration", serialize(run.configuration()))
                .addValue("summary", serialize(run.summary()))
                .addValue("errorMessage", run.errorMessage())
                .addValue("createdAt", run.createdAt() != null ? Timestamp.from(run.createdAt()) : null)
                .addValue("startedAt", run.startedAt() != null ? Timestamp.from(run.startedAt()) : null)
                .addValue("completedAt", run.completedAt() != null ? Timestamp.from(run.completedAt()) : null);

        jdbc.update(INSERT_RUN_SQL, params);
        return new AiRun(
                id,
                run.runType(),
                run.triggerType(),
                run.status(),
                run.provider(),
                run.model(),
                run.promptVersion(),
                run.requestedBy(),
                run.totalStages(),
                run.preparedItems(),
                run.processedItems(),
                run.changedItems(),
                run.generatedItems(),
                run.estimatedInputTokens(),
                run.estimatedOutputTokens(),
                run.actualInputTokens(),
                run.actualOutputTokens(),
                run.toolCallCount(),
                run.configuration(),
                run.summary(),
                run.errorMessage(),
                run.createdAt(),
                run.startedAt(),
                run.completedAt()
        );
    }

    public List<AiRunStage> saveStages(List<AiRunStage> stages) {
        List<AiRunStage> saved = new ArrayList<>();
        for (AiRunStage stage : stages) {
            long id = stage.id() != null ? stage.id() : TSID.Factory.getTsid().toLong();
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("runId", stage.runId())
                    .addValue("stageIndex", stage.stageIndex())
                    .addValue("stageType", stage.stageType().name())
                    .addValue("status", stage.status().name())
                    .addValue("itemCount", stage.itemCount())
                    .addValue("estimatedInputTokens", stage.estimatedInputTokens())
                    .addValue("estimatedOutputTokens", stage.estimatedOutputTokens())
                    .addValue("actualInputTokens", stage.actualInputTokens())
                    .addValue("actualOutputTokens", stage.actualOutputTokens())
                    .addValue("imageCount", stage.imageCount())
                    .addValue("payload", serialize(stage.payload()))
                    .addValue("resultSummary", serialize(stage.resultSummary()))
                    .addValue("errorMessage", stage.errorMessage())
                    .addValue("providerRequestId", stage.providerRequestId())
                    .addValue("createdAt", stage.createdAt() != null ? Timestamp.from(stage.createdAt()) : null)
                    .addValue("startedAt", stage.startedAt() != null ? Timestamp.from(stage.startedAt()) : null)
                    .addValue("completedAt", stage.completedAt() != null ? Timestamp.from(stage.completedAt()) : null);

            jdbc.update(INSERT_STAGE_SQL, params);
            saved.add(new AiRunStage(
                    id,
                    stage.runId(),
                    stage.stageIndex(),
                    stage.stageType(),
                    stage.status(),
                    stage.itemCount(),
                    stage.estimatedInputTokens(),
                    stage.estimatedOutputTokens(),
                    stage.actualInputTokens(),
                    stage.actualOutputTokens(),
                    stage.imageCount(),
                    stage.payload(),
                    stage.resultSummary(),
                    stage.errorMessage(),
                    stage.providerRequestId(),
                    stage.createdAt(),
                    stage.startedAt(),
                    stage.completedAt()
            ));
        }
        return saved;
    }

    public Optional<AiRun> findById(Long runId) {
        List<AiRun> runs = jdbc.query(
                FIND_RUN_BY_ID_SQL,
                new MapSqlParameterSource("runId", runId),
                (rs, rowNum) -> mapRun(rs)
        );
        return runs.isEmpty() ? Optional.empty() : Optional.of(runs.getFirst());
    }

    public List<AiRunStage> findStagesByRunId(Long runId) {
        return jdbc.query(
                FIND_STAGES_BY_RUN_ID_SQL,
                new MapSqlParameterSource("runId", runId),
                (rs, rowNum) -> mapStage(rs)
        );
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize AI run payload", e);
            return "{}";
        }
    }

    private Map<String, Object> deserialize(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (IOException e) {
            log.warn("Failed to deserialize AI json payload", e);
            return Map.of();
        }
    }

    private AiRun mapRun(ResultSet rs) throws SQLException {
        return new AiRun(
                rs.getLong("id"),
                AiRunType.valueOf(rs.getString("run_type")),
                AiRunTriggerType.valueOf(rs.getString("trigger_type")),
                AiRunStatus.valueOf(rs.getString("status")),
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
                deserialize(rs.getString("configuration")),
                deserialize(rs.getString("summary")),
                rs.getString("error_message"),
                getInstant(rs, "created_at"),
                getInstant(rs, "started_at"),
                getInstant(rs, "completed_at")
        );
    }

    private AiRunStage mapStage(ResultSet rs) throws SQLException {
        return new AiRunStage(
                rs.getLong("id"),
                rs.getLong("run_id"),
                rs.getInt("stage_index"),
                AiStageType.valueOf(rs.getString("stage_type")),
                AiStageStatus.valueOf(rs.getString("status")),
                rs.getInt("item_count"),
                rs.getLong("estimated_input_tokens"),
                rs.getLong("estimated_output_tokens"),
                rs.getLong("actual_input_tokens"),
                rs.getLong("actual_output_tokens"),
                rs.getInt("image_count"),
                deserialize(rs.getString("payload")),
                deserialize(rs.getString("result_summary")),
                rs.getString("error_message"),
                rs.getString("provider_request_id"),
                getInstant(rs, "created_at"),
                getInstant(rs, "started_at"),
                getInstant(rs, "completed_at")
        );
    }

    private Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
