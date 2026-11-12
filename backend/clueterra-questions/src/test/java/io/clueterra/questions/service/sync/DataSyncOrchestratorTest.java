package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.dto.FetchFailure;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.OperationBatchLog;
import io.clueterra.questions.entity.OperationTriggerType;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.exception.ResourceNotFoundException;
import io.clueterra.questions.repository.OperationBatchLogRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.hint.HintGenerationResult;
import io.clueterra.questions.service.hint.HintGeneratorService;
import io.clueterra.questions.service.hint.combinatorics.CombinatoricsHintEngine;
import io.clueterra.questions.service.hint.lens.LensRegistry;
import io.clueterra.questions.service.hint.lens.StatisticContextBuilder;
import io.clueterra.questions.service.operation.AdminOperationContext;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import io.clueterra.questions.service.operation.OperationSummary;
import io.clueterra.questions.service.source.SourceModule;
import io.clueterra.questions.service.source.SourceModuleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataSyncOrchestratorTest {

    private final CountryJdbcRepository countryRepository = mock(CountryJdbcRepository.class);
    private final HintGeneratorService hintGeneratorService = mock(HintGeneratorService.class);
    private final CombinatoricsHintEngine combinatoricsHintEngine = mock(CombinatoricsHintEngine.class);
    private final ConsoleLogWriter console = mock(ConsoleLogWriter.class);
    private final LensRegistry lensRegistry = mock(LensRegistry.class);
    private final StatisticContextBuilder contextBuilder = mock(StatisticContextBuilder.class);
    private final SourceModuleRegistry sourceModuleRegistry = mock(SourceModuleRegistry.class);
    private final SourceSyncFlowService sourceSyncFlowService = mock(SourceSyncFlowService.class);
    private final OperationBatchLogRepository batchLogRepository = mock(OperationBatchLogRepository.class);
    private final AdminOperationRunner operationRunner = mock(AdminOperationRunner.class);

    private final DataSyncOrchestrator orchestrator = new DataSyncOrchestrator(
            countryRepository,
            hintGeneratorService,
            combinatoricsHintEngine,
            console,
            lensRegistry,
            contextBuilder,
            sourceModuleRegistry,
            sourceSyncFlowService,
            batchLogRepository,
            operationRunner);

    @Test
    void runSourceSync_countsSourceWithCountryFailuresAsFailedAndKeepsDetails() {
        SourceModule sourceModule = module(DataSource.NAGER_DATE);
        AdminOperationContext context = context(OperationType.DATA_SYNC);
        SourceSyncResult result = new SourceSyncResult(DataSource.NAGER_DATE, 249, 800, 500, 0, 0,
                List.of(new FetchFailure("LV", "PERSIST", "value too long")));
        when(sourceSyncFlowService.syncFull(sourceModule, null, "op")).thenReturn(result);

        OperationSummary summary = orchestrator.runSourceSync(context, sourceModule);

        assertThat(context.batchLog().getSuccessfulItems()).isZero();
        assertThat(context.batchLog().getFailedItems()).isEqualTo(1);
        assertThat(summary.asMap()).containsEntry("source", "NAGER_DATE");
        assertThat(summary.asMap().get("errors")).asList()
                .containsExactly("NAGER_DATE [LV/PERSIST]: value too long");
    }

    @Test
    void rejectsOperationsThatTheSourceDoesNotSupportBeforeLaunching() {
        assertThatThrownBy(() -> orchestrator.startSourceSync(DataSource.HYDRO_RIVERS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support synchronization");
        assertThatThrownBy(() -> orchestrator.startSourceHintGeneration(DataSource.COMBINATORICS.name()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support hint generation");

        verifyNoInteractions(operationRunner, batchLogRepository, sourceModuleRegistry);
    }

    @Test
    void startCountryHintGeneration_rejectsUnknownCountryBeforeLaunching() {
        when(countryRepository.findByIsoAlpha2("ZZ")).thenReturn(Optional.empty());
        when(countryRepository.findByIsoAlpha3("ZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.startCountryHintGeneration("zz"))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(operationRunner);
    }

    @Test
    void runCountrySourceSync_delegatesToUnifiedSourceFlow() {
        SourceModule sourceModule = module(DataSource.CURATED_COUNTRY_FACTS);
        when(sourceSyncFlowService.syncCountry(eq(sourceModule), eq("US"), any(), any())).thenReturn(
                new SourceSyncResult(DataSource.CURATED_COUNTRY_FACTS, 1, 3, 2, 1, 3));

        orchestrator.runCountrySourceSync(context(OperationType.DATA_SYNC), sourceModule, "US");

        verify(sourceSyncFlowService).syncCountry(eq(sourceModule), eq("US"), any(), any());
        verify(console).success("Synced US from Curated Country Facts: 3 stats, 2 hints");
    }

    @Test
    void runCountrySync_failsWhenNoSourceHasData() {
        SourceModule sourceModule = module(DataSource.WORLD_BANK);
        DataSourceClient client = mock(DataSourceClient.class);
        when(sourceModule.client()).thenReturn(client);
        when(client.isAvailable()).thenReturn(true);
        when(sourceModuleRegistry.allModules()).thenReturn(List.of(sourceModule));
        when(sourceSyncFlowService.syncCountry(eq(sourceModule), eq("US"), any(), any()))
                .thenReturn(SourceSyncResult.empty(DataSource.WORLD_BANK));

        assertThatThrownBy(() -> orchestrator.runCountrySync(context(OperationType.DATA_SYNC), "US"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No data found for US");
    }

    @Test
    void runCountryHintGeneration_invalidatesLensCachesAndRunsGenerator() {
        Country country = Country.builder().id(10L).isoAlpha2("US").isoAlpha3("USA").nameEn("United States").build();
        when(hintGeneratorService.generateAllHintsForCountry(any(), any(), any()))
                .thenReturn(new HintGenerationResult(4, 2, 1, 1, 3, Map.of(), Map.of()));

        orchestrator.runCountryHintGeneration(context(OperationType.HINT_GENERATION), country);

        verify(lensRegistry).invalidateAllCaches();
        verify(contextBuilder).refreshCache();
        verify(console).success("2 new, 1 updated for US (1 rejected)");
        verify(sourceSyncFlowService, never()).syncCountry(any(), any(), any(), any());
    }

    @Test
    void runComboHintGeneration_invalidatesOnceAndProcessesAllActiveCountries() {
        Country us = Country.builder().id(10L).isoAlpha2("US").build();
        Country jp = Country.builder().id(20L).isoAlpha2("JP").build();
        when(countryRepository.findAllActive()).thenReturn(List.of(us, jp));
        when(combinatoricsHintEngine.generateForCountry(us)).thenReturn(5);
        when(combinatoricsHintEngine.generateForCountry(jp)).thenReturn(3);

        OperationSummary summary = orchestrator.runComboHintGeneration(context(OperationType.HINT_GENERATION));

        verify(combinatoricsHintEngine).invalidateCache();
        verify(console).success("Combo hint generation completed: 2 countries, 8 hints");
        assertThat(summary.asMap()).containsEntry("comboHintsGenerated", 8);
    }

    private SourceModule module(DataSource source) {
        SourceModule module = mock(SourceModule.class);
        when(module.source()).thenReturn(source);
        return module;
    }

    private static AdminOperationContext context(OperationType type) {
        return new AdminOperationContext("op", OperationBatchLog.start(type, OperationTriggerType.MANUAL));
    }
}
