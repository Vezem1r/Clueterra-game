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
import io.clueterra.questions.entity.enums.SyncType;
import io.clueterra.questions.image.ContentRetirementService;
import io.clueterra.questions.repository.jdbc.DataSyncLogJdbcRepository;
import io.clueterra.questions.service.enrichment.dto.IndicatorKey;
import io.clueterra.questions.service.enrichment.StatisticEnrichmentService;
import io.clueterra.questions.service.hint.HintGeneratorService;
import io.clueterra.questions.service.hint.lens.StatisticContextBuilder;
import io.clueterra.questions.service.source.SourceModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceSyncFlowService {

    private final SourceSyncPersistenceService persistenceService;
    private final StatisticEnrichmentService enrichmentService;
    private final HintGeneratorService hintGeneratorService;
    private final DataSyncLogJdbcRepository syncLogRepository;
    private final SyncProgressReporter progressReporter;
    private final OsmEnricher osmEnricher;
    private final ContentRetirementService contentRetirementService;
    private final StatisticContextBuilder contextBuilder;

    private final AppProperties appProperties;

    public SourceSyncResult syncFull(SourceModule module, Long batchId, String operationId) {
        if (module.policy().enrichmentOnly()) {
            return syncOsmFull(module, batchId, operationId);
        }

        DataSourceClient client = module.client();
        DataSyncLog syncLog = syncLogRepository.save(DataSyncLog.start(module.source(), SyncType.FULL, batchId, null));
        try {
            progressReporter.info(operationId, "FETCH", module.source(), null, null, null,
                    "Fetching full source dataset");
            List<RawCountryData> rawData = client.fetchAllCountries((current, total, message) -> {
                progressReporter.info(operationId, "FETCH", module.source(), null, current, total, message);
            });
            List<FetchFailure> fetchFailures = attachFetchDetails(client, syncLog);

            SourceSyncResult result = syncFetchedData(module, rawData, syncLog, operationId, fetchFailures);
            syncLog.complete();
            syncLogRepository.update(syncLog);
            return result;
        } catch (Exception e) {
            syncLog.fail(e);
            syncLogRepository.update(syncLog);
            throw e;
        }
    }

    public SourceSyncResult syncCountry(SourceModule module, String countryCode, Long batchId, String operationId) {
        if (module.policy().enrichmentOnly()) {
            return syncOsmCountry(module, countryCode, batchId, operationId);
        }

        DataSourceClient client = module.client();
        DataSyncLog syncLog = syncLogRepository.save(DataSyncLog.start(module.source(), SyncType.INCREMENTAL, batchId, countryCode));
        try {
            progressReporter.info(operationId, "FETCH", module.source(), countryCode, null, null,
                    "Fetching source-country slice");
            Optional<RawCountryData> dataOpt = client.fetchCountryByCode(countryCode);
            List<FetchFailure> fetchFailures = attachFetchDetails(client, syncLog);

            if (dataOpt.isEmpty()) {
                syncLog.setErrorMessage("No data found for " + countryCode + " from " + module.source());
                syncLog.buildFetchDetailsJson();
                syncLog.complete();
                syncLogRepository.update(syncLog);
                return SourceSyncResult.empty(module.source(), fetchFailures);
            }

            SourceSyncResult result = syncFetchedData(
                    module, List.of(dataOpt.get()), syncLog, operationId, fetchFailures);
            syncLog.complete();
            syncLogRepository.update(syncLog);
            return result;
        } catch (Exception e) {
            syncLog.fail(e);
            syncLogRepository.update(syncLog);
            throw e;
        }
    }

    private SourceSyncResult syncFetchedData(SourceModule module,
                                             List<RawCountryData> rawData,
                                             DataSyncLog syncLog,
                                             String operationId,
                                             List<FetchFailure> initialFailures) {
        List<FetchFailure> failures = new ArrayList<>(initialFailures);
        if (rawData == null || rawData.isEmpty()) {
            log.info("[op={}] [{}] No data returned for sync", operationId, module.source());
            progressReporter.info(operationId, "FETCH", module.source(), null, 0, 0, "No data returned");
            syncLog.buildFetchDetailsJson();
            return SourceSyncResult.empty(module.source(), failures);
        }

        int countriesProcessed = 0;
        int statisticsSaved = 0;
        int staleDeleted = 0;
        int hintsGenerated = 0;
        int enrichedStatistics = 0;
        int totalCountries = rawData.size();
        List<Country> countriesNeedingHints = new ArrayList<>();
        Set<IndicatorKey> comparableKeys = new LinkedHashSet<>();
        StaleCleanupGuard cleanupGuard = StaleCleanupGuard.from(initialFailures);
        if (cleanupGuard.blocksAll()) {
            log.warn("[op={}] [{}] Source fetch was incomplete; stale statistics will be kept for this run",
                    operationId, module.source());
        }

        int chunkSize = Math.max(1, appProperties.getSync().getChunkSize());
        for (int offset = 0; offset < rawData.size(); offset += chunkSize) {
            List<RawCountryData> chunk = rawData.subList(offset, Math.min(offset + chunkSize, rawData.size()));
            progressReporter.info(operationId, "PROCESS", module.source(), null, offset + 1, totalCountries,
                    "Processing chunk of " + chunk.size() + " countries");

            for (RawCountryData rawCountryData : chunk) {
                String countryCode = rawCountryData.getPreferredIsoCode();
                try {
                    SlicePersistenceResult slice = persistenceService.persistSlice(
                            module, rawCountryData, cleanupGuard.allowsCleanup(rawCountryData));
                    if (slice.country() == null) {
                        continue;
                    }
                    contextBuilder.invalidateIndicatorCodes(slice.changedIndicatorCodes());
                    countriesProcessed++;
                    statisticsSaved += slice.statisticsSaved();
                    staleDeleted += slice.staleStatisticsDeleted();
                    countriesNeedingHints.add(slice.country());
                    comparableKeys.addAll(slice.comparableKeys());
                } catch (Exception e) {
                    recordCountryFailure(
                            failures, syncLog, operationId, module.source(), countryCode, "PERSIST", e);
                }
            }
        }

        if (module.policy().comparativeEnrichmentEnabled() && !comparableKeys.isEmpty()) {
            progressReporter.info(operationId, "COMPARE_ENRICH", module.source(), null, null, null,
                    "Enriching " + comparableKeys.size() + " changed indicator keys");
            enrichedStatistics += enrichmentService.enrichIndicators(comparableKeys);
        }

        module.invalidateLensCaches();
        progressReporter.info(operationId, "HINT_GENERATION", module.source(), null, null, totalCountries,
                "Generating hints after the complete source snapshot was saved");
        for (Country country : countriesNeedingHints) {
            try {
                hintsGenerated += hintGeneratorService.generateHintsForCountryAndSource(country, module.source());
            } catch (Exception e) {
                recordCountryFailure(failures, syncLog, operationId, module.source(),
                        country.getIsoAlpha2(), "HINT_GENERATION", e);
            }
        }

        if (countriesProcessed == 0) {
            log.warn("[op={}] [{}] Fetched {} raw country slices but persisted none. Check source raw snapshot validity.",
                    operationId, module.source(), totalCountries);
        }

        syncLog.addCountriesProcessed(countriesProcessed);
        syncLog.addStatisticsSaved(statisticsSaved);
        syncLog.setHintsGenerated(hintsGenerated);
        syncLog.buildFetchDetailsJson();

        if (module.policy().rerollsArchivedHints()) {
            contentRetirementService.rerollArchivedBudgetHints();
        }

        String completionMessage =
                "Sync completed: %d countries, %d stats, %d hints, %d stale stats removed, %d enriched, %d failures"
                        .formatted(countriesProcessed, statisticsSaved, hintsGenerated, staleDeleted,
                                enrichedStatistics, failures.size());
        if (failures.isEmpty()) {
            progressReporter.success(operationId, "COMPLETE", module.source(), null,
                    countriesProcessed, totalCountries, completionMessage);
        } else {
            progressReporter.error(operationId, "COMPLETE", module.source(), null,
                    countriesProcessed, totalCountries, completionMessage);
        }

        return new SourceSyncResult(module.source(), countriesProcessed, statisticsSaved, hintsGenerated,
                staleDeleted, enrichedStatistics, failures);
    }

    private SourceSyncResult syncOsmFull(SourceModule module, Long batchId, String operationId) {
        DataSyncLog syncLog = syncLogRepository.save(DataSyncLog.start(module.source(), SyncType.FULL, batchId, null));
        try {
            progressReporter.info(operationId, "COMPARE_ENRICH", module.source(), null, null, null,
                    "Running OSM targeted enrichment");
            int enriched = osmEnricher.enrich();
            syncLog.addStatisticsSaved(enriched);
            syncLog.complete();
            syncLogRepository.update(syncLog);
            log.info("[op={}] [{}] OSM sync completed: {} statistics enriched", operationId, module.source(), enriched);
            progressReporter.success(operationId, "COMPLETE", module.source(), null, null, null,
                    "OSM sync completed: " + enriched + " statistics enriched");
            return new SourceSyncResult(module.source(), 0, enriched, 0, 0, enriched);
        } catch (Exception e) {
            syncLog.fail(e);
            syncLogRepository.update(syncLog);
            throw e;
        }
    }

    private SourceSyncResult syncOsmCountry(SourceModule module, String countryCode, Long batchId, String operationId) {
        DataSyncLog syncLog = syncLogRepository.save(DataSyncLog.start(module.source(), SyncType.INCREMENTAL, batchId, countryCode));
        try {
            progressReporter.info(operationId, "COMPARE_ENRICH", module.source(), countryCode, null, null,
                    "Running OSM country-targeted enrichment");
            int enriched = osmEnricher.enrichCountry(countryCode);
            syncLog.addCountriesProcessed(1);
            syncLog.addStatisticsSaved(enriched);
            syncLog.complete();
            syncLogRepository.update(syncLog);
            log.info("[op={}] [{}] [{}] OSM country sync completed: {} statistics enriched",
                    operationId, module.source(), countryCode, enriched);
            progressReporter.success(operationId, "COMPLETE", module.source(), countryCode, 1, 1,
                    "OSM country sync completed: " + enriched + " statistics enriched");
            return new SourceSyncResult(module.source(), 1, enriched, 0, 0, enriched);
        } catch (Exception e) {
            syncLog.fail(e);
            syncLogRepository.update(syncLog);
            throw e;
        }
    }

    private List<FetchFailure> attachFetchDetails(DataSourceClient client, DataSyncLog syncLog) {
        FetchResult fetchResult = client.getLastFetchResult();
        if (fetchResult == null || fetchResult.failures().isEmpty()) {
            return List.of();
        }
        for (FetchFailure failure : fetchResult.failures()) {
            syncLog.addFetchError(failure.countryCode(), failure.feedType(), failure.error());
        }
        return List.copyOf(fetchResult.failures());
    }

    private void recordCountryFailure(List<FetchFailure> failures,
                                      DataSyncLog syncLog,
                                      String operationId,
                                      DataSource source,
                                      String countryCode,
                                      String stage,
                                      Exception exception) {
        String message = conciseErrorMessage(exception);
        FetchFailure failure = new FetchFailure(countryCode, stage, message);
        failures.add(failure);
        syncLog.addFetchError(countryCode, stage, message);
        progressReporter.error(operationId, stage, source, countryCode, null, null, message);
        log.error("[op={}] [{}] [{}] {} failed: {}",
                operationId, source, countryCode, stage, message, exception);
    }

    private String conciseErrorMessage(Exception exception) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(exception);
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getMessage();
        }
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }
}
