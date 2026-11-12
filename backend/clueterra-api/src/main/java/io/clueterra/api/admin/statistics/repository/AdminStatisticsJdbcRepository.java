package io.clueterra.api.admin.statistics.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.statistics.dto.CountryStatisticAdminDto;
import io.clueterra.api.admin.statistics.dto.StatCountryOption;
import io.clueterra.api.admin.statistics.dto.UpdateStatisticRequest;
import io.clueterra.api.common.utils.database.JdbcUtils;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminStatisticsJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String CREATE_SUPPRESSION = SqlLoader.load(AdminStatisticsJdbcRepository.class, "createSuppression");
    private static final String RESOLVE_SUPPRESSION = SqlLoader.load(AdminStatisticsJdbcRepository.class, "resolveSuppression");

    private static final String SELECT_SQL = """
            SELECT cs.id, cs.country_id, c.iso_alpha2 AS country_code, c.name_en AS country_name,
                   cs.source, cs.indicator_code, cs.indicator_name, cs.category,
                   cs.value, cs.value_text, cs.unit, cs.year,
                   cs.global_rank, cs.regional_rank, cs.percentile, cs.deviation_score,
                   cs.is_current, cs.manual_override, cs.confidence, cs.weight, cs.fetched_at,
                   cs.image_url, cs.original_image_url, cs.image_source_page_url, cs.image_author_name,
                   cs.image_attribution_text, cs.image_license_label, cs.image_license_url
            FROM cl_media_schema.country_statistic_projection cs
            JOIN countries c ON c.id = cs.country_id
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM cl_media_schema.country_statistic_projection cs
            JOIN countries c ON c.id = cs.country_id
            """;

    private static final Set<String> ALLOWED_SORT = Set.of(
            "countryName", "category", "indicatorName", "value", "source",
            "year", "globalRank", "percentile", "isCurrent", "weight", "fetchedAt");

    private static final Map<String, String> SORT_MAP = Map.ofEntries(
            Map.entry("countryName", "c.name_en"),
            Map.entry("category", "cs.category"),
            Map.entry("indicatorName", "cs.indicator_name"),
            Map.entry("value", "cs.value"),
            Map.entry("source", "cs.source"),
            Map.entry("year", "cs.year"),
            Map.entry("globalRank", "cs.global_rank"),
            Map.entry("percentile", "cs.percentile"),
            Map.entry("isCurrent", "cs.is_current"),
            Map.entry("weight", "cs.weight"),
            Map.entry("fetchedAt", "cs.fetched_at"));

    private static final RowMapper<CountryStatisticAdminDto> MAPPER = (rs, rowNum) -> mapRow(rs);

    private static CountryStatisticAdminDto mapRow(ResultSet rs) throws SQLException {
        return new CountryStatisticAdminDto(
                rs.getLong("id"),
                rs.getLong("country_id"),
                rs.getString("country_code"),
                rs.getString("country_name"),
                rs.getString("source"),
                rs.getString("indicator_code"),
                rs.getString("indicator_name"),
                rs.getString("category"),
                rs.getBigDecimal("value"),
                rs.getString("value_text"),
                rs.getString("unit"),
                JdbcUtils.nullLong(rs, "year") != null ? rs.getInt("year") : null,
                JdbcUtils.nullLong(rs, "global_rank") != null ? rs.getInt("global_rank") : null,
                JdbcUtils.nullLong(rs, "regional_rank") != null ? rs.getInt("regional_rank") : null,
                rs.getBigDecimal("percentile"),
                rs.getBigDecimal("deviation_score"),
                rs.getBoolean("is_current"),
                rs.getBoolean("manual_override"),
                rs.getBigDecimal("confidence"),
                rs.getBigDecimal("weight"),
                JdbcUtils.ts(rs, "fetched_at"),
                rs.getString("image_url"),
                rs.getString("original_image_url"),
                rs.getString("image_source_page_url"),
                rs.getString("image_author_name"),
                rs.getString("image_attribution_text"),
                rs.getString("image_license_label"),
                rs.getString("image_license_url"));
    }

    public Page<CountryStatisticAdminDto> findAll(String search, Long countryId, String category,
                                                  String source, String indicatorCode,
                                                  Boolean isCurrent, Integer year,
                                                  String sortBy, String sortDir,
                                                  Pageable pageable) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .ilike("LOWER(cs.indicator_name) LIKE :search OR LOWER(cs.value_text) LIKE :search OR LOWER(c.name_en) LIKE :search",
                        "search", search)
                .eqLong("cs.country_id", "countryId", countryId)
                .eq("cs.category", "category", category)
                .eq("cs.source", "source", source)
                .eq("cs.indicator_code", "indicatorCode", indicatorCode)
                .bool("cs.is_current", "isCurrent", isCurrent)
                .eqInt("cs.year", "year", year);

        return JdbcUtils.paginate(jdbc, COUNT_SQL, SELECT_SQL, f.whereClause(), f.params(),
                sortBy, sortDir, ALLOWED_SORT, SORT_MAP, "cs.fetched_at", pageable, MAPPER);
    }

    public Optional<CountryStatisticAdminDto> findById(Long id) {
        var results = jdbc.query(SELECT_SQL + " WHERE cs.id = :id",
                new MapSqlParameterSource("id", id), MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<CountryStatisticAdminDto> update(Long id, UpdateStatisticRequest request) {
        StringBuilder sql = new StringBuilder("UPDATE country_statistics SET ");
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        boolean hasUpdates = false;

        if (request.value() != null) {
            sql.append("value = :value");
            params.addValue("value", request.value());
            hasUpdates = true;
        }
        if (request.valueText() != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("value_text = :valueText");
            params.addValue("valueText", request.valueText());
            hasUpdates = true;
        }
        if (request.weight() != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("weight = :weight");
            params.addValue("weight", request.weight());
            hasUpdates = true;
        }
        if (request.confidence() != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("confidence = :confidence");
            params.addValue("confidence", request.confidence());
            hasUpdates = true;
        }
        if (request.isCurrent() != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("is_current = :isCurrent, manual_override = :manualOverride");
            params.addValue("isCurrent", request.isCurrent());
            params.addValue("manualOverride", !request.isCurrent());
            hasUpdates = true;
        }

        if (!hasUpdates) return findById(id);

        sql.append(" WHERE id = :id");
        int rows = jdbc.update(sql.toString(), params);
        return rows > 0 ? findById(id) : Optional.empty();
    }

    public void createSuppression(Long id) {
        jdbc.update(CREATE_SUPPRESSION, new MapSqlParameterSource("id", id));
    }

    public void resolveSuppression(Long id) {
        jdbc.update(RESOLVE_SUPPRESSION, new MapSqlParameterSource("id", id));
    }

    public List<String> findDistinctCategories() {
        return jdbc.queryForList(
                "SELECT DISTINCT category FROM country_statistics ORDER BY category",
                Map.of(), String.class);
    }

    public List<String> findDistinctSources() {
        return jdbc.queryForList(
                "SELECT DISTINCT source FROM country_statistics ORDER BY source",
                Map.of(), String.class);
    }

    public List<String> findDistinctIndicatorsByCategory(String category) {
        return jdbc.queryForList(
                "SELECT DISTINCT indicator_code FROM country_statistics WHERE category = :category ORDER BY indicator_code",
                Map.of("category", category), String.class);
    }

    public List<Integer> findDistinctYears() {
        return jdbc.queryForList(
                "SELECT DISTINCT year FROM country_statistics WHERE year IS NOT NULL ORDER BY year DESC",
                Map.of(), Integer.class);
    }

    public List<StatCountryOption> findCountriesForFilter(String search) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT c.id, c.iso_alpha2, c.name_en
                FROM countries c
                JOIN country_statistics cs ON cs.country_id = c.id
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (search != null && !search.isBlank()) {
            sql.append(" WHERE LOWER(c.name_en) LIKE :search OR LOWER(c.iso_alpha2) LIKE :search");
            params.addValue("search", "%" + search.toLowerCase() + "%");
        }

        sql.append(" ORDER BY c.name_en LIMIT 50");

        return jdbc.query(sql.toString(), params, (rs, rn) -> new StatCountryOption(
                rs.getLong("id"),
                rs.getString("iso_alpha2"),
                rs.getString("name_en")));
    }
}
