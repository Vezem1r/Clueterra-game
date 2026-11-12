package io.clueterra.questions.service.hint;

import io.clueterra.questions.entity.HintGenerationTriggerType;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.HintGenerationLog;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.HintCoverageStats;
import io.clueterra.questions.repository.HintGenerationLogRepository;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.HintSaveResult;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.deduplication.dto.DeduplicationResult;
import io.clueterra.questions.service.deduplication.DeduplicationService;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.CountryHintLens;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensRegistry;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import io.clueterra.questions.service.hint.lens.StatisticContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.util.StringUtils;

@Service
@Slf4j
public class HintGeneratorService {

    private final HintRepository hintRepository;
    private final StatisticRepository statisticRepository;
    private final DeduplicationService deduplicationService;
    private final LensRegistry lensRegistry;
    private final StatisticContextBuilder contextBuilder;
    private final HintGenerationLogRepository generationLogRepository;
    private final HintSanitizer hintSanitizer;
    private final HintLifecycleService hintLifecycleService;
    private final HintFactory hintFactory;
    private final List<CountryHintLens> countryHintLenses;
    private final Clock clock;

    public HintGeneratorService(
            HintRepository hintRepository,
            StatisticRepository statisticRepository,
            DeduplicationService deduplicationService,
            LensRegistry lensRegistry,
            StatisticContextBuilder contextBuilder,
            HintGenerationLogRepository generationLogRepository,
            HintSanitizer hintSanitizer,
            HintLifecycleService hintLifecycleService,
            HintFactory hintFactory,
            List<CountryHintLens> countryHintLenses,
            Clock clock) {
        this.hintRepository = hintRepository;
        this.statisticRepository = statisticRepository;
        this.deduplicationService = deduplicationService;
        this.lensRegistry = lensRegistry;
        this.contextBuilder = contextBuilder;
        this.generationLogRepository = generationLogRepository;
        this.hintSanitizer = hintSanitizer;
        this.hintLifecycleService = hintLifecycleService;
        this.hintFactory = hintFactory;
        this.countryHintLenses = countryHintLenses;
        this.clock = clock;
    }

