package io.clueterra.api.reports.repository;

import io.clueterra.api.reports.dto.PreInsertChecks;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HintReportJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String PRE_INSERT_CHECKS = """
            SELECT
                EXISTS(SELECT 1 FROM hint_reports WHERE hint_id = :hintId AND user_id = :userId) AS already_reported,
                (SELECT COUNT(*) FROM hint_reports WHERE user_id = :userId AND status = 'PENDING') AS pending_count,
                (SELECT reports_muted FROM users WHERE id = :userId) AS is_muted,
                (
                    EXISTS(SELECT 1 FROM hints WHERE id = :hintId)
                    OR EXISTS(SELECT 1 FROM hints_history WHERE id = :hintId)
                ) AS hint_exists
            """;

    private static final String INSERT_REPORT = """
            INSERT INTO hint_reports (id, hint_id, user_id, report_type, description, status, created_at)
            VALUES (:id, :hintId, :userId, :reportType, :description, 'PENDING', NOW())
            """;

    public PreInsertChecks preInsertChecks(Long hintId, Long userId) {
        var params = new MapSqlParameterSource()
                .addValue("hintId", hintId)
                .addValue("userId", userId);
        return jdbc.queryForObject(PRE_INSERT_CHECKS, params,
                (rs, rowNum) -> new PreInsertChecks(
                        rs.getBoolean("already_reported"),
                        rs.getLong("pending_count"),
                        rs.getBoolean("is_muted"),
                        rs.getBoolean("hint_exists")));
    }

    public long insert(Long hintId, Long userId, String reportType, String description) {
        long id = TSID.Factory.getTsid().toLong();
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("hintId", hintId)
                .addValue("userId", userId)
                .addValue("reportType", reportType)
                .addValue("description", description);
        jdbc.update(INSERT_REPORT, params);
        return id;
    }
}
