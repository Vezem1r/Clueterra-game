package io.clueterra.questions.service.enrichment;

import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.enrichment.dto.IndicatorKey;
import io.clueterra.questions.service.hint.lens.LensRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticEnrichmentServiceTest {

    @Test
    void collectComparableIndicatorKeys_filtersDynamicAndInvalidStatistics() {
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        CountryJdbcRepository countryRepository = mock(CountryJdbcRepository.class);
        ConsoleLogWriter console = mock(ConsoleLogWriter.class);
        LensRegistry lensRegistry = mock(LensRegistry.class);

        StatisticEnrichmentService service = new StatisticEnrichmentService(
                statisticRepository,
                countryRepository,
                console,
                null,
                lensRegistry
        );

        CountryStatistic manual = stat(DataSource.MANUAL, "MANUAL_COUNTRY_COUNT", false, true);
        CountryStatistic dynamicFlag = stat(DataSource.GLOBAL_BRANDS, "BRAND_TOP_APP", true, true);
        CountryStatistic dynamicPrefix = stat(DataSource.GLOBAL_BRANDS, "BRAND_TOP_SONG", false, true);
        CountryStatistic inactive = stat(DataSource.WIKIDATA, "WIKIDATA_INACTIVE", false, false);
        CountryStatistic missingSource = CountryStatistic.builder()
                .indicatorCode("BROKEN")
                .category(Category.CULTURE)
                .value(BigDecimal.ONE)
                .isCurrent(true)
                .build();

        when(lensRegistry.isDynamicIndicator("MANUAL_COUNTRY_COUNT")).thenReturn(false);
        when(lensRegistry.isDynamicIndicator("BRAND_TOP_APP")).thenReturn(true);
        when(lensRegistry.isDynamicIndicator("BRAND_TOP_SONG")).thenReturn(true);
        when(lensRegistry.isDynamicIndicator("WIKIDATA_INACTIVE")).thenReturn(false);

        Set<IndicatorKey> keys = service.collectComparableIndicatorKeys(
                List.of(manual, dynamicFlag, dynamicPrefix, inactive, missingSource)
        );

        assertThat(keys).containsExactly(new IndicatorKey(DataSource.MANUAL, "MANUAL_COUNTRY_COUNT", 2026));
    }

    private CountryStatistic stat(DataSource source, String code, boolean isDynamic, boolean isCurrent) {
        return CountryStatistic.builder()
                .countryId(1L)
                .source(source)
                .indicatorCode(code)
                .indicatorName(code)
                .category(Category.CULTURE)
                .value(BigDecimal.ONE)
                .year(2026)
                .isDynamic(isDynamic)
                .isCurrent(isCurrent)
                .build();
    }
}
