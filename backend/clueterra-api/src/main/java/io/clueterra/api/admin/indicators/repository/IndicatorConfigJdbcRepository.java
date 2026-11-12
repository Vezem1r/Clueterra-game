package io.clueterra.api.admin.indicators.repository;

import io.clueterra.api.admin.indicators.dto.IndicatorConfigAdminDto;
import io.clueterra.api.common.utils.database.JdbcUtils;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class IndicatorConfigJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String BASE_SELECT = """
            SELECT ic.*,
              COALESCE(hs.hint_count, 0) AS hint_count,
              hs.example_hint
            FROM indicator_config ic
            LEFT JOIN (
              SELECT source_indicator,
                     COUNT(*) AS hint_count,
                     MIN(hint_text) AS example_hint
              FROM hints
              WHERE is_active = true
              GROUP BY source_indicator
            ) hs ON hs.source_indicator = ic.indicator_code
            """;

    private static final String BASE_COUNT = """
            SELECT COUNT(*) FROM indicator_config ic
            """;

    private static final Set<String> ALLOWED_SORT = Set.of(
            "indicatorCode", "displayName", "category", "weight",
            "baseDifficulty", "qualityScore", "hintCount"
    );

    private static final Map<String, String> SORT_MAP = Map.of(
            "indicatorCode", "ic.indicator_code",
            "displayName", "ic.display_name",
            "category", "ic.category",
            "weight", "ic.weight",
            "baseDifficulty", "ic.base_difficulty",
            "qualityScore", "ic.quality_score",
            "hintCount", "hint_count"
    );

    private static final String DEFAULT_SORT = "ic.category";

    private static Double bd(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        var v = rs.getBigDecimal(col);
        return v != null ? v.doubleValue() : null;
    }

    private static final RowMapper<IndicatorConfigAdminDto> ROW_MAPPER = (rs, rowNum) -> new IndicatorConfigAdminDto(
            rs.getString("indicator_code"),
            rs.getString("display_name"),
            rs.getString("category"),
            rs.getString("unit"),
            bd(rs, "weight"),
            rs.getString("profile_level"),
            rs.getObject("lower_is_better", Boolean.class),
            rs.getObject("exclude_from_ranking", Boolean.class),
            rs.getObject("exclude_from_percentile", Boolean.class),
            rs.getString("hint_template"),
            rs.getString("hint_type"),
            rs.getObject("base_difficulty", Integer.class),
            bd(rs, "quality_score"),
            rs.getObject("is_active", Boolean.class),
            rs.getObject("high_interest", Boolean.class),
            rs.getObject("is_trending", Boolean.class),
            rs.getString("trending_frequency"),
            rs.getInt("hint_count"),
            rs.getString("example_hint"),
            "regular",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    public Page<IndicatorConfigAdminDto> findAll(String search, String category, String profileLevel,
                                                 Boolean isActive, String sortBy, String sortDir,
                                                 int page, int size) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .ilike("LOWER(ic.indicator_code) LIKE :search OR LOWER(ic.display_name) LIKE :search OR LOWER(ic.hint_template) LIKE :search",
                        "search", search)
                .eq("ic.category", "category", category)
                .eq("ic.profile_level", "profileLevel", profileLevel)
                .bool("ic.is_active", "isActive", isActive);

        Pageable pageable = PageRequest.of(page, size);
        return JdbcUtils.paginate(jdbc, BASE_COUNT, BASE_SELECT, f.whereClause(), f.params(),
                sortBy, sortDir, ALLOWED_SORT, SORT_MAP, DEFAULT_SORT, pageable, ROW_MAPPER);
    }

    public List<String> findDistinctCategories() {
        return jdbc.queryForList(
                "SELECT DISTINCT category FROM indicator_config WHERE category IS NOT NULL ORDER BY category",
                Map.of(), String.class);
    }

    public List<String> findDistinctProfileLevels() {
        return jdbc.queryForList(
                "SELECT DISTINCT profile_level FROM indicator_config WHERE profile_level IS NOT NULL ORDER BY profile_level",
                Map.of(), String.class);
    }
}
