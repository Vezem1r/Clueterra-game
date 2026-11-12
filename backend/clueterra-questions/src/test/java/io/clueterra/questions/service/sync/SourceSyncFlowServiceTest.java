package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.dto.FetchFailure;
import io.clueterra.questions.clients.common.dto.FetchResult;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.openstreetmap.OsmEnricher;
import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.DataSyncLog;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.image.ContentRetirementService;
import io.clueterra.questions.repository.jdbc.DataSyncLogJdbcRepository;
import io.clueterra.questions.service.enrichment.StatisticEnrichmentService;
import io.clueterra.questions.service.enrichment.dto.IndicatorKey;
import io.clueterra.questions.service.hint.HintGeneratorService;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.StatisticContextBuilder;
import io.clueterra.questions.service.source.SourceModule;
import io.clueterra.questions.service.source.SourcePolicy;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceSyncFlowServiceTest {

    @Test
    void syncFull_persistsEveryChunkBeforeGeneratingHintsFromTheCompleteSnapshot() {
        SourceSyncPersistenceService persistenceService = mock(SourceSyncPersistenceService.class);
        StatisticEnrichmentService enrichmentService = mock(StatisticEnrichmentService.class);
        HintGeneratorService hintGeneratorService = mock(HintGeneratorService.class);
        DataSyncLogJdbcRepository syncLogRepository = mock(DataSyncLogJdbcRepository.class);
        SyncProgressReporter progressReporter = mock(SyncProgressReporter.class);
        OsmEnricher osmEnricher = mock(OsmEnricher.class);

        SourceSyncFlowService flowService = new SourceSyncFlowService(
                persistenceService,
                enrichmentService,
                hintGeneratorService,
                syncLogRepository,
                progressReporter,
                osmEnricher,
                mock(ContentRetirementService.class),
                mock(StatisticContextBuilder.class),
                properties(2)
        );

        DataSourceClient client = mock(DataSourceClient.class);
        SourceModule module = mock(SourceModule.class);
        HintLens lens = mock(HintLens.class);
        Country us = Country.builder().id(1L).isoAlpha2("US").build();
        Country jp = Country.builder().id(2L).isoAlpha2("JP").build();
        Country de = Country.builder().id(3L).isoAlpha2("DE").build();

        RawCountryData usData = RawCountryData.builder().isoAlpha2("US").source(DataSource.MANUAL).build();
        RawCountryData jpData = RawCountryData.builder().isoAlpha2("JP").source(DataSource.MANUAL).build();
        RawCountryData deData = RawCountryData.builder().isoAlpha2("DE").source(DataSource.MANUAL).build();

        when(module.source()).thenReturn(DataSource.MANUAL);
        when(module.client()).thenReturn(client);
        when(module.policy()).thenReturn(SourcePolicy.staticSource(true));
        when(module.lenses()).thenReturn(List.of(lens));
        when(syncLogRepository.save(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncLogRepository.update(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.fetchAllCountries(any())).thenReturn(List.of(usData, jpData, deData));

        when(persistenceService.persistSlice(module, usData, true)).thenReturn(new SlicePersistenceResult(
                us, 2, 1, Set.of(new IndicatorKey(DataSource.MANUAL, "MANUAL_A", 2026)), Set.of()
        ));
        when(persistenceService.persistSlice(module, jpData, true)).thenReturn(new SlicePersistenceResult(
                jp, 1, 0, Set.of(new IndicatorKey(DataSource.MANUAL, "MANUAL_B", 2026)), Set.of()
        ));
        when(persistenceService.persistSlice(module, deData, true)).thenReturn(new SlicePersistenceResult(
                de, 3, 2, Set.of(new IndicatorKey(DataSource.MANUAL, "MANUAL_C", 2026)), Set.of()
        ));

        when(enrichmentService.enrichIndicators(Set.of(
                new IndicatorKey(DataSource.MANUAL, "MANUAL_A", 2026),
                new IndicatorKey(DataSource.MANUAL, "MANUAL_B", 2026),
                new IndicatorKey(DataSource.MANUAL, "MANUAL_C", 2026)
        ))).thenReturn(7);

        when(hintGeneratorService.generateHintsForCountryAndSource(us, DataSource.MANUAL)).thenReturn(2);
        when(hintGeneratorService.generateHintsForCountryAndSource(jp, DataSource.MANUAL)).thenReturn(1);
        when(hintGeneratorService.generateHintsForCountryAndSource(de, DataSource.MANUAL)).thenReturn(3);

        SourceSyncResult result = flowService.syncFull(module, 10L, "op-1");

        InOrder sourceOrder = inOrder(persistenceService, module, hintGeneratorService);
        sourceOrder.verify(persistenceService).persistSlice(module, usData, true);
        sourceOrder.verify(persistenceService).persistSlice(module, jpData, true);
        sourceOrder.verify(persistenceService).persistSlice(module, deData, true);
        sourceOrder.verify(module).invalidateLensCaches();
        sourceOrder.verify(hintGeneratorService).generateHintsForCountryAndSource(us, DataSource.MANUAL);

        verify(enrichmentService, times(1)).enrichIndicators(any());
        verify(hintGeneratorService).generateHintsForCountryAndSource(us, DataSource.MANUAL);
        verify(hintGeneratorService).generateHintsForCountryAndSource(jp, DataSource.MANUAL);
        verify(hintGeneratorService).generateHintsForCountryAndSource(de, DataSource.MANUAL);
        verify(syncLogRepository).update(any(DataSyncLog.class));

        assertThat(result.countriesProcessed()).isEqualTo(3);
        assertThat(result.statisticsSaved()).isEqualTo(6);
        assertThat(result.hintsGenerated()).isEqualTo(6);
        assertThat(result.staleStatisticsDeleted()).isEqualTo(3);
        assertThat(result.enrichedStatistics()).isEqualTo(7);
    }

    @Test
    void syncFull_recordsCountryPersistenceFailureAndContinuesRemainingCountries() {
        SourceSyncPersistenceService persistenceService = mock(SourceSyncPersistenceService.class);
        StatisticEnrichmentService enrichmentService = mock(StatisticEnrichmentService.class);
        HintGeneratorService hintGeneratorService = mock(HintGeneratorService.class);
        DataSyncLogJdbcRepository syncLogRepository = mock(DataSyncLogJdbcRepository.class);
        SyncProgressReporter progressReporter = mock(SyncProgressReporter.class);
        OsmEnricher osmEnricher = mock(OsmEnricher.class);

        SourceSyncFlowService flowService = new SourceSyncFlowService(
                persistenceService,
                enrichmentService,
                hintGeneratorService,
                syncLogRepository,
                progressReporter,
                osmEnricher,
                mock(ContentRetirementService.class),
                mock(StatisticContextBuilder.class),
                properties(3)
        );

        DataSourceClient client = mock(DataSourceClient.class);
        SourceModule module = mock(SourceModule.class);
        Country us = Country.builder().id(1L).isoAlpha2("US").build();
        Country jp = Country.builder().id(2L).isoAlpha2("JP").build();
        RawCountryData usData = RawCountryData.builder().isoAlpha2("US").source(DataSource.NAGER_DATE).build();
        RawCountryData lvData = RawCountryData.builder().isoAlpha2("LV").source(DataSource.NAGER_DATE).build();
        RawCountryData jpData = RawCountryData.builder().isoAlpha2("JP").source(DataSource.NAGER_DATE).build();

        when(module.source()).thenReturn(DataSource.NAGER_DATE);
        when(module.client()).thenReturn(client);
        when(module.policy()).thenReturn(SourcePolicy.staticSource(false));
        when(syncLogRepository.save(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncLogRepository.update(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.fetchAllCountries(any())).thenReturn(List.of(usData, lvData, jpData));
        when(persistenceService.persistSlice(module, usData, true)).thenReturn(
                new SlicePersistenceResult(us, 3, 0, Set.of(), Set.of()));
        when(persistenceService.persistSlice(module, lvData, true)).thenThrow(
                new DataIntegrityViolationException("value too long for type character varying(500)"));
        when(persistenceService.persistSlice(module, jpData, true)).thenReturn(
                new SlicePersistenceResult(jp, 3, 0, Set.of(), Set.of()));
        when(hintGeneratorService.generateHintsForCountryAndSource(us, DataSource.NAGER_DATE)).thenReturn(2);
        when(hintGeneratorService.generateHintsForCountryAndSource(jp, DataSource.NAGER_DATE)).thenReturn(2);

        SourceSyncResult result = flowService.syncFull(module, 10L, "op-partial");

        assertThat(result.countriesProcessed()).isEqualTo(2);
        assertThat(result.statisticsSaved()).isEqualTo(6);
        assertThat(result.hintsGenerated()).isEqualTo(4);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.countryCode()).isEqualTo("LV");
            assertThat(failure.feedType()).isEqualTo("PERSIST");
            assertThat(failure.error()).contains("value too long");
        });
        verify(persistenceService).persistSlice(module, jpData, true);
        verify(hintGeneratorService).generateHintsForCountryAndSource(us, DataSource.NAGER_DATE);
        verify(hintGeneratorService).generateHintsForCountryAndSource(jp, DataSource.NAGER_DATE);
        verify(syncLogRepository).update(argThat(log -> log.getFetchDetails() != null
                && log.getFetchDetails().contains("\"countryCode\":\"LV\"")
                && log.getFetchDetails().contains("\"feedType\":\"PERSIST\"")));
    }

    @Test
    void syncCountry_returnsEmptyWhenSourceHasNoCountryData() {
        SourceSyncPersistenceService persistenceService = mock(SourceSyncPersistenceService.class);
        StatisticEnrichmentService enrichmentService = mock(StatisticEnrichmentService.class);
        HintGeneratorService hintGeneratorService = mock(HintGeneratorService.class);
        DataSyncLogJdbcRepository syncLogRepository = mock(DataSyncLogJdbcRepository.class);
        SyncProgressReporter progressReporter = mock(SyncProgressReporter.class);
        OsmEnricher osmEnricher = mock(OsmEnricher.class);

        SourceSyncFlowService flowService = new SourceSyncFlowService(
                persistenceService,
                enrichmentService,
                hintGeneratorService,
                syncLogRepository,
                progressReporter,
                osmEnricher,
                mock(ContentRetirementService.class),
                mock(StatisticContextBuilder.class),
                properties(50)
        );

        DataSourceClient client = mock(DataSourceClient.class);
        SourceModule module = mock(SourceModule.class);
        when(module.source()).thenReturn(DataSource.MANUAL);
        when(module.client()).thenReturn(client);
        when(module.policy()).thenReturn(SourcePolicy.staticSource(true));
        when(syncLogRepository.save(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncLogRepository.update(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.fetchCountryByCode("US")).thenReturn(Optional.empty());

        SourceSyncResult result = flowService.syncCountry(module, "US", 99L, "op-empty");

        assertThat(result).isEqualTo(SourceSyncResult.empty(DataSource.MANUAL));
        verify(persistenceService, times(0)).persistSlice(any(), any(), anyBoolean());
        verify(hintGeneratorService, times(0)).generateHintsForCountryAndSource(any(), any());
    }

    @Test
    void syncFull_propagatesFetchFailuresIntoUpdatedSyncLog() {
        SourceSyncPersistenceService persistenceService = mock(SourceSyncPersistenceService.class);
        StatisticEnrichmentService enrichmentService = mock(StatisticEnrichmentService.class);
        HintGeneratorService hintGeneratorService = mock(HintGeneratorService.class);
        DataSyncLogJdbcRepository syncLogRepository = mock(DataSyncLogJdbcRepository.class);
        SyncProgressReporter progressReporter = mock(SyncProgressReporter.class);
        OsmEnricher osmEnricher = mock(OsmEnricher.class);

        SourceSyncFlowService flowService = new SourceSyncFlowService(
                persistenceService,
                enrichmentService,
                hintGeneratorService,
                syncLogRepository,
                progressReporter,
                osmEnricher,
                mock(ContentRetirementService.class),
                mock(StatisticContextBuilder.class),
                properties(50)
        );

        DataSourceClient client = mock(DataSourceClient.class);
        SourceModule module = mock(SourceModule.class);
        when(module.source()).thenReturn(DataSource.MANUAL);
        when(module.client()).thenReturn(client);
        when(module.policy()).thenReturn(SourcePolicy.staticSource(true));
        when(syncLogRepository.save(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncLogRepository.update(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.fetchAllCountries(any())).thenReturn(List.of());
        when(client.getLastFetchResult()).thenReturn(new FetchResult(0, List.of(
                new FetchFailure("US", "feed", "boom")
        )));

        flowService.syncFull(module, 10L, "op-1");

        verify(syncLogRepository).update(argThat(log ->
                log.getFetchDetails() != null && log.getFetchDetails().contains("\"countryCode\":\"US\"")));
    }

    @Test
    void syncFull_forOsmDelegatesToOsmEnricher() {
        SourceSyncPersistenceService persistenceService = mock(SourceSyncPersistenceService.class);
        StatisticEnrichmentService enrichmentService = mock(StatisticEnrichmentService.class);
        HintGeneratorService hintGeneratorService = mock(HintGeneratorService.class);
        DataSyncLogJdbcRepository syncLogRepository = mock(DataSyncLogJdbcRepository.class);
        SyncProgressReporter progressReporter = mock(SyncProgressReporter.class);
        OsmEnricher osmEnricher = mock(OsmEnricher.class);

        SourceSyncFlowService flowService = new SourceSyncFlowService(
                persistenceService,
                enrichmentService,
                hintGeneratorService,
                syncLogRepository,
                progressReporter,
                osmEnricher,
                mock(ContentRetirementService.class),
                mock(StatisticContextBuilder.class),
                properties(50)
        );

        SourceModule module = mock(SourceModule.class);
        when(module.source()).thenReturn(DataSource.OPEN_STREET_MAP);
        when(module.policy()).thenReturn(SourcePolicy.enrichmentOnlySource());
        when(syncLogRepository.save(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncLogRepository.update(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(osmEnricher.enrich()).thenReturn(17);

        SourceSyncResult result = flowService.syncFull(module, 10L, "op-osm");

        assertThat(result.statisticsSaved()).isEqualTo(17);
        assertThat(result.enrichedStatistics()).isEqualTo(17);
        verify(osmEnricher).enrich();
        verify(persistenceService, times(0)).persistSlice(any(), any(), anyBoolean());
    }

    @Test
    void syncFull_skipsComparativeEnrichmentWhenChunkHasNoComparableKeys() {
        SourceSyncPersistenceService persistenceService = mock(SourceSyncPersistenceService.class);
        StatisticEnrichmentService enrichmentService = mock(StatisticEnrichmentService.class);
        HintGeneratorService hintGeneratorService = mock(HintGeneratorService.class);
        DataSyncLogJdbcRepository syncLogRepository = mock(DataSyncLogJdbcRepository.class);
        SyncProgressReporter progressReporter = mock(SyncProgressReporter.class);
        OsmEnricher osmEnricher = mock(OsmEnricher.class);

        SourceSyncFlowService flowService = new SourceSyncFlowService(
                persistenceService,
                enrichmentService,
                hintGeneratorService,
                syncLogRepository,
                progressReporter,
                osmEnricher,
                mock(ContentRetirementService.class),
                mock(StatisticContextBuilder.class),
                properties(50)
        );

        DataSourceClient client = mock(DataSourceClient.class);
        SourceModule module = mock(SourceModule.class);
        Country us = Country.builder().id(1L).isoAlpha2("US").build();
        RawCountryData usData = RawCountryData.builder().isoAlpha2("US").source(DataSource.MANUAL).build();

        when(module.source()).thenReturn(DataSource.MANUAL);
        when(module.client()).thenReturn(client);
        when(module.policy()).thenReturn(SourcePolicy.staticSource(true));
        when(syncLogRepository.save(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncLogRepository.update(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.fetchAllCountries(any())).thenReturn(List.of(usData));
        when(persistenceService.persistSlice(module, usData, true)).thenReturn(new SlicePersistenceResult(
                us, 2, 1, Set.of()
        , Set.of()));
        when(hintGeneratorService.generateHintsForCountryAndSource(us, DataSource.MANUAL)).thenReturn(2);

        SourceSyncResult result = flowService.syncFull(module, 10L, "op-no-compare");

        assertThat(result.countriesProcessed()).isEqualTo(1);
        assertThat(result.statisticsSaved()).isEqualTo(2);
        assertThat(result.hintsGenerated()).isEqualTo(2);
        assertThat(result.enrichedStatistics()).isZero();
        verify(enrichmentService, times(0)).enrichIndicators(any());
        verify(hintGeneratorService).generateHintsForCountryAndSource(us, DataSource.MANUAL);
    }

    @Test
    void syncCountry_forOsmDelegatesToCountryEnricher() {
        SourceSyncPersistenceService persistenceService = mock(SourceSyncPersistenceService.class);
        StatisticEnrichmentService enrichmentService = mock(StatisticEnrichmentService.class);
        HintGeneratorService hintGeneratorService = mock(HintGeneratorService.class);
        DataSyncLogJdbcRepository syncLogRepository = mock(DataSyncLogJdbcRepository.class);
        SyncProgressReporter progressReporter = mock(SyncProgressReporter.class);
        OsmEnricher osmEnricher = mock(OsmEnricher.class);

        SourceSyncFlowService flowService = new SourceSyncFlowService(
                persistenceService,
                enrichmentService,
                hintGeneratorService,
                syncLogRepository,
                progressReporter,
                osmEnricher,
                mock(ContentRetirementService.class),
                mock(StatisticContextBuilder.class),
                properties(50)
        );

        SourceModule module = mock(SourceModule.class);
        when(module.source()).thenReturn(DataSource.OPEN_STREET_MAP);
        when(module.policy()).thenReturn(SourcePolicy.enrichmentOnlySource());
        when(syncLogRepository.save(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncLogRepository.update(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(osmEnricher.enrichCountry("US")).thenReturn(5);

        SourceSyncResult result = flowService.syncCountry(module, "US", 10L, "op-osm-country");

        assertThat(result.countriesProcessed()).isEqualTo(1);
        assertThat(result.statisticsSaved()).isEqualTo(5);
        verify(osmEnricher).enrichCountry("US");
    }

    @Test
    void syncFull_incompleteFetchKeepsStaleStatistics() {
        SourceSyncPersistenceService persistenceService = mock(SourceSyncPersistenceService.class);
        HintGeneratorService hintGeneratorService = mock(HintGeneratorService.class);
        DataSyncLogJdbcRepository syncLogRepository = mock(DataSyncLogJdbcRepository.class);

        SourceSyncFlowService flowService = new SourceSyncFlowService(
                persistenceService,
                mock(StatisticEnrichmentService.class),
                hintGeneratorService,
                syncLogRepository,
                mock(SyncProgressReporter.class),
                mock(OsmEnricher.class),
                mock(ContentRetirementService.class),
                mock(StatisticContextBuilder.class),
                properties(50)
        );

        DataSourceClient client = mock(DataSourceClient.class);
        SourceModule module = mock(SourceModule.class);
        Country us = Country.builder().id(1L).isoAlpha2("US").build();
        Country jp = Country.builder().id(2L).isoAlpha2("JP").build();
        RawCountryData usData = RawCountryData.builder().isoAlpha2("US").source(DataSource.WORLD_BANK).build();
        RawCountryData jpData = RawCountryData.builder().isoAlpha2("JP").source(DataSource.WORLD_BANK)
                .statisticsComplete(false).build();
        RawCountryData deData = RawCountryData.builder().isoAlpha2("DE").source(DataSource.WORLD_BANK).build();

        when(module.source()).thenReturn(DataSource.WORLD_BANK);
        when(module.client()).thenReturn(client);
        when(module.policy()).thenReturn(SourcePolicy.staticSource(false));
        when(syncLogRepository.save(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.fetchAllCountries(any())).thenReturn(List.of(usData, jpData, deData));
        when(client.getLastFetchResult()).thenReturn(new FetchResult(3, List.of(new FetchFailure("DE", "INDICATORS", "HTTP 503"))));
        when(persistenceService.persistSlice(eq(module), any(), anyBoolean()))
                .thenReturn(new SlicePersistenceResult(us, 1, 0, Set.of(), Set.of()));

        flowService.syncFull(module, 10L, "op-incomplete");

        verify(persistenceService).persistSlice(module, usData, true);
        verify(persistenceService).persistSlice(module, jpData, false);
        verify(persistenceService).persistSlice(module, deData, false);
    }

    @Test
    void syncFull_datasetLevelFetchFailureKeepsStaleStatisticsForEveryCountry() {
        SourceSyncPersistenceService persistenceService = mock(SourceSyncPersistenceService.class);
        DataSyncLogJdbcRepository syncLogRepository = mock(DataSyncLogJdbcRepository.class);

        SourceSyncFlowService flowService = new SourceSyncFlowService(
                persistenceService,
                mock(StatisticEnrichmentService.class),
                mock(HintGeneratorService.class),
                syncLogRepository,
                mock(SyncProgressReporter.class),
                mock(OsmEnricher.class),
                mock(ContentRetirementService.class),
                mock(StatisticContextBuilder.class),
                properties(50)
        );

        DataSourceClient client = mock(DataSourceClient.class);
        SourceModule module = mock(SourceModule.class);
        RawCountryData usData = RawCountryData.builder().isoAlpha2("US").source(DataSource.OUR_WORLD_IN_DATA).build();

        when(module.source()).thenReturn(DataSource.OUR_WORLD_IN_DATA);
        when(module.client()).thenReturn(client);
        when(module.policy()).thenReturn(SourcePolicy.staticSource(false));
        when(syncLogRepository.save(any(DataSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.fetchAllCountries(any())).thenReturn(List.of(usData));
        when(client.getLastFetchResult()).thenReturn(new FetchResult(1, List.of(new FetchFailure(null, "OWID_GDP", "HTTP 500"))));
        when(persistenceService.persistSlice(eq(module), any(), anyBoolean()))
                .thenReturn(SlicePersistenceResult.empty());

        flowService.syncFull(module, 10L, "op-dataset-failure");

        verify(persistenceService).persistSlice(module, usData, false);
    }

    private static AppProperties properties(int chunkSize) {
        AppProperties properties = new AppProperties();
        properties.getSync().setChunkSize(chunkSize);
        return properties;
    }
}
