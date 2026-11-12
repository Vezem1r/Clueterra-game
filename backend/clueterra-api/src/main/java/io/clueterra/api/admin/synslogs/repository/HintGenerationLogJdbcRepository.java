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
import io.clueterra.api.admin.synslogs.dto.HintGenerationLogAdminDto;
import io.clueterra.api.common.utils.database.JdbcUtils;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HintGenerationLogJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_SQL = """
            SELECT h.id, h.country_id, h.country_code, h.trigger_type,
                   h.hints_attempted, h.hints_created, h.hints_duplicates, h.hints_failed,
                   h.avg_difficulty, h.categories_covered, h.hint_types_covered,
                   h.started_at, h.completed_at, h.duration_ms, h.error_message, h.batch_id,
                   h.avg_quality_score, h.details, h.lens_breakdown
            FROM hint_generation_log h
            """;

    private static final String GROUPED_ERRORS_SQL = """
            SELECT error_message,
                   COUNT(*) AS cnt,
                   MIN(started_at) AS first_occurrence,
                   MAX(started_at) AS last_occurrence,
                   array_agg(DISTINCT country_code) FILTER (WHERE country_code IS NOT NULL) AS affected_countries
            FROM hint_generation_log
            WHERE error_message IS NOT NULL AND error_message != ''
            GROUP BY error_message
            ORDER BY cnt DESC
            LIMIT :lim
            """;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM hint_generation_log h";

    private static final Set<String> ALLOWED_SORT = Set.of(
            "countryCode", "triggerType", "hintsCreated", "hintsDuplicates",
            "hintsFailed", "avgDifficulty", "startedAt", "durationMs");

    private static final Map<String, String> SORT_MAP = Map.of(
            "countryCode", "h.country_code",
            "triggerType", "h.trigger_type",
            "hintsCreated", "h.hints_created",
            "hintsDuplicates", "h.hints_duplicates",
            "hintsFailed", "h.hints_failed",
            "avgDifficulty", "h.avg_difficulty",
            "startedAt", "h.started_at",
            "durationMs", "h.duration_ms");

    private static final RowMapper<HintGenerationLogAdminDto> MAPPER = (rs, rowNum) -> mapRow(rs);

    private static HintGenerationLogAdminDto mapRow(ResultSet rs) throws SQLException {
        return new HintGenerationLogAdminDto(
                rs.getLong("id"),
                JdbcUtils.nullLong(rs, "country_id"),
                rs.getString("country_code"),
                rs.getString("trigger_type"),
                rs.getInt("hints_attempted"),
                rs.getInt("hints_created"),
                rs.getInt("hints_duplicates"),
                rs.getInt("hints_failed"),
                rs.getBigDecimal("avg_difficulty"),
                rs.getInt("categories_covered"),
                rs.getInt("hint_types_covered"),
                JdbcUtils.ts(rs, "started_at"),
                JdbcUtils.ts(rs, "completed_at"),
                JdbcUtils.nullLong(rs, "duration_ms"),
                rs.getString("error_message"),
                JdbcUtils.nullLong(rs, "batch_id"),
                rs.getBigDecimal("avg_quality_score"),
                JdbcUtils.jsonbMap(rs, "details"),
                JdbcUtils.jsonbMap(rs, "lens_breakdown"));
    }

    public Page<HintGenerationLogAdminDto> findAll(String countryCode, String triggerType,
                                                   String startAfter, String startBefore,
                                                   String sortBy, String sortDir,
                                                   Pageable pageable) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .eq("h.country_code", "countryCode", countryCode)
                .eq("h.trigger_type", "triggerType", triggerType)
                .gte("h.started_at", "startAfter", startAfter, "timestamp")
                .lte("h.started_at", "startBefore", startBefore, "timestamp");

        return JdbcUtils.paginate(jdbc, COUNT_SQL, SELECT_SQL, f.whereClause(), f.params(), sortBy, sortDir, ALLOWED_SORT, SORT_MAP, "h.started_at", pageable, MAPPER);
    }

    public List<String> findDistinctTriggerTypes() {
        return jdbc.queryForList("SELECT DISTINCT trigger_type FROM hint_generation_log ORDER BY trigger_type", Map.of(), String.class);
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
