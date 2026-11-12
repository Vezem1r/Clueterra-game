package io.clueterra.api.admin.analytics.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.analytics.dto.AnalyticsSummaryDto;
import io.clueterra.api.admin.analytics.dto.DailySessionDto;
import io.clueterra.api.admin.analytics.dto.SessionsByLevelDto;
import io.clueterra.api.admin.analytics.dto.TopCountryDto;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AnalyticsJdbcRepository {

    private static final String SQL_SUMMARY = SqlLoader.load(AnalyticsJdbcRepository.class, "getSummary");
    private static final String SQL_TOP_COUNTRIES = SqlLoader.load(AnalyticsJdbcRepository.class, "getTopCountries");
    private static final String SQL_HARDEST_COUNTRIES = SqlLoader.load(AnalyticsJdbcRepository.class, "getHardestCountries");

    private static final String SQL_BY_LEVEL = """
            SELECT challenge_level AS level,
                   SUM(total_sessions) AS total,
                   SUM(completed_sessions) AS completed
            FROM game_statistics
            WHERE date BETWEEN :startDate AND :endDate
            GROUP BY challenge_level
            ORDER BY total DESC
            """;

    private static final String SQL_DAILY = """
            SELECT date AS session_date,
                   SUM(total_sessions)::INT AS total_sessions,
                   SUM(completed_sessions)::INT AS completed_sessions,
                   SUM(unique_users)::INT AS unique_users,
                   SUM(total_guesses)::INT AS total_guesses,
                   SUM(correct_guesses)::INT AS correct_guesses
            FROM game_statistics
            WHERE date BETWEEN :startDate AND :endDate
            GROUP BY date
            ORDER BY date
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsSummaryDto getSummary(LocalDate startDate, LocalDate endDate) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        return jdbc.queryForObject(SQL_SUMMARY, params, (rs, rowNum) -> new AnalyticsSummaryDto(
                rs.getLong("total_sessions"),
                rs.getLong("completed_sessions"),
                rs.getLong("abandoned_sessions"),
                rs.getLong("unique_users"),
                rs.getLong("total_guesses"),
                rs.getLong("correct_guesses"),
                rs.getDouble("avg_attempts_per_country"),
                rs.getDouble("avg_time_per_session_ms")));
    }

    public List<SessionsByLevelDto> getSessionsByLevel(LocalDate startDate, LocalDate endDate) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        return jdbc.query(SQL_BY_LEVEL, params, (rs, rowNum) -> new SessionsByLevelDto(
                rs.getString("level"),
                rs.getLong("total"),
                rs.getLong("completed")));
    }

    public List<TopCountryDto> getTopCountries(LocalDate startDate, LocalDate endDate, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("limit", limit);

        return jdbc.query(SQL_TOP_COUNTRIES, params, (rs, rowNum) -> new TopCountryDto(
                rs.getLong("country_id"),
                rs.getString("country_code"),
                rs.getString("country_name"),
                rs.getLong("times_featured"),
                rs.getLong("times_guessed_correct"),
                rs.getDouble("avg_attempts")));
    }

    public List<TopCountryDto> getHardestCountries(LocalDate startDate, LocalDate endDate, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("limit", limit);

        return jdbc.query(SQL_HARDEST_COUNTRIES, params, (rs, rowNum) -> new TopCountryDto(
                rs.getLong("country_id"),
                rs.getString("country_code"),
                rs.getString("country_name"),
                rs.getLong("times_featured"),
                rs.getLong("times_guessed_correct"),
                rs.getDouble("avg_attempts")));
    }

    public List<DailySessionDto> getDailySessions(LocalDate startDate, LocalDate endDate) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        return jdbc.query(SQL_DAILY, params, (rs, rowNum) -> new DailySessionDto(
                rs.getDate("session_date").toLocalDate(),
                rs.getInt("total_sessions"),
                rs.getInt("completed_sessions"),
                rs.getInt("unique_users"),
                rs.getInt("total_guesses"),
                rs.getInt("correct_guesses")));
    }

    public void aggregateDate(LocalDate date) {
        jdbc.queryForList(
                "SELECT aggregate_daily_analytics(:date)",
                new MapSqlParameterSource("date", date));
    }
}
