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

import io.clueterra.api.admin.synslogs.dto.DataSyncLogAdminDto;
import io.clueterra.api.admin.synslogs.dto.GroupedErrorDto;
import io.clueterra.api.common.utils.database.JdbcUtils;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DataSyncLogJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_SQL = """
            SELECT id, source, sync_type, status,
                   countries_processed, statistics_saved, hints_generated, hints_deactivated,
                   error_message, started_at, completed_at, duration_ms, batch_id, country_code,
                   error_stack, fetch_details
            FROM data_sync_log
            """;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM data_sync_log";

    private static final Set<String> ALLOWED_SORT = Set.of(
            "source", "syncType", "status", "countriesProcessed",
            "statisticsSaved", "hintsGenerated", "startedAt", "durationMs");

    private static final Map<String, String> SORT_MAP = Map.of(
            "source", "source",
            "syncType", "sync_type",
            "status", "status",
            "countriesProcessed", "countries_processed",
            "statisticsSaved", "statistics_saved",
            "hintsGenerated", "hints_generated",
            "startedAt", "started_at",
            "durationMs", "duration_ms");

    private static final String GROUPED_ERRORS_SQL = """
            SELECT error_message,
                   COUNT(*) AS cnt,
                   MIN(started_at) AS first_occurrence,
                   MAX(started_at) AS last_occurrence,
                   array_agg(DISTINCT country_code) FILTER (WHERE country_code IS NOT NULL) AS affected_countries
            FROM data_sync_log
            WHERE error_message IS NOT NULL AND error_message != ''
            GROUP BY error_message
            ORDER BY cnt DESC
            LIMIT :lim
            """;

    private static final RowMapper<DataSyncLogAdminDto> MAPPER = (rs, rowNum) -> mapRow(rs);

    private static DataSyncLogAdminDto mapRow(ResultSet rs) throws SQLException {
        return new DataSyncLogAdminDto(
                rs.getLong("id"),
                rs.getString("source"),
                rs.getString("sync_type"),
                rs.getString("status"),
                rs.getInt("countries_processed"),
                rs.getInt("statistics_saved"),
                rs.getInt("hints_generated"),
                rs.getInt("hints_deactivated"),
                rs.getString("error_message"),
                JdbcUtils.ts(rs, "started_at"),
                JdbcUtils.ts(rs, "completed_at"),
                JdbcUtils.nullLong(rs, "duration_ms"),
                JdbcUtils.nullLong(rs, "batch_id"),
                rs.getString("country_code"),
                rs.getString("error_stack"),
                rs.getString("fetch_details"));
    }

    public Page<DataSyncLogAdminDto> findAll(String source, String status, String syncType,
                                             String countryCode, String startAfter,
                                             String startBefore, String sortBy,
                                             String sortDir, Pageable pageable) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .eq("source", "source", source)
                .eq("status", "status", status)
                .eq("sync_type", "syncType", syncType)
                .eq("country_code", "countryCode", countryCode)
                .gte("started_at", "startAfter", startAfter, "timestamp")
                .lte("started_at", "startBefore", startBefore, "timestamp");

        return JdbcUtils.paginate(jdbc, COUNT_SQL, SELECT_SQL, f.whereClause(), f.params(), sortBy, sortDir, ALLOWED_SORT, SORT_MAP, "started_at", pageable, MAPPER);
    }

    public List<String> findDistinctSources() {
        return jdbc.queryForList("SELECT DISTINCT source FROM data_sync_log ORDER BY source", Map.of(), String.class);
    }

    public List<String> findDistinctStatuses() {
        return jdbc.queryForList("SELECT DISTINCT status FROM data_sync_log ORDER BY status", Map.of(), String.class);
    }

    public List<String> findDistinctTypes() {
        return jdbc.queryForList("SELECT DISTINCT sync_type FROM data_sync_log ORDER BY sync_type", Map.of(), String.class);
    }

    public List<GroupedErrorDto> findGroupedErrors(int limit) {
        return jdbc.query(GROUPED_ERRORS_SQL, Map.of("lim", limit), (rs, rowNum) -> {
            java.sql.Array arr = rs.getArray("affected_countries");
            List<String> countries = arr != null
                    ? List.of((String[]) arr.getArray())
                    : List.of();
            return new GroupedErrorDto(
                    rs.getString("error_message"),
                    rs.getInt("cnt"),
                    JdbcUtils.ts(rs, "first_occurrence"),
                    JdbcUtils.ts(rs, "last_occurrence"),
                    countries);
        });
    }
}
