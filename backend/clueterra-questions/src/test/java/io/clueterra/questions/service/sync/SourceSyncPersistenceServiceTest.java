package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.service.challenge.CountryVisibilityProfileService;
import io.clueterra.questions.service.enrichment.StatisticEnrichmentService;
import io.clueterra.questions.service.enrichment.dto.IndicatorKey;
import io.clueterra.questions.service.source.SourceModule;
import io.clueterra.questions.service.source.SourcePolicy;
import io.clueterra.questions.service.source.SourceStatisticProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceSyncPersistenceServiceTest {

    private final CountryJdbcRepository countryRepository = mock(CountryJdbcRepository.class);
    private final StatisticRepository statisticRepository = mock(StatisticRepository.class);
    private final CountryVisibilityProfileService visibilityProfileService = mock(CountryVisibilityProfileService.class);
    private final StatisticEnrichmentService enrichmentService = mock(StatisticEnrichmentService.class);
    private final SourceStatisticProcessor processor = mock(SourceStatisticProcessor.class);
    private final SourceSyncPersistenceService service = new SourceSyncPersistenceService(
            countryRepository, statisticRepository, visibilityProfileService, enrichmentService);

    @Test
    void persistSlice_newCountry_insertsCountryAndReplacesSnapshot() {
        SourceModule module = module(DataSource.WORLD_BANK, SourcePolicy.staticSource(true));
        RawCountryData rawData = raw("JP", DataSource.WORLD_BANK, true);
        Country incoming = Country.builder().isoAlpha2("JP").isoAlpha3("JPN").nameEn("Japan")
                .region("East Asia & Pacific").isActive(false).build();
        Country saved = Country.builder().id(15L).isoAlpha2("JP").isoAlpha3("JPN").nameEn("Japan").build();
        CountryStatistic statistic = statistic(15L, DataSource.WORLD_BANK, "SP.POP.TOTL");

        when(processor.mergeCountryData(List.of(rawData))).thenReturn(Optional.of(incoming));
        when(processor.buildStatisticsSnapshot(15L, List.of(rawData))).thenReturn(List.of(statistic));
        when(countryRepository.findByIsoAlpha2("JP")).thenReturn(Optional.empty());
        when(countryRepository.findByIsoAlpha3("JPN")).thenReturn(Optional.empty());
        when(visibilityProfileService.getModifier("JP")).thenReturn(new BigDecimal("1.05"));
        when(countryRepository.insert(any(Country.class))).thenReturn(saved);
        when(statisticRepository.deleteStaleSnapshot(15L, DataSource.WORLD_BANK, List.of(statistic), false)).thenReturn(1);
        when(enrichmentService.collectComparableIndicatorKeys(List.of(statistic)))
                .thenReturn(Set.of(new IndicatorKey(DataSource.WORLD_BANK, "SP.POP.TOTL", 2026)));

        SlicePersistenceResult result = service.persistSlice(module, rawData, true);

        ArgumentCaptor<Country> inserted = ArgumentCaptor.forClass(Country.class);
        verify(countryRepository).insert(inserted.capture());
        assertThat(inserted.getValue().getRegion()).isNull();
        assertThat(inserted.getValue().getIsActive()).isTrue();
        assertThat(inserted.getValue().getDifficultyModifier()).isEqualByComparingTo("1.05");
        verify(statisticRepository).batchUpsert(List.of(statistic));
        assertThat(result.country()).isEqualTo(saved);
        assertThat(result.statisticsSaved()).isEqualTo(1);
        assertThat(result.staleStatisticsDeleted()).isEqualTo(1);
        assertThat(result.changedIndicatorCodes()).containsExactly("SP.POP.TOTL");
        assertThat(result.comparableKeys()).containsExactly(new IndicatorKey(DataSource.WORLD_BANK, "SP.POP.TOTL", 2026));
    }

    @Test
    void persistSlice_completeFetchWithoutStatistics_deletesWholeCurrentSnapshot() {
        SourceModule module = module(DataSource.WORLD_BANK, SourcePolicy.staticSource(true));
        RawCountryData rawData = raw("JP", DataSource.WORLD_BANK, true);
        Country existing = existingJapan();

        when(processor.mergeCountryData(List.of(rawData))).thenReturn(Optional.of(existing));
        when(processor.buildStatisticsSnapshot(15L, List.of(rawData))).thenReturn(List.of());
        when(countryRepository.findByIsoAlpha2("JP")).thenReturn(Optional.of(existing));
        when(countryRepository.fillMissingFromSource(existing, existing)).thenReturn(existing);
        when(statisticRepository.deleteAllCurrentByCountryAndSource(15L, DataSource.WORLD_BANK, false)).thenReturn(3);

        SlicePersistenceResult result = service.persistSlice(module, rawData, true);

        verify(statisticRepository, never()).batchUpsert(any());
        assertThat(result.staleStatisticsDeleted()).isEqualTo(3);
    }

    @Test
    void persistSlice_incompleteFetch_keepsStatisticsMissingFromSnapshot() {
        SourceModule module = module(DataSource.WORLD_BANK, SourcePolicy.staticSource(true));
        RawCountryData rawData = raw("JP", DataSource.WORLD_BANK, false);
        Country existing = existingJapan();
        CountryStatistic statistic = statistic(15L, DataSource.WORLD_BANK, "SP.POP.TOTL");

        when(processor.mergeCountryData(List.of(rawData))).thenReturn(Optional.of(existing));
        when(processor.buildStatisticsSnapshot(15L, List.of(rawData))).thenReturn(List.of(statistic));
        when(countryRepository.findByIsoAlpha2("JP")).thenReturn(Optional.of(existing));
        when(countryRepository.fillMissingFromSource(existing, existing)).thenReturn(existing);

        SlicePersistenceResult result = service.persistSlice(module, rawData, false);

        verify(statisticRepository).batchUpsert(List.of(statistic));
        verify(statisticRepository, never()).deleteStaleSnapshot(anyLong(), any(), any(), anyBoolean());
        verify(statisticRepository, never()).deleteAllCurrentByCountryAndSource(anyLong(), any(), anyBoolean());
        assertThat(result.staleStatisticsDeleted()).isZero();
    }

    @Test
    void persistSlice_existingCountry_onlyFillsMissingFieldsAndKeepsAdminState() {
        SourceModule module = module(DataSource.MANUAL, SourcePolicy.staticSource(true));
        RawCountryData rawData = raw("JP", DataSource.MANUAL, true);
        Country incoming = Country.builder().isoAlpha2("JP").isoAlpha3("JPN").nameEn("Japan Updated").capital("Kyoto").build();
        Country existing = existingJapan();

        when(processor.mergeCountryData(List.of(rawData))).thenReturn(Optional.of(incoming));
        when(processor.buildStatisticsSnapshot(15L, List.of(rawData))).thenReturn(List.of());
        when(countryRepository.findByIsoAlpha2("JP")).thenReturn(Optional.of(existing));
        when(countryRepository.fillMissingFromSource(existing, incoming)).thenReturn(existing);

        service.persistSlice(module, rawData, true);

        verify(countryRepository).fillMissingFromSource(existing, incoming);
        verify(countryRepository, never()).insert(any());
        verify(visibilityProfileService, never()).getModifier(any());
    }

    @Test
    void persistSlice_dynamicSource_usesDynamicStatisticsPath() {
        SourceModule module = module(DataSource.GLOBAL_BRANDS, SourcePolicy.dynamicSource());
        RawCountryData rawData = RawCountryData.builder()
                .isoAlpha2("JP")
                .isoAlpha3("JPN")
                .nameEn("Japan")
                .source(DataSource.GLOBAL_BRANDS)
                .fetchedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .statistics(List.of(new RawStatistic("BRAND_TOP", "Top", Category.CULTURE, BigDecimal.ONE, null, null, 2026, null, null)))
                .build();
        Country existing = existingJapan();
        CountryStatistic dynamicStat = statistic(15L, DataSource.GLOBAL_BRANDS, "BRAND_TOP");

        when(processor.mergeCountryData(List.of(rawData))).thenReturn(Optional.of(existing));
        when(countryRepository.findByIsoAlpha2("JP")).thenReturn(Optional.of(existing));
        when(countryRepository.fillMissingFromSource(existing, existing)).thenReturn(existing);
        when(processor.buildDynamicStatistics(15L, DataSource.GLOBAL_BRANDS, rawData.getFetchedAt(), rawData.getStatistics()))
                .thenReturn(List.of(dynamicStat));

        SlicePersistenceResult result = service.persistSlice(module, rawData, true);

        verify(statisticRepository).deleteStaleSnapshot(15L, DataSource.GLOBAL_BRANDS, List.of(dynamicStat), true);
        assertThat(result.statisticsSaved()).isEqualTo(1);
    }

    private SourceModule module(DataSource source, SourcePolicy policy) {
        SourceModule module = mock(SourceModule.class);
        when(module.source()).thenReturn(source);
        when(module.policy()).thenReturn(policy);
        when(module.statisticProcessor()).thenReturn(processor);
        when(module.client()).thenReturn(mock(DataSourceClient.class));
        return module;
    }

    private static RawCountryData raw(String iso2, DataSource source, boolean complete) {
        return RawCountryData.builder()
                .isoAlpha2(iso2)
                .isoAlpha3("JPN")
                .nameEn("Japan")
                .source(source)
                .statistics(List.of())
                .statisticsComplete(complete)
                .build();
    }

    private static Country existingJapan() {
        return Country.builder()
                .id(15L)
                .isoAlpha2("JP")
                .isoAlpha3("JPN")
                .nameEn("Japan")
                .capital("Tokyo")
                .isActive(false)
                .difficultyModifier(new BigDecimal("1.40"))
                .build();
    }

    private static CountryStatistic statistic(Long countryId, DataSource source, String code) {
        return CountryStatistic.builder()
                .countryId(countryId)
                .source(source)
                .indicatorCode(code)
                .indicatorName(code)
                .category(Category.DEMOGRAPHICS)
                .value(new BigDecimal("100"))
                .year(2026)
                .isCurrent(true)
                .build();
    }
}