    public HintGenerationResult generateAllHintsForCountry(
            Country country,
            HintGenerationTriggerType triggerType,
            Long batchId) {

        Instant startTime = Instant.now(clock);
        String countryCode = country.getIsoAlpha2();
        int hintsFailed = 0;
        Set<DataSource> failedSources = EnumSet.noneOf(DataSource.class);

        log.info("Generating hints for country: {} ({})", country.getNameEn(), countryCode);

        try {
            List<CountryStatistic> statistics = statisticRepository.findCurrentByCountryId(country.getId());
            if (statistics.isEmpty()) {
                log.info("No statistics found for {}; generating country-level hints only", countryCode);
            } else {
                log.info("Processing {} statistics for {}", statistics.size(), countryCode);
            }

            List<CandidateHint> allCandidates = new ArrayList<>();
            Map<String, Integer> lensGeneratedCounts = new HashMap<>();
            Map<String, CountryStatistic> imageMetadataByStoredUrl = buildImageMetadataMap(statistics);

            for (CountryStatistic statistic : statistics) {
                try {
                    List<CandidateHint> candidates = attachImageMetadata(
                            processStatistic(country, statistic),
                            imageMetadataByStoredUrl);
                    candidates.forEach(candidate -> lensGeneratedCounts.merge(lensName(candidate, "unknown"), 1, Integer::sum));
                    allCandidates.addAll(candidates);
                } catch (Exception e) {
                    hintsFailed++;
                    if (statistic.getSource() != null) {
                        failedSources.add(statistic.getSource());
                    }
                    log.warn("Error processing statistic {} for {}", statistic.getIndicatorCode(), countryCode, e);
                }
            }

            List<CandidateHint> countryLevelHints = generateCountryLevelHints(country, failedSources);
            countryLevelHints.forEach(candidate -> lensGeneratedCounts.merge(lensName(candidate, "country"), 1, Integer::sum));
            allCandidates.addAll(countryLevelHints);

            log.info("Generated {} candidate hints before deduplication", allCandidates.size());

            List<CandidateHint> sourceOwnedCandidates = new ArrayList<>();
            List<CandidateHint> genericCandidates = new ArrayList<>();
            for (CandidateHint c : allCandidates) {
                if (isSourceOwnedCandidate(c)) {
                    sourceOwnedCandidates.add(c);
                } else {
                    genericCandidates.add(c);
                }
            }

            DeduplicationResult deduplicationResult = deduplicationService.deduplicate(country.getId(), genericCandidates);

            Set<DataSource> sourcesToClean = EnumSet.noneOf(DataSource.class);
            sourcesToClean.addAll(findSourceOwnedSources(statistics, country));
            if (sourcesToClean.removeAll(failedSources)) {
                log.warn("Skipping stale hint cleanup for {} sources {} because their lenses failed",
                        countryCode, failedSources);
            }
            SourceOwnedLifecycleResult sourceOwnedResult = applySourceOwnedLifecycle(
                    country,
                    sourceOwnedCandidates,
                    sourcesToClean
            );

            Map<String, Integer> lensAcceptedCounts = new HashMap<>();
            deduplicationResult.accepted().forEach(candidate ->
                    lensAcceptedCounts.merge(lensName(candidate, "unknown"), 1, Integer::sum));
            sourceOwnedResult.accepted().forEach(candidate ->
                    lensAcceptedCounts.merge(lensName(candidate, "unknown"), 1, Integer::sum));

            int persistedHints = 0;
            int newCount = 0;
            int updatedCount = 0;
            for (CandidateHint candidate : deduplicationResult.accepted()) {
                Optional<HintSaveResult> saved = convertAndSave(country, candidate);
                if (saved.isPresent()) {
                    persistedHints++;
                    if (saved.get().isNew()) newCount++;
                    if (saved.get().isUpdated()) updatedCount++;
                }
            }
            persistedHints += sourceOwnedResult.accepted().size();
            newCount += sourceOwnedResult.newCount();
            updatedCount += sourceOwnedResult.updatedCount();

            int refreshedCount = refreshMetadata(country, deduplicationResult.toRefresh());
            updatedCount += refreshedCount;

            Map<String, Object> lensBreakdown = new HashMap<>();
            for (Map.Entry<String, Integer> generated : lensGeneratedCounts.entrySet()) {
                int accepted = lensAcceptedCounts.getOrDefault(generated.getKey(), 0);
                lensBreakdown.put(generated.getKey(), Map.of(
                        "generated", generated.getValue(),
                        "accepted", accepted,
                        "rejected", generated.getValue() - accepted));
            }

            log.info("Hints for {}: {} new, {} updated ({} refreshed), {} rejected, {} failed",
                    countryCode, newCount, updatedCount, refreshedCount, deduplicationResult.totalRejected(), hintsFailed);

            HintGenerationResult result = new HintGenerationResult(
                    persistedHints,
                    newCount,
                    updatedCount,
                    deduplicationResult.totalRejected(),
                    statistics.size(),
                    deduplicationResult.toLogDetails(),
                    lensBreakdown
            );

            saveGenerationLog(country, triggerType, batchId, startTime, result, hintsFailed, null);
            return result;

        } catch (Exception e) {
            log.error("Failed to generate hints for {}", countryCode, e);
            saveGenerationLog(country, triggerType, batchId, startTime, HintGenerationResult.empty(), hintsFailed, e.getMessage());
            throw e;
        }
    }

    private static String lensName(CandidateHint candidate, String fallback) {
        return candidate.lensName() != null ? candidate.lensName() : fallback;
    }

