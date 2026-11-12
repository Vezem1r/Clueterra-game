package io.clueterra.questions.clients.openstreetmap;

import io.clueterra.questions.clients.openstreetmap.dao.OsmCountryStatisticDao;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.service.enrichment.StatisticEnrichmentService;
import io.clueterra.questions.service.enrichment.dto.IndicatorKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OsmEnricherTest {

    @Test
    void enrichCountry_targetsOnlyIndicatorsPresentForThatCountry() {
        OsmCountryStatisticDao dao = mock(OsmCountryStatisticDao.class);
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        CountryJdbcRepository countryRepository = mock(CountryJdbcRepository.class);
        StatisticEnrichmentService enrichmentService = mock(StatisticEnrichmentService.class);

        OsmEnricher enricher = new OsmEnricher(dao, statisticRepository, countryRepository, enrichmentService);

        Country country = Country.builder()
                .id(77L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        when(countryRepository.findByIsoAlpha2("US")).thenReturn(Optional.of(country));
        when(statisticRepository.findCurrentByCountryIdAndSource(77L, DataSource.OPEN_STREET_MAP)).thenReturn(List.of(
                stat("OSM_MUSEUM"),
                stat("OSM_MUSEUM"),
                stat("OSM_STADIUM")
        ));
        when(enrichmentService.enrichIndicators(Set.of(
                new IndicatorKey(DataSource.OPEN_STREET_MAP, "OSM_MUSEUM", null),
                new IndicatorKey(DataSource.OPEN_STREET_MAP, "OSM_STADIUM", null)
        ))).thenReturn(9);

        int enriched = enricher.enrichCountry("US");

        assertThat(enriched).isEqualTo(9);
        verify(dao).syncCountryToMain("US");
        verify(statisticRepository).findCurrentByCountryIdAndSource(77L, DataSource.OPEN_STREET_MAP);
    }

    private CountryStatistic stat(String code) {
        return CountryStatistic.builder()
                .countryId(77L)
                .source(DataSource.OPEN_STREET_MAP)
                .indicatorCode(code)
                .indicatorName(code)
                .category(Category.CULTURE)
                .value(BigDecimal.ONE)
                .isCurrent(true)
                .build();
    }
}
