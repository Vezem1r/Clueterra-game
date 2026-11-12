package io.clueterra.api.admin.reports.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.reports.dto.AdminReportDto;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.database.SortDirection;
import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.common.enums.ReportStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminReportJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String BASE_SELECT = SqlLoader.load(AdminReportJdbcRepository.class, "findAll");

    private static final String BASE_COUNT = """
            SELECT COUNT(*) FROM hint_reports hr
            JOIN users u ON u.id = hr.user_id
            """;

    private static final String FIND_BY_ID = " WHERE hr.id = :id";

    private static final String RESOLVE = """
            UPDATE hint_reports
            SET status = :status, admin_notes = :adminNotes,
                resolved_by = :resolvedById, resolved_at = NOW()
            WHERE id = :id
            """;

    private static final String RESOLVE_ALL_PENDING_BY_HINT = """
            UPDATE hint_reports
            SET status = :status, admin_notes = :adminNotes,
                resolved_by = :resolvedById, resolved_at = NOW()
            WHERE hint_id = :hintId AND status = :pendingStatus
            """;

    private static final String COUNT_PENDING_BY_HINT = """
            SELECT COUNT(*) FROM hint_reports
            WHERE hint_id = :hintId AND status = :pendingStatus
            """;

    private static final Set<String> ALLOWED_SORT = Set.of("createdAt", "status", "reportType");

    private static final Map<String, String> SORT_MAP = Map.of(
            "createdAt", "hr.created_at",
            "status", "hr.status",
            "reportType", "hr.report_type");

    private static final RowMapper<AdminReportDto> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    private static AdminReportDto mapRow(ResultSet rs) throws SQLException {
        return new AdminReportDto(
                rs.getLong("id"),
                rs.getLong("hint_id"),
                rs.getLong("user_id"),
                rs.getString("user_email"),
                rs.getString("hint_text"),
                rs.getString("country_name"),
                rs.getString("report_type"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("admin_notes"),
                rs.getObject("resolved_by_id") != null ? rs.getLong("resolved_by_id") : null,
                rs.getString("resolved_by_email"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant().toString() : null,
                rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant().toString() : null);
    }

    public Page<AdminReportDto> findAll(String status, String excludeStatus, Long hintId,
                                        String sortBy, String sortDir, Pageable pageable) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .eq("hr.status", "status", status != null ? status.toUpperCase() : null)
                .neq("hr.status", "excludeStatus", excludeStatus != null ? excludeStatus.toUpperCase() : null)
                .eqLong("hr.hint_id", "hintId", hintId);

        Long total = jdbc.queryForObject(BASE_COUNT + f.whereClause(), f.params(), Long.class);
        if (total == null || total == 0) return Page.empty(pageable);

        String col = ALLOWED_SORT.contains(sortBy) ? SORT_MAP.getOrDefault(sortBy, "hr.created_at") : "hr.created_at";
        String dir = SortDirection.resolve(sortDir);

        String sql = BASE_SELECT + f.whereClause()
                + " ORDER BY " + col + " " + dir
                + " LIMIT :limit OFFSET :offset";
        f.params().addValue("limit", pageable.getPageSize());
        f.params().addValue("offset", pageable.getOffset());

        return new PageImpl<>(jdbc.query(sql, f.params(), ROW_MAPPER), pageable, total);
    }

    public Optional<AdminReportDto> findById(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        List<AdminReportDto> list = jdbc.query(BASE_SELECT + FIND_BY_ID, params, ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public int resolve(Long id, String status, String adminNotes, Long resolvedById) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status)
                .addValue("adminNotes", adminNotes)
                .addValue("resolvedById", resolvedById);
        return jdbc.update(RESOLVE, params);
    }

    public void resolveAllPendingByHintId(Long hintId, String status, String adminNotes, Long resolvedById) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hintId", hintId)
                .addValue("status", status)
                .addValue("adminNotes", adminNotes)
                .addValue("resolvedById", resolvedById)
                .addValue("pendingStatus", ReportStatus.PENDING.name());
        jdbc.update(RESOLVE_ALL_PENDING_BY_HINT, params);
    }

    public long countPendingByHintId(Long hintId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hintId", hintId)
                .addValue("pendingStatus", ReportStatus.PENDING.name());
        Long count = jdbc.queryForObject(COUNT_PENDING_BY_HINT, params, Long.class);
        return count != null ? count : 0;
    }
}