    private void saveGenerationLog(
            Country country,
            HintGenerationTriggerType triggerType,
            Long batchId,
            Instant startTime,
            HintGenerationResult result,
            int hintsFailed,
            String errorMessage) {

        Instant endTime = Instant.now(clock);
        HintCoverageStats coverage = hintRepository.findActiveCoverageByCountryId(country.getId());

        HintGenerationLog genLog = HintGenerationLog.builder()
                .countryId(country.getId())
                .batchId(batchId)
                .countryCode(country.getIsoAlpha2())
                .triggerType(triggerType)
                .hintsAttempted(result.hintsGenerated() + result.hintsRejected())
                .hintsCreated(result.hintsGenerated())
                .hintsDuplicates(result.hintsRejected())
                .hintsFailed(hintsFailed)
                .avgDifficulty(coverage.avgDifficulty())
                .avgQualityScore(coverage.avgQualityScore())
                .categoriesCovered(coverage.categoriesCovered())
                .hintTypesCovered(coverage.hintTypesCovered())
                .startedAt(startTime)
                .completedAt(endTime)
                .durationMs(Duration.between(startTime, endTime).toMillis())
                .errorMessage(errorMessage)
                .details(result.details())
                .lensBreakdown(result.lensBreakdown())
                .build();

        generationLogRepository.save(genLog);
    }

    private List<CandidateHint> processStatistic(Country country, CountryStatistic statistic) {
        List<CandidateHint> candidates = new ArrayList<>();

        String code = statistic.getIndicatorCode();
        boolean isDynamic = lensRegistry.isDynamicIndicator(code);

        StatisticContext context = contextBuilder.buildMinimal(country, statistic);
        boolean claimed = lensRegistry.isClaimed(statistic.getSource(), code);

        for (HintLens lens : lensRegistry.getAllLenses()) {
            if (!isLensEligibleForIndicator(lens, statistic.getSource(), code, isDynamic, claimed)) continue;
            if (lens.canApply(context)) {
                candidates.addAll(lens.generate(context));
            }
        }

        return candidates;
    }

    boolean isLensEligibleForIndicator(HintLens lens,
                                       DataSource source,
                                       String indicatorCode,
                                       boolean dynamicIndicator,
                                       boolean claimedIndicator) {
        if (dynamicIndicator && lens.lensType() != LensType.SPECIALIZED_DYNAMIC) {
            return false;
        }
        if (!dynamicIndicator && lens.lensType() == LensType.SPECIALIZED_DYNAMIC) {
            return false;
        }

        boolean lensOwnsIndicator = lens.ownsIndicator(source, indicatorCode);

        if (claimedIndicator) {
            return lensOwnsIndicator;
        }

        return !lens.hasOwnershipRules() || lensOwnsIndicator;
    }

    boolean isSourceOwnedCandidate(CandidateHint candidate) {
        if (candidate == null || candidate.sourceIndicator() == null) return false;
        if (lensRegistry.isClaimed(candidate.source(), candidate.sourceIndicator())) return true;
        return countryHintLenses.stream()
                .flatMap(lens -> lens.claimedPrefixes().stream())
                .anyMatch(candidate.sourceIndicator()::startsWith);
    }

    Set<DataSource> findSourceOwnedSources(List<CountryStatistic> statistics, Country country) {
        Set<DataSource> sources = EnumSet.noneOf(DataSource.class);
        for (CountryStatistic statistic : statistics) {
            if (statistic.getSource() != null
                    && lensRegistry.isClaimed(statistic.getSource(), statistic.getIndicatorCode())) {
                sources.add(statistic.getSource());
            }
        }
        for (CountryHintLens lens : countryHintLenses) {
            if (lens.shouldCleanup(country) && !lens.claimedPrefixes().isEmpty()) {
                lens.cleanupSources().stream().filter(Objects::nonNull).forEach(sources::add);
            }
        }
        return sources;
    }

    SourceOwnedLifecycleResult applySourceOwnedLifecycle(
            Country country,
            List<CandidateHint> candidates,
            Set<DataSource> sourcesToClean) {
        List<CandidateHint> sourceOwnedCandidates = candidates == null
                ? List.of()
                : candidates.stream().filter(this::isSourceOwnedCandidate).toList();
        return hintLifecycleService.applySourceOwnedLifecycle(country, sourceOwnedCandidates, sourcesToClean);
    }

