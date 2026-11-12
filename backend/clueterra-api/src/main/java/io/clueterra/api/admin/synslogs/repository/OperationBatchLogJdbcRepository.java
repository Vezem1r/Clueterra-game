package io.clueterra.api.admin.synslogs.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.synslogs.dto.GroupedErrorDto;
import io.clueterra.api.admin.synslogs.dto.OperationBatchLogAdminDto;
import io.clueterra.api.common.utils.database.JdbcUtils;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OperationBatchLogJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_SQL = """
            SELECT id, operation_type, trigger_type, status,
                   total_items, successful_items, failed_items,
                   started_at, completed_at, duration_ms, error_message, summary
            FROM operation_batch_log
            """;

    private static final String GROUPED_ERRORS_SQL = """
            SELECT error_message,
                   COUNT(*) AS cnt,
                   MIN(started_at) AS first_occurrence,
                   MAX(started_at) AS last_occurrence,
                   array_agg(DISTINCT operation_type) AS operation_types
            FROM operation_batch_log
            WHERE error_message IS NOT NULL AND error_message != ''
            GROUP BY error_message
            ORDER BY cnt DESC
            LIMIT :lim
            """;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM operation_batch_log";

    private static final Set<String> ALLOWED_SORT = Set.of(
            "operationType", "triggerType", "status", "totalItems",
            "successfulItems", "failedItems", "startedAt", "durationMs");

    private static final Map<String, String> SORT_MAP = Map.of(
            "operationType", "operation_type",
            "triggerType", "trigger_type",
            "status", "status",
            "totalItems", "total_items",
            "successfulItems", "successful_items",
            "failedItems", "failed_items",
            "startedAt", "started_at",
            "durationMs", "duration_ms");

    private static final RowMapper<OperationBatchLogAdminDto> MAPPER = (rs, rowNum) -> mapRow(rs);

    private static OperationBatchLogAdminDto mapRow(ResultSet rs) throws SQLException {
        return new OperationBatchLogAdminDto(
                rs.getLong("id"),
                rs.getString("operation_type"),
                rs.getString("trigger_type"),
                rs.getString("status"),
                rs.getInt("total_items"),
                rs.getInt("successful_items"),
                rs.getInt("failed_items"),
                JdbcUtils.ts(rs, "started_at"),
                JdbcUtils.ts(rs, "completed_at"),
                JdbcUtils.nullLong(rs, "duration_ms"),
                rs.getString("error_message"),
                JdbcUtils.jsonbMap(rs, "summary"));
    }

    public Page<OperationBatchLogAdminDto> findAll(String operationType, String status,
                                                   String triggerType, String startAfter,
                                                   String startBefore, String sortBy,
                                                   String sortDir, Pageable pageable) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .eq("operation_type", "operationType", operationType)
                .eq("status", "status", status)
                .eq("trigger_type", "triggerType", triggerType)
                .gte("started_at", "startAfter", startAfter, "timestamp")
                .lte("started_at", "startBefore", startBefore, "timestamp");

        return JdbcUtils.paginate(jdbc, COUNT_SQL, SELECT_SQL, f.whereClause(), f.params(),
                sortBy, sortDir, ALLOWED_SORT, SORT_MAP, "started_at",
                pageable, MAPPER);
    }

    public List<String> findDistinctTypes() {
        return jdbc.queryForList("SELECT DISTINCT operation_type FROM operation_batch_log ORDER BY operation_type", Map.of(), String.class);
    }

    public List<String> findDistinctStatuses() {
        return jdbc.queryForList("SELECT DISTINCT status FROM operation_batch_log ORDER BY status", Map.of(), String.class);
    }

    public List<String> findDistinctTriggerTypes() {
        return jdbc.queryForList("SELECT DISTINCT trigger_type FROM operation_batch_log ORDER BY trigger_type", Map.of(), String.class);
    }

    public List<GroupedErrorDto> findGroupedErrors(int limit) {
        return jdbc.query(GROUPED_ERRORS_SQL, Map.of("lim", limit), (rs, rowNum) -> {
            java.sql.Array arr = rs.getArray("operation_types");
            List<String> types = arr != null
                    ? List.of((String[]) arr.getArray())
                    : List.of();
            return new GroupedErrorDto(
                    rs.getString("error_message"),
                    rs.getInt("cnt"),
                    JdbcUtils.ts(rs, "first_occurrence"),
                    JdbcUtils.ts(rs, "last_occurrence"),
                    types);
        });
    }
}
