package io.clueterra.api.admin.indicators.repository;

import io.clueterra.api.admin.indicators.dto.IndicatorConfigAdminDto;
import io.clueterra.api.common.utils.database.JdbcUtils;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class ComboIndicatorConfigJdbcRepository {

    private static final String BASE_SELECT = SqlLoader.load(ComboIndicatorConfigJdbcRepository.class, "findAllSelect");
    private static final String BASE_COUNT = SqlLoader.load(ComboIndicatorConfigJdbcRepository.class, "findAllCount");

    private static final Set<String> ALLOWED_SORT = Set.of(
            "indicatorCode", "displayName", "category", "familyType",
            "componentCount", "predicateCount", "variantCount",
            "baseDifficulty", "qualityScore", "hintCount", "maxAmbiguityCount"
    );

    private static final Map<String, String> SORT_MAP = Map.ofEntries(
            Map.entry("indicatorCode", "cic.indicator_code"),
            Map.entry("displayName", "cic.display_name"),
            Map.entry("category", "cic.category"),
            Map.entry("familyType", "cic.family_type"),
            Map.entry("componentCount", "component_count"),
            Map.entry("predicateCount", "predicate_count"),
            Map.entry("variantCount", "variant_count"),
            Map.entry("baseDifficulty", "cic.base_difficulty"),
            Map.entry("qualityScore", "cic.quality_score"),
            Map.entry("hintCount", "hint_count"),
            Map.entry("maxAmbiguityCount", "cic.max_ambiguity_count")
    );

    private static final String DEFAULT_SORT = "cic.category";

    private final NamedParameterJdbcTemplate jdbc;

    private static Double bd(ResultSet rs) throws java.sql.SQLException {
        var v = rs.getBigDecimal("quality_score");
        return v != null ? v.doubleValue() : null;
    }

    static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Expected numeric value but got: " + value.getClass().getName());
    }

    private static Integer integer(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        return toInteger(rs.getObject(col));
    }

    private static final RowMapper<IndicatorConfigAdminDto> ROW_MAPPER = (rs, rowNum) -> new IndicatorConfigAdminDto(
            rs.getString("indicator_code"),
            rs.getString("display_name"),
            rs.getString("category"),
            null,
            null,
            null,
            null,
            null,
            null,
            rs.getString("hint_template"),
            rs.getString("hint_type"),
            integer(rs, "base_difficulty"),
            bd(rs),
            rs.getObject("is_active", Boolean.class),
            null,
            null,
            null,
            java.util.Objects.requireNonNullElse(integer(rs, "hint_count"), 0),
            rs.getString("example_hint"),
            "combo",
            rs.getString("family_type"),
            integer(rs, "max_ambiguity_count"),
            integer(rs, "component_count"),
            integer(rs, "predicate_count"),
            integer(rs, "variant_count"),
            rs.getString("components_summary"),
            rs.getString("predicates_summary"),
            rs.getString("variant_templates")
    );

    public Page<IndicatorConfigAdminDto> findAll(String search, String category, String familyType,
                                                 Boolean isActive, String sortBy, String sortDir,
                                                 int page, int size) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .ilike("""
                                LOWER(cic.indicator_code) LIKE :search
                                OR LOWER(cic.display_name) LIKE :search
                                OR LOWER(COALESCE(vm.variant_templates, '')) LIKE :search
                                OR LOWER(COALESCE(cm.components_summary, '')) LIKE :search
                                OR LOWER(COALESCE(pm.predicates_summary, '')) LIKE :search
                                """,
                        "search", search)
                .eq("cic.category", "category", category)
                .eq("cic.family_type", "familyType", familyType)
                .bool("cic.is_active", "isActive", isActive);

        Pageable pageable = PageRequest.of(page, size);
        return JdbcUtils.paginate(jdbc, BASE_COUNT, BASE_SELECT, f.whereClause(), f.params(),
                sortBy, sortDir, ALLOWED_SORT, SORT_MAP, DEFAULT_SORT, pageable, ROW_MAPPER);
    }

    public List<String> findDistinctCategories() {
        return jdbc.queryForList(
                "SELECT DISTINCT category FROM combo_indicator_config ORDER BY category",
                Map.of(), String.class);
    }

    public List<String> findDistinctFamilyTypes() {
        return jdbc.queryForList(
                "SELECT DISTINCT family_type FROM combo_indicator_config ORDER BY family_type",
                Map.of(), String.class);
    }
}