    public int generateHintsForCountryAndSource(Country country, DataSource source) {
        List<CountryStatistic> stats = statisticRepository.findCurrentByCountryIdAndSource(
                country.getId(), source);
        Map<String, CountryStatistic> imageMetadataByStoredUrl = buildImageMetadataMap(stats);
        boolean sourceOwned = source == DataSource.BUDGET_PROJECTS || stats.stream()
                .anyMatch(stat -> lensRegistry.isClaimed(stat.getSource(), stat.getIndicatorCode()))
                || countryHintLenses.stream()
                .anyMatch(lens -> lens.source() == source && lens.shouldCleanup(country) && !lens.claimedPrefixes().isEmpty());

        List<CandidateHint> allCandidates = new ArrayList<>();
        boolean lensFailed = false;
        for (CountryStatistic stat : stats) {
            try {
                allCandidates.addAll(attachImageMetadata(processStatistic(country, stat), imageMetadataByStoredUrl));
            } catch (Exception e) {
                lensFailed = true;
                log.warn("Error processing stat {} for {} [{}]", stat.getIndicatorCode(), country.getIsoAlpha2(), source, e);
            }
        }
        for (CountryHintLens lens : countryHintLenses) {
            if (lens.source() != source || !lens.canApply(country)) {
                continue;
            }
            try {
                allCandidates.addAll(lens.generate(country));
            } catch (Exception e) {
                lensFailed = true;
                log.warn("Error processing country-level hints from {} for {} [{}]",
                        lens.name(), country.getIsoAlpha2(), source, e);
            }
        }
        sourceOwned = sourceOwned || allCandidates.stream().anyMatch(this::isSourceOwnedCandidate);

        Set<DataSource> cleanupSources = lensFailed ? Set.of() : findCountryLensCleanupSources(country, source);
        if (lensFailed) {
            log.warn("Skipping stale hint cleanup for {} [{}] because a lens failed", country.getIsoAlpha2(), source);
        }

        if (allCandidates.isEmpty()) {
            if (sourceOwned) {
                applySourceOwnedLifecycle(country, List.of(), cleanupSources);
            }
            return 0;
        }

        int newCount = 0;
        int updatedCount = 0;

        if (sourceOwned) {
            SourceOwnedLifecycleResult result = applySourceOwnedLifecycle(country, allCandidates, cleanupSources);
            newCount += result.newCount();
            updatedCount += result.updatedCount();
        } else {
            DeduplicationResult deduplicationResult = deduplicationService.deduplicate(country.getId(), allCandidates);
            for (CandidateHint candidate : deduplicationResult.accepted()) {
                Optional<HintSaveResult> saved = convertAndSave(country, candidate);
                if (saved.isPresent()) {
                    if (saved.get().isNew()) newCount++;
                    if (saved.get().isUpdated()) updatedCount++;
                }
            }
        }

        log.debug("Source hints for {} [{}]: {} new, {} updated", country.getIsoAlpha2(), source, newCount, updatedCount);
        return newCount + updatedCount;
    }

    private Set<DataSource> findCountryLensCleanupSources(Country country, DataSource source) {
        Set<DataSource> sources = EnumSet.noneOf(DataSource.class);
        if (source != null) {
            sources.add(source);
        }
        countryHintLenses.stream()
                .filter(lens -> lens.source() == source && lens.shouldCleanup(country))
                .flatMap(lens -> lens.cleanupSources().stream())
                .filter(Objects::nonNull)
                .forEach(sources::add);
        return sources;
    }

    private List<CandidateHint> generateCountryLevelHints(Country country, Set<DataSource> failedSources) {
        List<CandidateHint> hints = new ArrayList<>();
        for (CountryHintLens lens : countryHintLenses) {
            if (!lens.canApply(country)) {
                continue;
            }
            try {
                hints.addAll(lens.generate(country));
            } catch (Exception e) {
                if (lens.source() != null) {
                    failedSources.add(lens.source());
                }
                lens.cleanupSources().stream().filter(Objects::nonNull).forEach(failedSources::add);
                log.warn("Error generating country-level hints from {} for {}", lens.name(), country.getIsoAlpha2(), e);
            }
        }
        return hints;
    }

