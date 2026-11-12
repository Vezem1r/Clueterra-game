package io.clueterra.api.admin.dashboard.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.dashboard.dto.DashboardSummaryDto;
import io.clueterra.api.admin.dashboard.dto.DashboardGameSummaryDto;
import io.clueterra.api.admin.dashboard.dto.SessionChartPointDto;
import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DashboardJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String DASHBOARD_SUMMARY =
            SqlLoader.load(DashboardJdbcRepository.class, "getDashboardSummary");
    private static final String SESSIONS_OVER_TIME =
            SqlLoader.load(DashboardJdbcRepository.class, "getSessionsOverTime");
    private static final String BUDGET_SESSIONS_OVER_TIME =
            SqlLoader.load(DashboardJdbcRepository.class, "getBudgetSessionsOverTime");

    public DashboardSummaryDto getDashboardSummary() {
        var params = new MapSqlParameterSource()
                .addValue("completedStatus", SessionStatus.COMPLETED.name())
                .addValue("abandonedStatus", SessionStatus.ABANDONED.name());
        return jdbc.queryForObject(DASHBOARD_SUMMARY, params, (rs, rowNum) -> new DashboardSummaryDto(
                new DashboardGameSummaryDto(
                        rs.getLong("geography_total_sessions"),
                        rs.getLong("geography_completed_sessions"),
                        rs.getLong("geography_abandoned_sessions"),
                        rs.getLong("geography_unique_users"),
                        rs.getLong("geography_active_today")),
                new DashboardGameSummaryDto(
                        rs.getLong("budget_total_sessions"),
                        rs.getLong("budget_completed_sessions"),
                        rs.getLong("budget_abandoned_sessions"),
                        rs.getLong("budget_unique_users"),
                        rs.getLong("budget_active_today")),
                rs.getLong("total_registered_users")));
    }

    public List<SessionChartPointDto> getSessionsOverTime(int days) {
        var params = new MapSqlParameterSource()
                .addValue("days", days)
                .addValue("completedStatus", SessionStatus.COMPLETED.name());
        return mapSessionChart(SESSIONS_OVER_TIME, params);
    }

    public List<SessionChartPointDto> getBudgetSessionsOverTime(int days) {
        var params = new MapSqlParameterSource()
                .addValue("days", days)
                .addValue("completedStatus", SessionStatus.COMPLETED.name());
        return mapSessionChart(BUDGET_SESSIONS_OVER_TIME, params);
    }

    private List<SessionChartPointDto> mapSessionChart(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (rs, rowNum) -> new SessionChartPointDto(
                rs.getDate("session_date").toLocalDate(),
                rs.getInt("total_sessions"),
                rs.getInt("completed_sessions"),
                rs.getInt("unique_users")));
    }
}
