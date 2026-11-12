package io.clueterra.questions.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.entity.OperationBatchLog;
import io.clueterra.questions.repository.util.SqlLoader;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@Slf4j
@RequiredArgsConstructor
public class OperationBatchLogRepository {

    private static final String MARK_INTERRUPTED_SQL = SqlLoader.load(OperationBatchLogRepository.class, "markInterrupted");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private static final String INSERT_SQL = """
            INSERT INTO operation_batch_log (id, operation_type, trigger_type, status, total_items,
                successful_items, failed_items, started_at, completed_at, duration_ms, error_message, summary)
            VALUES (:id, :operationType, :triggerType, :status, :totalItems,
                :successfulItems, :failedItems, :startedAt, :completedAt, :durationMs, :errorMessage, :summary::jsonb)
            """;

    private static final String UPDATE_SQL = """
            UPDATE operation_batch_log SET
                status = :status, total_items = :totalItems, successful_items = :successfulItems,
                failed_items = :failedItems, completed_at = :completedAt, duration_ms = :durationMs,
                error_message = :errorMessage, summary = :summary::jsonb
            WHERE id = :id
            """;

    public OperationBatchLog save(OperationBatchLog batch) {
        if (batch.getId() == null) {
            batch.setId(TSID.Factory.getTsid().toLong());
        }

        MapSqlParameterSource params = createParams(batch);
        jdbc.update(INSERT_SQL, params);
        return batch;
    }

    public OperationBatchLog update(OperationBatchLog batch) {
        MapSqlParameterSource params = createParams(batch);
        jdbc.update(UPDATE_SQL, params);
        return batch;
    }

    public int markInterrupted(String operationId, Instant completedAt, String errorMessage) {
        return jdbc.update(
                MARK_INTERRUPTED_SQL,
                new MapSqlParameterSource()
                        .addValue("operationId", operationId)
                        .addValue("completedAt", Timestamp.from(completedAt))
                        .addValue("errorMessage", errorMessage));
    }

    private MapSqlParameterSource createParams(OperationBatchLog batch) {
        String summaryJson = serializeToJson(batch.getSummary());

        return new MapSqlParameterSource()
                .addValue("id", batch.getId())
                .addValue("operationType", batch.getOperationType().name())
                .addValue("triggerType", batch.getTriggerType().name())
                .addValue("status", batch.getStatus().name())
                .addValue("totalItems", batch.getTotalItems())
                .addValue("successfulItems", batch.getSuccessfulItems())
                .addValue("failedItems", batch.getFailedItems())
                .addValue("startedAt", batch.getStartedAt() != null ? Timestamp.from(batch.getStartedAt()) : null)
                .addValue("completedAt", batch.getCompletedAt() != null ? Timestamp.from(batch.getCompletedAt()) : null)
                .addValue("durationMs", batch.getDurationMs())
                .addValue("errorMessage", batch.getErrorMessage())
                .addValue("summary", summaryJson);
    }

    private String serializeToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON", e);
            return "{}";
        }
    }
}
