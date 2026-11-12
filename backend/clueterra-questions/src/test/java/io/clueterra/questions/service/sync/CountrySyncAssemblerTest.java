package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CountrySyncAssemblerTest {

    private static final long COUNTRY_ID = 44L;
    private static final Instant FETCHED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private final CountrySyncAssembler assembler = new CountrySyncAssembler();

    @Test
    void mergeCountryData_prefersFieldsFromHigherPrioritySources() {
        RawCountryData manual = rawCountry(
                DataSource.MANUAL,
                "JP",
                "JPN",
                "Japan Override",
                "Kyoto",
                List.of()
        );
        RawCountryData worldBank = rawCountry(
                DataSource.WORLD_BANK,
                "JP",
                "JPN",
                " Japan ",
                " Tokyo ",
                List.of()
        );

        Country merged = assembler.mergeCountryData(List.of(manual, worldBank)).orElseThrow();

        assertThat(merged.getNameEn()).isEqualTo("Japan");
        assertThat(merged.getCapital()).isEqualTo("Tokyo");
        assertThat(merged.getIsoAlpha2()).isEqualTo("JP");
        assertThat(merged.getIsoAlpha3()).isEqualTo("JPN");
    }

    @Test
    void mergeCountryData_ignoresInvalidRowsAndReturnsEmptyWhenNothingValidRemains() {
        RawCountryData invalid = RawCountryData.builder()
                .isoAlpha2("JP")
                .source(DataSource.MANUAL)
                .build();

        assertThat(assembler.mergeCountryData(List.of(invalid))).isEmpty();
        assertThat(assembler.mergeCountryData(List.of())).isEmpty();
    }

    @Test
    void buildStatisticsSnapshot_prefersSourcePriorityOverRecency() {
        RawCountryData worldBank = rawCountry(
                DataSource.WORLD_BANK,
                "JP",
                "JPN",
                "Japan",
                "Tokyo",
                List.of(rawStat("GDP", "GDP", Category.ECONOMY, "100", 2020, "0.70", "0.70"))
        );
        RawCountryData manual = rawCountry(
                DataSource.MANUAL,
                "JP",
                "JPN",
                "Japan",
                "Tokyo",
                List.of(rawStat("GDP", "GDP", Category.ECONOMY, "200", 2026, "0.99", "0.99"))
        );

        List<CountryStatistic> statistics = assembler.buildStatisticsSnapshot(COUNTRY_ID, List.of(manual, worldBank));

        assertThat(statistics).singleElement().satisfies(stat -> {
            assertThat(stat.getIndicatorCode()).isEqualTo("GDP");
            assertThat(stat.getSource()).isEqualTo(DataSource.WORLD_BANK);
            assertThat(stat.getValue()).isEqualByComparingTo("100.0000");
            assertThat(stat.getYear()).isEqualTo(2020);
        });
    }

    @Test
    void buildStatisticsSnapshot_prefersLatestYearWithinSamePriority() {
        RawCountryData wb2024 = rawCountry(
                DataSource.WORLD_BANK,
                "JP",
                "JPN",
                "Japan",
                "Tokyo",
                List.of(rawStat("POP", "Population", Category.DEMOGRAPHICS, "10", 2024, "0.90", "0.70"))
        );
        RawCountryData wb2026 = rawCountry(
                DataSource.WORLD_BANK,
                "JP",
                "JPN",
                "Japan",
                "Tokyo",
                List.of(rawStat("POP", "Population", Category.DEMOGRAPHICS, "12", 2026, "0.60", "0.70"))
        );

        List<CountryStatistic> statistics = assembler.buildStatisticsSnapshot(COUNTRY_ID, List.of(wb2024, wb2026));

        assertThat(statistics).singleElement().satisfies(stat -> {
            assertThat(stat.getYear()).isEqualTo(2026);
            assertThat(stat.getValue()).isEqualByComparingTo("12.0000");
        });
    }

    @Test
    void buildStatisticsSnapshot_prefersHigherConfidenceWhenPriorityAndYearMatch() {
        RawCountryData lowConfidence = rawCountry(
                DataSource.WORLD_BANK,
                "JP",
                "JPN",
                "Japan",
                "Tokyo",
                List.of(rawStat("LIFE", "Life expectancy", Category.HEALTH, "80", 2026, "0.60", "0.70"))
        );
        RawCountryData highConfidence = rawCountry(
                DataSource.WORLD_BANK,
                "JP",
                "JPN",
                "Japan",
                "Tokyo",
                List.of(rawStat("LIFE", "Life expectancy", Category.HEALTH, "82", 2026, "0.95", "0.70"))
        );

        List<CountryStatistic> statistics = assembler.buildStatisticsSnapshot(COUNTRY_ID, List.of(lowConfidence, highConfidence));

        assertThat(statistics).singleElement().satisfies(stat -> {
            assertThat(stat.getValue()).isEqualByComparingTo("82.0000");
            assertThat(stat.getConfidence()).isEqualByComparingTo("0.95");
        });
    }

    @Test
    void buildStatisticsSnapshot_normalizesTextAndRoundsValues() {
        RawCountryData worldBank = rawCountry(
                DataSource.WORLD_BANK,
                "JP",
                "JPN",
                "Japan",
                "Tokyo",
                List.of(new RawStatistic(
                        "GDP_PER_CAPITA",
                        " GDP&nbsp;per&nbsp;capita ",
                        Category.ECONOMY,
                        new BigDecimal("12.345678"),
                        "  valueText  ",
                        " USD ",
                        2026,
                        new BigDecimal("0.90"),
                        new BigDecimal("0.80")
                ))
        );

        List<CountryStatistic> statistics = assembler.buildStatisticsSnapshot(COUNTRY_ID, List.of(worldBank));

        assertThat(statistics).singleElement().satisfies(stat -> {
            assertThat(stat.getIndicatorName()).isEqualTo("GDP per capita");
            assertThat(stat.getValue()).isEqualByComparingTo("12.3457");
            assertThat(stat.getValueText()).isEqualTo("valueText");
            assertThat(stat.getUnit()).isEqualTo("USD");
        });
    }

    @Test
    void buildDynamicStatistics_marksStatisticsDynamicAndUsesFetchedAt() {
        List<CountryStatistic> statistics = assembler.buildDynamicStatistics(
                COUNTRY_ID,
                DataSource.GLOBAL_BRANDS,
                FETCHED_AT,
                List.of(rawStat("BRAND_TOP_APP", "Top app", Category.CULTURE, "1234567", 2026, null, null))
        );

        assertThat(statistics).singleElement().satisfies(stat -> {
            assertThat(stat.getSource()).isEqualTo(DataSource.GLOBAL_BRANDS);
            assertThat(stat.getIsDynamic()).isTrue();
            assertThat(stat.getIsCurrent()).isTrue();
            assertThat(stat.getFetchedAt()).isEqualTo(FETCHED_AT);
            assertThat(stat.getEffectiveDate()).isNull();
            assertThat(stat.getValue()).isEqualByComparingTo("1235000");
        });
    }

    @Test
    void buildStatisticsSnapshot_preservesImageAttributionMetadata() {
        RawStatistic statistic = new RawStatistic(
                "WM_LANDMARK_IMAGE",
                "Wikimedia landmark image",
                Category.LANDMARKS,
                null,
                "castle",
                "image",
                2026,
                new BigDecimal("0.92"),
                new BigDecimal("0.80"),
                "https://cdn.example.com/wikimedia/castle.jpg",
                "https://upload.wikimedia.org/castle.jpg",
                "https://commons.wikimedia.org/wiki/File:Castle.jpg",
                "Jane Doe",
                "Image by Jane Doe via Wikimedia Commons.",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/");

        List<CountryStatistic> statistics = assembler.buildStatisticsSnapshot(
                COUNTRY_ID,
                List.of(rawCountry(
                        DataSource.AI_GENERATED,
                        "JP",
                        "JPN",
                        "Japan",
                        "Tokyo",
                        List.of(statistic))));

        assertThat(statistics).singleElement().satisfies(stat -> {
            assertThat(stat.getImageUrl()).isEqualTo("https://cdn.example.com/wikimedia/castle.jpg");
            assertThat(stat.getOriginalImageUrl()).isEqualTo("https://upload.wikimedia.org/castle.jpg");
            assertThat(stat.getImageSourcePageUrl()).isEqualTo("https://commons.wikimedia.org/wiki/File:Castle.jpg");
            assertThat(stat.getImageAuthorName()).isEqualTo("Jane Doe");
            assertThat(stat.getImageAttributionText()).isEqualTo("Image by Jane Doe via Wikimedia Commons.");
            assertThat(stat.getImageLicenseLabel()).isEqualTo("CC BY-SA 4.0");
            assertThat(stat.getImageLicenseUrl()).isEqualTo("https://creativecommons.org/licenses/by-sa/4.0/");
        });
    }

    private RawCountryData rawCountry(DataSource source,
                                      String iso2,
                                      String iso3,
                                      String name,
                                      String capital,
                                      List<RawStatistic> statistics) {
        return RawCountryData.builder()
                .isoAlpha2(iso2)
                .isoAlpha3(iso3)
                .nameEn(name)
                .capital(capital)
                .source(source)
                .fetchedAt(FETCHED_AT)
                .statistics(statistics)
                .build();
    }

    private RawStatistic rawStat(String code,
                                 String name,
                                 Category category,
                                 String value,
                                 Integer year,
                                 String confidence,
                                 String weight) {
        return new RawStatistic(
                code,
                name,
                category,
                new BigDecimal(value),
                null,
                null,
                year,
                confidence != null ? new BigDecimal(confidence) : null,
                weight != null ? new BigDecimal(weight) : null
        );
    }
}
