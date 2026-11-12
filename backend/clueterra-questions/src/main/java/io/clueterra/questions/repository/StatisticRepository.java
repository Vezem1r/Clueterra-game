package io.clueterra.questions.repository;

import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StatisticRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public List<CountryStatistic> findCurrentByCountryId(Long countryId) {
        return jdbc.query("""
                SELECT * FROM cl_media_schema.country_statistic_projection
                WHERE country_id = :countryId AND is_current = true
                ORDER BY category, weight DESC, indicator_code
                """, Map.of("countryId", countryId), rowMapper());
    }

    public List<CountryStatistic> findCurrentByCountryIdAndSource(Long countryId, DataSource source) {
        return jdbc.query("""
                        SELECT * FROM cl_media_schema.country_statistic_projection
                        WHERE country_id = :countryId AND is_current = true AND source = :source
                        ORDER BY category, weight DESC, indicator_code
                        """,
                Map.of("countryId", countryId, "source", source.name()),
                rowMapper());
    }

    public List<CountryStatistic> findCurrentByCountryIdAndPrefix(Long countryId, String prefix) {
        return jdbc.query("""
                        SELECT * FROM cl_media_schema.country_statistic_projection
                        WHERE country_id = :countryId AND is_current = true
                          AND indicator_code LIKE :prefix
                        ORDER BY indicator_code
                        """,
                Map.of("countryId", countryId, "prefix", prefix + "%"),
                rowMapper());
    }

    public int countCountriesWithIndicator(String indicatorCode) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT country_id) FROM country_statistics
                WHERE indicator_code = :indicatorCode AND is_current = true AND value IS NOT NULL
                """, Map.of("indicatorCode", indicatorCode), Integer.class);
        return count != null ? count : 0;
    }

    public Optional<CountryStatistic> findTopRanked(String indicatorCode) {
        List<CountryStatistic> results = jdbc.query("""
                SELECT * FROM cl_media_schema.country_statistic_projection
                WHERE indicator_code = :indicatorCode AND is_current = true AND global_rank = 1
                LIMIT 1
                """, Map.of("indicatorCode", indicatorCode), rowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<CountryStatistic> findBottomN(String indicatorCode, int n) {
        return jdbc.query("""
                SELECT * FROM cl_media_schema.country_statistic_projection
                WHERE indicator_code = :indicatorCode AND is_current = true AND global_rank IS NOT NULL
                ORDER BY global_rank DESC
                LIMIT :n
                """, Map.of("indicatorCode", indicatorCode, "n", n), rowMapper());
    }

    public List<RegionalRankInfo> findRegionalRankings(String indicatorCode, String region) {
        return jdbc.query("""
                        SELECT cs.country_id, cs.regional_rank, cs.value, c.name_en as country_name
                        FROM country_statistics cs
                        JOIN countries c ON cs.country_id = c.id
                        WHERE cs.indicator_code = :indicatorCode AND c.region = :region AND cs.is_current = true
                            AND cs.regional_rank IS NOT NULL
                        ORDER BY cs.regional_rank
                        """,
                Map.of("indicatorCode", indicatorCode, "region", region),
                (rs, rn) -> new RegionalRankInfo(
                        rs.getLong("country_id"),
                        rs.getString("country_name"),
                        rs.getInt("regional_rank"),
                        rs.getBigDecimal("value")
                )
        );
    }

    public List<CountryStatistic> findBySource(DataSource source) {
        return jdbc.query("""
                SELECT * FROM cl_media_schema.country_statistic_projection
                WHERE source = :source
                ORDER BY country_id, indicator_code
                """, Map.of("source", source.name()), rowMapper());
    }

    public List<CountryStatistic> findCurrentBySource(DataSource source) {
        return jdbc.query("""
                SELECT * FROM cl_media_schema.country_statistic_projection
                WHERE source = :source AND is_current = true
                ORDER BY country_id, indicator_code
                """, Map.of("source", source.name()), rowMapper());
    }

    public List<CountryStatistic> findBySourceAndIndicatorCode(DataSource source, String indicatorCode) {
        return jdbc.query("""
                SELECT * FROM cl_media_schema.country_statistic_projection
                WHERE source = :source AND indicator_code = :indicatorCode
                ORDER BY country_id
                """, Map.of("source", source.name(), "indicatorCode", indicatorCode), rowMapper());
    }

    public List<CountryStatistic> findAll() {
        return jdbc.query("SELECT * FROM cl_media_schema.country_statistic_projection ORDER BY country_id, indicator_code", Map.of(), rowMapper());
    }

    public void batchUpdateEnrichment(List<CountryStatistic> statistics) {
        if (statistics == null || statistics.isEmpty()) return;

        SqlParameterSource[] batch = statistics.stream()
                .map(stat -> new MapSqlParameterSource()
                        .addValue("globalRank", stat.getGlobalRank())
                        .addValue("regionalRank", stat.getRegionalRank())
                        .addValue("percentile", stat.getPercentile())
                        .addValue("deviationScore", stat.getDeviationScore())
                        .addValue("id", stat.getId())
                )
                .toArray(SqlParameterSource[]::new);

        jdbc.batchUpdate("""
                        UPDATE country_statistics
                        SET global_rank = :globalRank,
                            regional_rank = :regionalRank,
                            percentile = :percentile,
                            deviation_score = :deviationScore
                        WHERE id = :id
                        """,
                batch
        );
    }

    public int deleteStaleDynamic(Long countryId, DataSource source, List<String> activeIndicatorCodes) {
        if (activeIndicatorCodes == null || activeIndicatorCodes.isEmpty()) return 0;
        return jdbc.update("""
                        DELETE FROM country_statistics
                        WHERE country_id = :countryId
                          AND source = :source
                          AND is_dynamic = true
                          AND indicator_code NOT IN (:activeCodes)
                        """,
                Map.of("countryId", countryId, "source", source.name(), "activeCodes", activeIndicatorCodes));
    }

    public int deleteAllCurrentByCountryAndSource(Long countryId, DataSource source, boolean dynamicOnly) {
        String sql = """
                DELETE FROM country_statistics
                WHERE country_id = :countryId
                  AND source = :source
                  AND is_current = true
                  AND manual_override = false
                """ + (dynamicOnly ? "\n  AND is_dynamic = true" : "");

        return jdbc.update(sql, Map.of("countryId", countryId, "source", source.name()));
    }

    public int deleteStaleSnapshot(Long countryId,
                                   DataSource source,
                                   List<CountryStatistic> activeStatistics,
                                   boolean dynamicOnly) {
        if (activeStatistics == null || activeStatistics.isEmpty()) {
            return deleteAllCurrentByCountryAndSource(countryId, source, dynamicOnly);
        }

        List<CountryStatistic> active = activeStatistics.stream()
                .filter(stat -> stat != null && stat.getIndicatorCode() != null)
                .toList();
        if (active.isEmpty()) {
            return deleteAllCurrentByCountryAndSource(countryId, source, dynamicOnly);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("countryId", countryId)
                .addValue("source", source.name());

        List<String> valueRows = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            CountryStatistic stat = active.get(i);
            valueRows.add("(CAST(:code" + i + " AS VARCHAR), CAST(:year" + i + " AS INTEGER), CAST(:effectiveDate" + i + " AS DATE))");
            params.addValue("code" + i, stat.getIndicatorCode());
            params.addValue("year" + i, stat.getYear());
            params.addValue("effectiveDate" + i,
                    stat.getEffectiveDate() != null ? Date.valueOf(stat.getEffectiveDate()) : null);
        }

        String sql = """
                WITH active(indicator_code, year, effective_date) AS (
                    VALUES %s
                )
                DELETE FROM country_statistics cs
                WHERE cs.country_id = :countryId
                  AND cs.source = :source
                  AND cs.is_current = true
                  AND cs.manual_override = false
                """.formatted(String.join(", ", valueRows))
                + (dynamicOnly ? "\n  AND cs.is_dynamic = true" : "")
                + """
                  AND NOT EXISTS (
                      SELECT 1
                      FROM active a
                      WHERE a.indicator_code = cs.indicator_code
                        AND a.year IS NOT DISTINCT FROM cs.year
                        AND a.effective_date IS NOT DISTINCT FROM cs.effective_date
                  )
                """;

        return jdbc.update(sql, params);
    }

    public void batchUpsert(List<CountryStatistic> statistics) {
        if (statistics == null || statistics.isEmpty()) return;

        Instant now = Instant.now(clock);
        for (CountryStatistic stat : statistics) {
            if (stat.getId() == null) {
                stat.setId(TSID.Factory.getTsid().toLong());
            }
            if (stat.getFetchedAt() == null) {
                stat.setFetchedAt(now);
            }
        }

        SqlParameterSource[] batch = statistics.stream()
                .map(stat -> new MapSqlParameterSource()
                        .addValue("id", stat.getId())
                        .addValue("countryId", stat.getCountryId())
                        .addValue("source", stat.getSource().name())
                        .addValue("indicatorCode", stat.getIndicatorCode())
                        .addValue("indicatorName", stat.getIndicatorName())
                        .addValue("category", stat.getCategory().name())
                        .addValue("value", stat.getValue())
                        .addValue("valueText", stat.getValueText())
                        .addValue("unit", stat.getUnit())
                        .addValue("year", stat.getYear())
                        .addValue("isCurrent", stat.getIsCurrent())
                        .addValue("confidence", stat.getConfidence())
                        .addValue("weight", stat.getWeight())
                        .addValue("fetchedAt", Timestamp.from(stat.getFetchedAt()))
                        .addValue("effectiveDate", stat.getEffectiveDate() != null ? Date.valueOf(stat.getEffectiveDate()) : null)
                        .addValue("imageUrl", stat.getImageUrl())
                        .addValue("originalImageUrl", stat.getOriginalImageUrl())
                        .addValue("imageSourcePageUrl", stat.getImageSourcePageUrl())
                        .addValue("imageAuthorName", stat.getImageAuthorName())
                        .addValue("imageAttributionText", stat.getImageAttributionText())
                        .addValue("imageLicenseLabel", stat.getImageLicenseLabel())
                        .addValue("imageLicenseUrl", stat.getImageLicenseUrl())
                        .addValue("imageResourceId", stat.getImageResourceId())
                        .addValue("isDynamic", stat.getIsDynamic())
                )
                .toArray(SqlParameterSource[]::new);

        jdbc.batchUpdate("""
                        WITH registered_image AS (
                            SELECT COALESCE(
                                :imageResourceId,
                                cl_media_schema.register_image(
                                    :source, 'hint-images', :imageUrl, :originalImageUrl,
                                    :imageSourcePageUrl, :imageAuthorName,
                                    :imageAttributionText, :imageLicenseLabel,
                                    :imageLicenseUrl,
                                    p_source_key_override => CASE
                                        WHEN :source = 'BUDGET_PROJECTS'
                                            THEN 'budget-projects:' || :imageUrl
                                        ELSE NULL
                                    END)
                            ) AS image_resource_id
                        )
                        INSERT INTO country_statistics (
                            id, country_id, source, indicator_code, indicator_name,
                            category, value, value_text, unit, year,
                            is_current, confidence, weight, fetched_at, effective_date,
                            image_resource_id, is_dynamic
                        )
                        SELECT
                            :id, :countryId, :source, :indicatorCode, :indicatorName,
                            :category, :value, :valueText, :unit, :year,
                            :isCurrent, :confidence, :weight, :fetchedAt, :effectiveDate,
                            image_resource_id, :isDynamic
                        FROM registered_image
                        ON CONFLICT (country_id, source, indicator_code, year, effective_date) DO UPDATE SET
                            indicator_name = EXCLUDED.indicator_name,
                            category = EXCLUDED.category,
                            value = EXCLUDED.value,
                            value_text = EXCLUDED.value_text,
                            unit = EXCLUDED.unit,
                            is_current = CASE
                                WHEN country_statistics.manual_override = true THEN country_statistics.is_current
                                ELSE EXCLUDED.is_current
                            END,
                            manual_override = country_statistics.manual_override,
                            confidence = EXCLUDED.confidence,
                            weight = EXCLUDED.weight,
                            fetched_at = EXCLUDED.fetched_at,
                            image_resource_id = EXCLUDED.image_resource_id,
                            is_dynamic = EXCLUDED.is_dynamic
                        """,
                batch
        );
    }

    private RowMapper<CountryStatistic> rowMapper() {
        return (rs, rn) -> mapRow(rs);
    }

    private CountryStatistic mapRow(ResultSet rs) throws SQLException {
        return CountryStatistic.builder()
                .id(rs.getLong("id"))
                .countryId(rs.getLong("country_id"))
                .source(DataSource.valueOf(rs.getString("source")))
                .indicatorCode(rs.getString("indicator_code"))
                .indicatorName(rs.getString("indicator_name"))
                .category(Category.valueOf(rs.getString("category")))
                .value(rs.getBigDecimal("value"))
                .valueText(rs.getString("value_text"))
                .unit(rs.getString("unit"))
                .year(getInt(rs, "year"))
                .globalRank(getInt(rs, "global_rank"))
                .regionalRank(getInt(rs, "regional_rank"))
                .percentile(rs.getBigDecimal("percentile"))
                .deviationScore(rs.getBigDecimal("deviation_score"))
                .isCurrent(rs.getBoolean("is_current"))
                .confidence(rs.getBigDecimal("confidence"))
                .weight(rs.getBigDecimal("weight"))
                .fetchedAt(getInstant(rs))
                .effectiveDate(getLocalDate(rs))
                .imageUrl(rs.getString("image_url"))
                .originalImageUrl(rs.getString("original_image_url"))
                .imageSourcePageUrl(rs.getString("image_source_page_url"))
                .imageAuthorName(rs.getString("image_author_name"))
                .imageAttributionText(rs.getString("image_attribution_text"))
                .imageLicenseLabel(rs.getString("image_license_label"))
                .imageLicenseUrl(rs.getString("image_license_url"))
                .imageResourceId(rs.getObject("image_resource_id", Long.class))
                .isDynamic(rs.getBoolean("is_dynamic"))
                .build();
    }

    private Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private Instant getInstant(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("fetched_at");
        return ts != null ? ts.toInstant() : null;
    }

    private LocalDate getLocalDate(ResultSet rs) throws SQLException {
        Date d = rs.getDate("effective_date");
        return d != null ? d.toLocalDate() : null;
    }

}
