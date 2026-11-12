package io.clueterra.questions.service.sync;

import io.clueterra.questions.entity.HintGenerationTriggerType;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.OperationBatchLog;
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
import io.clueterra.questions.service.operation.AdminOperationSpec;
import io.clueterra.questions.service.operation.OperationSummary;
import io.clueterra.questions.service.source.SourceModule;
import io.clueterra.questions.service.source.SourceModuleRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncOrchestrator {

    private final CountryJdbcRepository countryRepository;
    private final HintGeneratorService hintGeneratorService;
    private final CombinatoricsHintEngine combinatoricsHintEngine;
    private final ConsoleLogWriter console;
    private final LensRegistry lensRegistry;
    private final StatisticContextBuilder contextBuilder;
    private final SourceModuleRegistry sourceModuleRegistry;
    private final SourceSyncFlowService sourceSyncFlowService;
    private final OperationBatchLogRepository batchLogRepository;
    private final AdminOperationRunner operationRunner;

    public String startFullSync() {
        return operationRunner.launch(
                new AdminOperationSpec("full_sync", "Full Sync", OperationType.DATA_SYNC, "FULL_SYNC"),
                this::runFullSync);
    }

    public String startSourceSync(DataSource source) {
        SourceModule module = requireSyncSupported(source);
        return operationRunner.launch(
                new AdminOperationSpec("source_sync", "Source Sync: " + source.getDisplayName(),
                        OperationType.DATA_SYNC, "SOURCE_SYNC"),
                context -> runSourceSync(context, module));
    }

    public String startCountrySync(String countryCode) {
        String code = normalize(countryCode);
        return operationRunner.launch(
                new AdminOperationSpec("country_sync", "Sync " + code, OperationType.DATA_SYNC, "COUNTRY_SYNC"),
                context -> runCountrySync(context, code));
    }

    public String startCountrySourceSync(DataSource source, String countryCode) {
        SourceModule module = requireSyncSupported(source);
        String code = normalize(countryCode);
        return operationRunner.launch(
                new AdminOperationSpec("country_source_sync", "Sync " + code + " from " + source.getDisplayName(),
                        OperationType.DATA_SYNC, "COUNTRY_SOURCE_SYNC"),
                context -> runCountrySourceSync(context, module, code));
    }

    public String startCountryHintGeneration(String countryCode) {
        Country country = requireCountry(countryCode);
        return operationRunner.launch(
                new AdminOperationSpec("hints_country", "Generate Hints: " + country.getIsoAlpha2(),
                        OperationType.HINT_GENERATION, "COUNTRY_HINTS"),
                context -> runCountryHintGeneration(context, country));
    }

    public String startSourceHintGeneration(String sourceName) {
        DataSource source = DataSource.valueOf(sourceName.toUpperCase(Locale.ROOT));
        if (!source.isHintGenerationSupported()) {
            throw new IllegalArgumentException("Source does not support hint generation: " + source);
        }
        return operationRunner.launch(
                new AdminOperationSpec("hints_source", "Generate Hints: " + source.getDisplayName(),
                        OperationType.HINT_GENERATION, "SOURCE_HINTS"),
                context -> runSourceHintGeneration(context, source));
    }

    public String startHintGeneration() {
        return operationRunner.launch(
                new AdminOperationSpec("hint_generation", "Hint Generation", OperationType.HINT_GENERATION, "ALL_HINTS"),
                this::runHintGeneration);
    }

    public String startComboHintGeneration() {
        return operationRunner.launch(
                new AdminOperationSpec("combo_hint_generation", "Combo Hint Generation",
                        OperationType.HINT_GENERATION, "ALL_COMBO_HINTS"),
                this::runComboHintGeneration);
    }

    OperationSummary runFullSync(AdminOperationContext context) {
        OperationBatchLog batchLog = context.batchLog();
        List<SourceModule> modules = availableModules();
        batchLog.setTotalItemsCount(modules.size());
        batchLogRepository.update(batchLog);
        console.system("Starting full data sync...");

        List<SourceSyncResult> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (SourceModule module : modules) {
            try {
                SourceSyncResult result = sourceSyncFlowService.syncFull(module, batchLog.getId(), context.operationId());
                recordSyncOutcome(batchLog, result, errors);
                results.add(result);
            } catch (Exception e) {
                batchLog.incrementFailed();
                errors.add(module.source().name() + ": " + e.getMessage());
                console.error("Source sync failed for " + module.source().name() + ": " + e.getMessage());
                log.warn("[op={}] Source sync failed for {}", context.operationId(), module.source(), e);
            }
        }

        int countries = sum(results, SourceSyncResult::countriesProcessed);
        int statistics = sum(results, SourceSyncResult::statisticsSaved);
        int hints = sum(results, SourceSyncResult::hintsGenerated);
        console.success("Full sync completed: " + countries + " countries, " + statistics + " stats, " + hints + " hints");
        return OperationSummary.of("sourcesProcessed", batchLog.getSuccessfulItems())
                .and("sourcesFailed", batchLog.getFailedItems())
                .and("countriesProcessed", countries)
                .and("statisticsSaved", statistics)
                .and("hintsGenerated", hints)
                .and("staleStatisticsDeleted", sum(results, SourceSyncResult::staleStatisticsDeleted))
                .and("enrichedStatistics", sum(results, SourceSyncResult::enrichedStatistics))
                .and("errors", errors);
    }

    OperationSummary runSourceSync(AdminOperationContext context, SourceModule module) {
        OperationBatchLog batchLog = context.batchLog();
        batchLog.setTotalItemsCount(1);
        SourceSyncResult result = sourceSyncFlowService.syncFull(module, batchLog.getId(), context.operationId());
        List<String> errors = new ArrayList<>();
        recordSyncOutcome(batchLog, result, errors);
        return resultSummary(result).and("source", module.source().name()).and("errors", errors);
    }

    OperationSummary runCountrySync(AdminOperationContext context, String countryCode) {
        OperationBatchLog batchLog = context.batchLog();
        List<SourceModule> modules = availableModules();
        batchLog.setTotalItemsCount(modules.size());
        batchLogRepository.update(batchLog);

        List<SourceSyncResult> results = new ArrayList<>();
        List<String> sourcesUsed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (SourceModule module : modules) {
            try {
                SourceSyncResult result = sourceSyncFlowService.syncCountry(
                        module, countryCode, batchLog.getId(), context.operationId());
                if (result.countriesProcessed() > 0) {
                    sourcesUsed.add(module.source().name());
                    results.add(result);
                }
                recordSyncOutcome(batchLog, result, errors);
            } catch (Exception e) {
                batchLog.incrementFailed();
                errors.add(module.source().name() + ": " + e.getMessage());
                console.error("Country sync failed for " + module.source().name() + ": " + e.getMessage());
                log.warn("[op={}] Country sync failed for {} from {}", context.operationId(), countryCode, module.source(), e);
            }
        }

        if (sourcesUsed.isEmpty()) {
            throw new ResourceNotFoundException("No data found for " + countryCode + " from any source"
                    + (errors.isEmpty() ? "" : "; errors: " + String.join("; ", errors)));
        }

        int statistics = sum(results, SourceSyncResult::statisticsSaved);
        int hints = sum(results, SourceSyncResult::hintsGenerated);
        console.success("Synced " + countryCode + ": " + statistics + " stats, " + hints
                + " hints from " + String.join(", ", sourcesUsed));
        return OperationSummary.of("countryCode", countryCode)
                .and("sourcesUsed", sourcesUsed)
                .and("statisticsSaved", statistics)
                .and("hintsGenerated", hints)
                .and("staleStatisticsDeleted", sum(results, SourceSyncResult::staleStatisticsDeleted))
                .and("enrichedStatistics", sum(results, SourceSyncResult::enrichedStatistics))
                .and("errors", errors);
    }

    OperationSummary runCountrySourceSync(AdminOperationContext context, SourceModule module, String countryCode) {
        OperationBatchLog batchLog = context.batchLog();
        batchLog.setTotalItemsCount(1);
        SourceSyncResult result = sourceSyncFlowService.syncCountry(
                module, countryCode, batchLog.getId(), context.operationId());
        List<String> errors = new ArrayList<>();
        recordSyncOutcome(batchLog, result, errors);
        String sourceName = module.source().getDisplayName();
        if (result.hasFailures()) {
            console.error("Synced " + countryCode + " from " + sourceName + " with " + result.failures().size() + " error(s)");
        } else {
            console.success("Synced " + countryCode + " from " + sourceName + ": "
                    + result.statisticsSaved() + " stats, " + result.hintsGenerated() + " hints");
        }
        return resultSummary(result)
                .and("source", module.source().name())
                .and("countryCode", countryCode)
                .and("errors", errors);
    }

    OperationSummary runCountryHintGeneration(AdminOperationContext context, Country country) {
        lensRegistry.invalidateAllCaches();
        contextBuilder.refreshCache();
        OperationBatchLog batchLog = context.batchLog();
        batchLog.setTotalItemsCount(1);

        HintGenerationResult result = hintGeneratorService.generateAllHintsForCountry(
                country, HintGenerationTriggerType.MANUAL, batchLog.getId());
        batchLog.incrementSuccess();

        console.success(result.hintsNew() + " new, " + result.hintsUpdated() + " updated for " + country.getIsoAlpha2()
                + " (" + result.hintsRejected() + " rejected)");
        return OperationSummary.of("countryCode", country.getIsoAlpha2())
                .and("hintsGenerated", result.hintsGenerated())
                .and("hintsNew", result.hintsNew())
                .and("hintsUpdated", result.hintsUpdated())
                .and("hintsRejected", result.hintsRejected());
    }

    OperationSummary runSourceHintGeneration(AdminOperationContext context, DataSource source) {
        lensRegistry.invalidateAllCaches();
        contextBuilder.refreshCache();
        PerCountryOutcome outcome = forEachActiveCountry(context, "Hints",
                country -> hintGeneratorService.generateHintsForCountryAndSource(country, source));
        console.success(source + " hint generation completed: " + outcome.processed() + " countries, "
                + outcome.total() + " hints");
        return outcome.toSummary("hintsGenerated").and("source", source.name());
    }

    OperationSummary runHintGeneration(AdminOperationContext context) {
        lensRegistry.invalidateAllCaches();
        contextBuilder.refreshCache();
        List<HintGenerationResult> results = new ArrayList<>();
        PerCountryOutcome outcome = forEachActiveCountry(context, "Hints", country -> {
            HintGenerationResult result = hintGeneratorService.generateAllHintsForCountry(
                    country, HintGenerationTriggerType.MANUAL, context.batchLog().getId());
            results.add(result);
            return result.hintsNew();
        });
        int updated = results.stream().mapToInt(HintGenerationResult::hintsUpdated).sum();
        console.success("Hint generation completed: " + outcome.processed() + " countries, " + outcome.total()
                + " new, " + updated + " updated");
        return outcome.toSummary("hintsNew").and("hintsUpdated", updated);
    }

    OperationSummary runComboHintGeneration(AdminOperationContext context) {
        combinatoricsHintEngine.invalidateCache();
        PerCountryOutcome outcome = forEachActiveCountry(context, "Combo hints", combinatoricsHintEngine::generateForCountry);
        console.success("Combo hint generation completed: " + outcome.processed() + " countries, "
                + outcome.total() + " hints");
        return outcome.toSummary("comboHintsGenerated");
    }

    private PerCountryOutcome forEachActiveCountry(AdminOperationContext context, String label,
                                                   ToIntFunction<Country> action) {
        OperationBatchLog batchLog = context.batchLog();
        List<Country> countries = countryRepository.findAllActive();
        batchLog.setTotalItemsCount(countries.size());
        batchLogRepository.update(batchLog);

        int total = 0;
        int processed = 0;
        List<String> errors = new ArrayList<>();
        for (Country country : countries) {
            try {
                total += action.applyAsInt(country);
                processed++;
                batchLog.incrementSuccess();
            } catch (Exception e) {
                batchLog.incrementFailed();
                String errorMessage = AdminOperationRunner.conciseErrorMessage(e);
                errors.add(country.getIsoAlpha2() + ": " + errorMessage);
                log.error("[op={}] {} failed for {}: {}", context.operationId(), label, country.getIsoAlpha2(), errorMessage, e);
                console.error(label + " failed for " + country.getIsoAlpha2() + ": " + errorMessage);
            }
        }
        return new PerCountryOutcome(processed, errors, total);
    }

    private List<SourceModule> availableModules() {
        return sourceModuleRegistry.allModules().stream()
                .filter(module -> module.client().isAvailable())
                .toList();
    }

    private SourceModule requireSyncSupported(DataSource source) {
        if (source == null || !source.isSyncSupported()) {
            throw new IllegalArgumentException("Source does not support synchronization: " + source);
        }
        return sourceModuleRegistry.require(source);
    }

    private Country requireCountry(String countryCode) {
        String code = normalize(countryCode);
        return countryRepository.findByIsoAlpha2(code)
                .or(() -> countryRepository.findByIsoAlpha3(code))
                .orElseThrow(() -> new ResourceNotFoundException("Country not found: " + code));
    }

    private static String normalize(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("Country code is required");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private static OperationSummary resultSummary(SourceSyncResult result) {
        return OperationSummary.of("countriesProcessed", result.countriesProcessed())
                .and("statisticsSaved", result.statisticsSaved())
                .and("hintsGenerated", result.hintsGenerated())
                .and("staleStatisticsDeleted", result.staleStatisticsDeleted())
                .and("enrichedStatistics", result.enrichedStatistics());
    }

    private static int sum(List<SourceSyncResult> results, ToIntFunction<SourceSyncResult> metric) {
        return results.stream().mapToInt(metric).sum();
    }

    private void recordSyncOutcome(OperationBatchLog batchLog, SourceSyncResult result, List<String> errors) {
        if (!result.hasFailures()) {
            batchLog.incrementSuccess();
            return;
        }
        batchLog.incrementFailed();
        result.failures().stream()
                .map(failure -> result.source().name() + " [" + failure.countryCode() + "/"
                        + failure.feedType() + "]: " + failure.error())
                .forEach(errors::add);
    }

}
