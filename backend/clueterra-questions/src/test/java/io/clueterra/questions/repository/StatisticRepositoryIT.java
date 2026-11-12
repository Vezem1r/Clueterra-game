package io.clueterra.questions.repository;

import io.clueterra.questions.TestcontainersConfiguration;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.support.ClockTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.schemas=cl_schema,cl_ai_schema,cl_sch_schema,cl_staging",
        "spring.flyway.default-schema=cl_schema",
        "spring.flyway.create-schemas=true",
        "spring.datasource.hikari.connection-init-sql=SET search_path TO cl_schema, cl_ai_schema, cl_sch_schema, cl_staging"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ClockTestConfiguration.class, StatisticRepository.class})
class StatisticRepositoryIT {

    private static final long COUNTRY_ID = 2001L;

    @Autowired
    private StatisticRepository statisticRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUpCountry() {
        jdbc.update("""
                INSERT INTO countries (id, iso_alpha2, iso_alpha3, name_en, difficulty_modifier, is_active, created_at, updated_at)
                VALUES (:id, :iso2, :iso3, :name, :difficultyModifier, true, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("id", COUNTRY_ID)
                .addValue("iso2", "XY")
                .addValue("iso3", "XYY")
                .addValue("name", "Statistic Repository Test Country")
                .addValue("difficultyModifier", BigDecimal.ONE)
                .addValue("createdAt", Timestamp.from(ClockTestConfiguration.FIXED_INSTANT))
                .addValue("updatedAt", Timestamp.from(ClockTestConfiguration.FIXED_INSTANT)));
    }

    @Test
    void batchUpsert_updatesSingleDynamicSnapshotWhenEffectiveDateIsNull() {
        CountryStatistic initial = dynamicStat("BRAND_TOP_APP", new BigDecimal("1.00"));
        CountryStatistic updated = dynamicStat("BRAND_TOP_APP", new BigDecimal("2.00"));

        statisticRepository.batchUpsert(List.of(initial));
        statisticRepository.batchUpsert(List.of(updated));

        List<CountryStatistic> saved = statisticRepository.findCurrentByCountryIdAndSource(COUNTRY_ID, DataSource.GLOBAL_BRANDS);
        assertThat(saved).singleElement().satisfies(stat -> {
            assertThat(stat.getIndicatorCode()).isEqualTo("BRAND_TOP_APP");
            assertThat(stat.getValue()).isEqualByComparingTo("2.0000");
            assertThat(stat.getEffectiveDate()).isNull();
        });
        assertThat(countStatistics()).isEqualTo(1);
    }

    @Test
    void batchUpsert_preservesSourceBackedTextLongerThanLegacyLimit() {
        String holidayNames = String.join(", ", List.of(
                "Commemoration Day of Defenders of the Barricades in 1991",
                "Day of the International Recognition of the Republic of Latvia",
                "World Non-Governmental Organization Day",
                "National Partisan Armed Resistance Remembrance Day",
                "International Women's Day",
                "National Resistance Movement Remembrance Day",
                "Commemoration Day of Victims of Communist Terror",
                "Latgale Congress Day",
                "Day of the Convocation of the Constitutional Assembly of the Republic of Latvia",
                "Day of the Restoration of Independence of the Republic of Latvia",
                "Day of the Defeat of Nazism and Commemoration Day of Victims of World War II",
                "Europe Day"
        ));
        CountryStatistic statistic = CountryStatistic.builder()
                .countryId(COUNTRY_ID)
                .source(DataSource.NAGER_DATE)
                .indicatorCode("HOLIDAY_UNIQUE_EN")
                .indicatorName("holiday english names")
                .category(Category.CULTURE)
                .valueText(holidayNames)
                .year(2026)
                .isCurrent(true)
                .isDynamic(false)
                .confidence(new BigDecimal("0.90"))
                .weight(new BigDecimal("0.80"))
                .build();

        assertThat(holidayNames.length()).isGreaterThan(500);
        statisticRepository.batchUpsert(List.of(statistic));

        assertThat(statisticRepository.findCurrentByCountryIdAndSource(COUNTRY_ID, DataSource.NAGER_DATE))
                .singleElement()
                .extracting(CountryStatistic::getValueText)
                .isEqualTo(holidayNames);
    }

    @Test
    void deleteStaleDynamic_removesIndicatorsMissingFromLatestSnapshot() {
        statisticRepository.batchUpsert(List.of(
                dynamicStat("BRAND_KEEP", new BigDecimal("1.00")),
                dynamicStat("BRAND_DROP", new BigDecimal("2.00"))
        ));

        int deleted = statisticRepository.deleteStaleDynamic(COUNTRY_ID, DataSource.GLOBAL_BRANDS, List.of("BRAND_KEEP"));

        assertThat(deleted).isEqualTo(1);
        assertThat(statisticRepository.findCurrentByCountryIdAndSource(COUNTRY_ID, DataSource.GLOBAL_BRANDS))
                .extracting(CountryStatistic::getIndicatorCode)
                .containsExactly("BRAND_KEEP");
    }

    @Test
    void deleteStaleSnapshot_removesMissingStaticRowsButPreservesManualOverrides() {
        CountryStatistic keep = staticStat("WB_KEEP", new BigDecimal("1.00"), 2026, null);
        CountryStatistic drop = staticStat("WB_DROP", new BigDecimal("2.00"), 2026, null);
        CountryStatistic override = staticStat("WB_OVERRIDE", new BigDecimal("3.00"), 2026, null);

        statisticRepository.batchUpsert(List.of(keep, drop, override));
        jdbc.update("""
                UPDATE country_statistics
                SET manual_override = true
                WHERE country_id = :countryId
                  AND source = :source
                  AND indicator_code = :indicatorCode
                """, new MapSqlParameterSource()
                .addValue("countryId", COUNTRY_ID)
                .addValue("source", DataSource.WORLD_BANK.name())
                .addValue("indicatorCode", "WB_OVERRIDE"));

        int deleted = statisticRepository.deleteStaleSnapshot(
                COUNTRY_ID,
                DataSource.WORLD_BANK,
                List.of(keep),
                false
        );

        assertThat(deleted).isEqualTo(1);
        assertThat(statisticRepository.findCurrentByCountryIdAndSource(COUNTRY_ID, DataSource.WORLD_BANK))
                .extracting(CountryStatistic::getIndicatorCode)
                .containsExactlyInAnyOrder("WB_KEEP", "WB_OVERRIDE");
    }

    @Test
    void deleteAllCurrentByCountryAndSource_respectsDynamicOnlyFlag() {
        statisticRepository.batchUpsert(List.of(
                dynamicStat("BRAND_DYNAMIC", new BigDecimal("1.00")),
                staticStat("WB_STATIC", new BigDecimal("2.00"), 2026, null)
        ));

        int deleted = statisticRepository.deleteAllCurrentByCountryAndSource(COUNTRY_ID, DataSource.GLOBAL_BRANDS, true);

        assertThat(deleted).isEqualTo(1);
        assertThat(statisticRepository.findCurrentByCountryId(COUNTRY_ID))
                .extracting(CountryStatistic::getIndicatorCode)
                .contains("WB_STATIC")
                .doesNotContain("BRAND_DYNAMIC");
    }

    @Test
    void deleteStaleSnapshot_matchesByYearAndEffectiveDate() {
        CountryStatistic keep2025 = staticStat("WB_SERIES", new BigDecimal("1.00"), 2025, LocalDate.of(2025, 1, 1));
        CountryStatistic drop2026 = staticStat("WB_SERIES", new BigDecimal("2.00"), 2026, LocalDate.of(2026, 1, 1));

        statisticRepository.batchUpsert(List.of(keep2025, drop2026));

        int deleted = statisticRepository.deleteStaleSnapshot(
                COUNTRY_ID,
                DataSource.WORLD_BANK,
                List.of(keep2025),
                false
        );

        assertThat(deleted).isEqualTo(1);
        assertThat(statisticRepository.findCurrentByCountryIdAndSource(COUNTRY_ID, DataSource.WORLD_BANK))
                .extracting(CountryStatistic::getYear, CountryStatistic::getEffectiveDate)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(2025, LocalDate.of(2025, 1, 1)));
    }

    @Test
    void countCountriesWithIndicator_countsDistinctCurrentCountriesWithNumericValueOnly() {
        long secondCountryId = 2002L;
        jdbc.update("""
                INSERT INTO countries (id, iso_alpha2, iso_alpha3, name_en, difficulty_modifier, is_active, created_at, updated_at)
                VALUES (:id, :iso2, :iso3, :name, :difficultyModifier, true, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("id", secondCountryId)
                .addValue("iso2", "XQ")
                .addValue("iso3", "XQQ")
                .addValue("name", "Statistic Repository Second Test Country")
                .addValue("difficultyModifier", BigDecimal.ONE)
                .addValue("createdAt", Timestamp.from(ClockTestConfiguration.FIXED_INSTANT))
                .addValue("updatedAt", Timestamp.from(ClockTestConfiguration.FIXED_INSTANT)));

        statisticRepository.batchUpsert(List.of(
                staticStat("WB_COUNT", new BigDecimal("1.00"), 2026, null),
                CountryStatistic.builder()
                        .countryId(secondCountryId)
                        .source(DataSource.WORLD_BANK)
                        .indicatorCode("WB_COUNT")
                        .indicatorName("WB_COUNT")
                        .category(Category.ECONOMY)
                        .value(new BigDecimal("2.00"))
                        .year(2026)
                        .isCurrent(true)
                        .isDynamic(false)
                        .confidence(new BigDecimal("0.90"))
                        .weight(new BigDecimal("0.80"))
                        .build(),
                CountryStatistic.builder()
                        .countryId(secondCountryId)
                        .source(DataSource.WORLD_BANK)
                        .indicatorCode("WB_TEXT_ONLY")
                        .indicatorName("WB_TEXT_ONLY")
                        .category(Category.ECONOMY)
                        .valueText("n/a")
                        .year(2026)
                        .isCurrent(true)
                        .isDynamic(false)
                        .confidence(new BigDecimal("0.90"))
                        .weight(new BigDecimal("0.80"))
                        .build()
        ));

        assertThat(statisticRepository.countCountriesWithIndicator("WB_COUNT")).isEqualTo(2);
        assertThat(statisticRepository.countCountriesWithIndicator("WB_TEXT_ONLY")).isZero();
    }

    @Test
    void findCurrentByCountryIdAndPrefix_filtersByPrefix() {
        statisticRepository.batchUpsert(List.of(
                dynamicStat("BRAND_KEEP", new BigDecimal("1.00")),
                dynamicStat("CITYBIKES_KEEP", new BigDecimal("2.00"))
        ));

        assertThat(statisticRepository.findCurrentByCountryIdAndPrefix(COUNTRY_ID, "BRAND"))
                .extracting(CountryStatistic::getIndicatorCode)
                .containsExactly("BRAND_KEEP");
    }

    private CountryStatistic dynamicStat(String indicatorCode, BigDecimal value) {
        return CountryStatistic.builder()
                .countryId(COUNTRY_ID)
                .source(DataSource.GLOBAL_BRANDS)
                .indicatorCode(indicatorCode)
                .indicatorName(indicatorCode)
                .category(Category.CULTURE)
                .value(value)
                .year(2026)
                .isCurrent(true)
                .isDynamic(true)
                .confidence(new BigDecimal("0.80"))
                .weight(new BigDecimal("0.70"))
                .build();
    }

    private CountryStatistic staticStat(String indicatorCode, BigDecimal value, Integer year, LocalDate effectiveDate) {
        return CountryStatistic.builder()
                .countryId(COUNTRY_ID)
                .source(DataSource.WORLD_BANK)
                .indicatorCode(indicatorCode)
                .indicatorName(indicatorCode)
                .category(Category.ECONOMY)
                .value(value)
                .year(year)
                .effectiveDate(effectiveDate)
                .isCurrent(true)
                .isDynamic(false)
                .confidence(new BigDecimal("0.90"))
                .weight(new BigDecimal("0.80"))
                .build();
    }

    private int countStatistics() {
        Integer count = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM country_statistics", Integer.class);
        return count != null ? count : 0;
    }
}