    private Optional<HintSaveResult> convertAndSave(Country country, CandidateHint candidate) {
        return hintFactory.fromCandidate(country, candidate).map(hintRepository::save);
    }

    private int refreshMetadata(Country country, List<CandidateHint> toRefresh) {
        int count = 0;
        for (CandidateHint candidate : toRefresh) {
            String sanitizedText = hintSanitizer.sanitize(candidate.text(), country);
            if (sanitizedText == null) {
                continue;
            }

            String contentHash = deduplicationService.generateContentHash(sanitizedText);
            if (contentHash != null) {
                int rows = hintRepository.refreshMetadata(
                        country.getId(), contentHash, sanitizedText,
                        candidate.ambiguityCount(),
                        candidate.estimatedDifficulty(),
                        candidate.estimatedQuality());
                if (rows > 0) count++;
            }
        }
        return count;
    }

    private Map<String, CountryStatistic> buildImageMetadataMap(List<CountryStatistic> statistics) {
        if (statistics == null || statistics.isEmpty()) {
            return Map.of();
        }

        Map<String, CountryStatistic> mapping = new HashMap<>();
        for (CountryStatistic statistic : statistics) {
            if (!StringUtils.hasText(statistic.getImageUrl()) || mapping.containsKey(statistic.getImageUrl())) {
                continue;
            }
            if (StringUtils.hasText(statistic.getOriginalImageUrl())
                    || StringUtils.hasText(statistic.getImageSourcePageUrl())
                    || StringUtils.hasText(statistic.getImageAuthorName())
                    || StringUtils.hasText(statistic.getImageAttributionText())
                    || StringUtils.hasText(statistic.getImageLicenseLabel())
                    || StringUtils.hasText(statistic.getImageLicenseUrl())
                    || isRemoteUrl(statistic.getImageUrl())) {
                mapping.put(statistic.getImageUrl(), statistic);
            }
        }
        return mapping;
    }

    private List<CandidateHint> attachImageMetadata(List<CandidateHint> candidates,
                                                    Map<String, CountryStatistic> imageMetadataByStoredUrl) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> attachImageMetadata(candidate, imageMetadataByStoredUrl))
                .toList();
    }

    private CandidateHint attachImageMetadata(CandidateHint candidate,
                                              Map<String, CountryStatistic> imageMetadataByStoredUrl) {
        if (candidate == null || !StringUtils.hasText(candidate.imageUrl())) {
            return candidate;
        }
        CountryStatistic statistic = imageMetadataByStoredUrl.get(candidate.imageUrl());

        String originalImageUrl = candidate.originalImageUrl();
        String imageSourcePageUrl = candidate.imageSourcePageUrl();
        String imageAuthorName = candidate.imageAuthorName();
        String imageAttributionText = candidate.imageAttributionText();
        String imageLicenseLabel = candidate.imageLicenseLabel();
        String imageLicenseUrl = candidate.imageLicenseUrl();

        if (statistic != null) {
            originalImageUrl = firstText(originalImageUrl, statistic.getOriginalImageUrl());
            imageSourcePageUrl = firstText(imageSourcePageUrl, statistic.getImageSourcePageUrl());
            imageAuthorName = firstText(imageAuthorName, statistic.getImageAuthorName());
            imageAttributionText = firstText(imageAttributionText, statistic.getImageAttributionText());
            imageLicenseLabel = firstText(imageLicenseLabel, statistic.getImageLicenseLabel());
            imageLicenseUrl = firstText(imageLicenseUrl, statistic.getImageLicenseUrl());
        }
        if (!StringUtils.hasText(originalImageUrl) && isRemoteUrl(candidate.imageUrl())) {
            originalImageUrl = candidate.imageUrl();
        }
        return candidate.withImageMetadata(
                originalImageUrl,
                imageSourcePageUrl,
                imageAuthorName,
                imageAttributionText,
                imageLicenseLabel,
                imageLicenseUrl);
    }

    private static String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private boolean isRemoteUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }
}
