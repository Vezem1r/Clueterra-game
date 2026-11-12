package io.clueterra.budget.repository;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.budget.project.BudgetSyncRunRow;
import io.clueterra.budget.util.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BudgetSyncRunRepository {

    private static final String START_RUN = SqlLoader.load(BudgetSyncRunRepository.class, "startRun");
    private static final String FINISH_SUCCESS = SqlLoader.load(BudgetSyncRunRepository.class, "finishSuccess");
    private static final String FINISH_PARTIAL = SqlLoader.load(BudgetSyncRunRepository.class, "finishPartial");
    private static final String FINISH_FAILURE = SqlLoader.load(BudgetSyncRunRepository.class, "finishFailure");
    private static final String HEARTBEAT = SqlLoader.load(BudgetSyncRunRepository.class, "heartbeat");
    private static final RowMapper<BudgetSyncRunRow> ROW_MAPPER =
            (resultSet, rowNumber) ->
                    new BudgetSyncRunRow(
                            resultSet.getLong("id"),
                            resultSet.getString("source_code"),
                            resultSet.getString("status"),
                            resultSet.getTimestamp("started_at").toInstant(),
                            resultSet.getTimestamp("completed_at") == null
                                    ? null
                                    : resultSet.getTimestamp("completed_at").toInstant(),
                            resultSet.getInt("categories_attempted"),
                            resultSet.getInt("categories_succeeded"),
                            resultSet.getInt("records_discovered"),
                            resultSet.getInt("records_retained"),
                            resultSet.getString("error_message"));

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<Long> startRun(String sourceCode, Duration staleTimeout) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("sourceCode", sourceCode)
                        .addValue("staleTimeout", staleTimeout.toSeconds() + " seconds");
        return jdbc
                .query(
                        START_RUN,
                        parameters,
                        (resultSet, rowNumber) -> resultSet.getObject("run_id", Long.class))
                .stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    public void heartbeat(long runId) {
        int updated = jdbc.update(HEARTBEAT, Map.of("runId", runId));
        if (updated != 1) {
            throw new IllegalStateException("Synchronization run is no longer active");
        }
    }

    public BudgetSyncRunRow finishSuccess(
            long runId,
            int categoriesAttempted,
            int categoriesSucceeded,
            int recordsDiscovered,
            int recordsRetained) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("categoriesAttempted", categoriesAttempted)
                        .addValue("categoriesSucceeded", categoriesSucceeded)
                        .addValue("recordsDiscovered", recordsDiscovered)
                        .addValue("recordsRetained", recordsRetained);
        return singleUpdatedRow(FINISH_SUCCESS, parameters);
    }

    public void finishFailure(
            long runId,
            int categoriesAttempted,
            int categoriesSucceeded,
            int recordsDiscovered,
            int recordsRetained,
            String errorMessage) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("categoriesAttempted", categoriesAttempted)
                        .addValue("categoriesSucceeded", categoriesSucceeded)
                        .addValue("recordsDiscovered", recordsDiscovered)
                        .addValue("recordsRetained", recordsRetained)
                        .addValue(
                                "errorMessage",
                                errorMessage == null ? "Unknown synchronization failure" : errorMessage);
        singleUpdatedRow(FINISH_FAILURE, parameters);
    }

    public BudgetSyncRunRow finishPartial(
            long runId,
            int categoriesAttempted,
            int categoriesSucceeded,
            int recordsDiscovered,
            int recordsRetained,
            String errorMessage) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("categoriesAttempted", categoriesAttempted)
                        .addValue("categoriesSucceeded", categoriesSucceeded)
                        .addValue("recordsDiscovered", recordsDiscovered)
                        .addValue("recordsRetained", recordsRetained)
                        .addValue("errorMessage", errorMessage);
        return singleUpdatedRow(FINISH_PARTIAL, parameters);
    }

    private BudgetSyncRunRow singleUpdatedRow(String sql, MapSqlParameterSource parameters) {
        return jdbc.query(sql, parameters, ROW_MAPPER).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Synchronization run is no longer active"));
    }
}
