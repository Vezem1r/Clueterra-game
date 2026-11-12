package io.clueterra.questions.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@Slf4j
@RequiredArgsConstructor
public class IndicatorConfigRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<IndicatorConfigRecord> findAll() {
        return jdbc.query("""
                SELECT indicator_code, display_name, category, unit, weight, profile_level,
                       lower_is_better, exclude_from_ranking, exclude_from_percentile,
                       hint_template, hint_type, base_difficulty,
                       COALESCE(quality_score, 0.50) as quality_score,
                       COALESCE(high_interest, false) as high_interest
                FROM indicator_config
                WHERE is_active = true
                """, Map.of(), rowMapper());
    }

    public Set<String> findExcludedFromRanking() {
        List<String> codes = jdbc.queryForList("""
                SELECT indicator_code FROM indicator_config
                WHERE exclude_from_ranking = true AND is_active = true
                """, Map.of(), String.class);
        return new HashSet<>(codes);
    }

    public Set<String> findExcludedFromPercentile() {
        List<String> codes = jdbc.queryForList("""
                SELECT indicator_code FROM indicator_config
                WHERE exclude_from_percentile = true AND is_active = true
                """, Map.of(), String.class);
        return new HashSet<>(codes);
    }

    public Set<String> findHighProfileIndicators() {
        List<String> codes = jdbc.queryForList("""
                SELECT indicator_code FROM indicator_config
                WHERE profile_level = 'HIGH' AND is_active = true
                """, Map.of(), String.class);
        return new HashSet<>(codes);
    }

    public Set<String> findMediumProfileIndicators() {
        List<String> codes = jdbc.queryForList("""
                SELECT indicator_code FROM indicator_config
                WHERE profile_level = 'MEDIUM' AND is_active = true
                """, Map.of(), String.class);
        return new HashSet<>(codes);
    }

    public Set<String> findLowerIsBetterIndicators() {
        List<String> codes = jdbc.queryForList("""
                SELECT indicator_code FROM indicator_config
                WHERE lower_is_better = true AND is_active = true
                """, Map.of(), String.class);
        return new HashSet<>(codes);
    }

    public Map<String, String> findDisplayNames() {
        Map<String, String> result = new HashMap<>();
        jdbc.query("""
                SELECT indicator_code, display_name FROM indicator_config
                WHERE display_name IS NOT NULL AND is_active = true
                """, Map.of(), (rs, rn) -> {
            result.put(rs.getString("indicator_code"), rs.getString("display_name"));
            return null;
        });
        return result;
    }

    private RowMapper<IndicatorConfigRecord> rowMapper() {
        return (rs, rn) -> mapRow(rs);
    }

    private IndicatorConfigRecord mapRow(ResultSet rs) throws SQLException {
        return new IndicatorConfigRecord(
                rs.getString("indicator_code"),
                rs.getString("display_name"),
                rs.getString("category"),
                rs.getString("unit"),
                rs.getBigDecimal("weight"),
                rs.getString("profile_level"),
                rs.getBoolean("lower_is_better"),
                rs.getBoolean("exclude_from_ranking"),
                rs.getBoolean("exclude_from_percentile"),
                rs.getString("hint_template"),
                rs.getString("hint_type"),
                getIntOrNull(rs),
                rs.getBigDecimal("quality_score"),
                rs.getBoolean("high_interest")
        );
    }

    private Integer getIntOrNull(ResultSet rs) throws SQLException {
        int v = rs.getInt("base_difficulty");
        return rs.wasNull() ? null : v;
    }

}
