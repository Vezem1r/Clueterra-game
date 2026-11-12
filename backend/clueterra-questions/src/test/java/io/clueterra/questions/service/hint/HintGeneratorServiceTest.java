package io.clueterra.questions.service.hint;

import io.clueterra.questions.repository.HintUpsertAction;
import io.clueterra.questions.entity.HintGenerationTriggerType;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.HintGenerationLogRepository;
import io.clueterra.questions.repository.HintCoverageStats;
import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.deduplication.DeduplicationService;
import io.clueterra.questions.service.deduplication.dto.DeduplicationResult;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.CountryHintLens;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.LensRegistry;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import io.clueterra.questions.service.hint.lens.StatisticContextBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HintGeneratorServiceTest {

    @Test
    void isLensEligibleForIndicator_allowsOnlyOwningLensForClaimedIndicators() {
        HintGeneratorService service = newService();

        HintLens genericLens = lens("generic", LensType.GENERIC, Set.of());
        HintLens ownerLens = lens("manual-owner", LensType.SPECIALIZED_STATIC, Set.of("MANUAL_"));
        HintLens foreignLens = lens("geonames-owner", LensType.SPECIALIZED_STATIC, Set.of("GEO_"));

        assertFalse(service.isLensEligibleForIndicator(genericLens, DataSource.MANUAL, "MANUAL_NOBEL_LAUREATES", false, true));
        assertTrue(service.isLensEligibleForIndicator(ownerLens, DataSource.MANUAL, "MANUAL_NOBEL_LAUREATES", false, true));
        assertFalse(service.isLensEligibleForIndicator(foreignLens, DataSource.MANUAL, "MANUAL_NOBEL_LAUREATES", false, true));
    }

    @Test
    void isLensEligibleForIndicator_rejectsForeignSpecializedLensForUnclaimedIndicators() {
        HintGeneratorService service = newService();

        HintLens genericLens = lens("generic", LensType.GENERIC, Set.of());
        HintLens foreignLens = lens("manual-owner", LensType.SPECIALIZED_STATIC, Set.of("MANUAL_"));

        assertTrue(service.isLensEligibleForIndicator(genericLens, DataSource.CIA_FACTBOOK, "CIA_POPULATION", false, false));
        assertFalse(service.isLensEligibleForIndicator(foreignLens, DataSource.CIA_FACTBOOK, "CIA_POPULATION", false, false));
    }

    @Test
    void isLensEligibleForIndicator_respectsDynamicLensBoundary() {
        HintGeneratorService service = newService();

        HintLens staticOwner = lens("brands-static", LensType.SPECIALIZED_STATIC, Set.of("BRAND_"));
        HintLens dynamicOwner = lens("brands-dynamic", LensType.SPECIALIZED_DYNAMIC, Set.of("BRAND_"));

        assertFalse(service.isLensEligibleForIndicator(staticOwner, DataSource.GLOBAL_BRANDS, "BRAND_TOP_SONG", true, true));
        assertTrue(service.isLensEligibleForIndicator(dynamicOwner, DataSource.GLOBAL_BRANDS, "BRAND_TOP_SONG", true, true));
    }

    @Test
    void isLensEligibleForIndicator_supportsSourceScopedExactIndicatorOwnership() {
        HintGeneratorService service = newService();

        HintLens genericLens = lens("generic", LensType.GENERIC, Set.of());
        HintLens holidayLens = exactLens(
                "nager-date",
                LensType.SPECIALIZED_STATIC,
                Set.of("HOLIDAY_COUNT"),
                Set.of(DataSource.NAGER_DATE)
        );

        assertFalse(service.isLensEligibleForIndicator(genericLens, DataSource.NAGER_DATE, "HOLIDAY_COUNT", false, true));
        assertTrue(service.isLensEligibleForIndicator(holidayLens, DataSource.NAGER_DATE, "HOLIDAY_COUNT", false, true));
        assertFalse(service.isLensEligibleForIndicator(holidayLens, DataSource.CIA_FACTBOOK, "HOLIDAY_COUNT", false, true));
    }

    @Test
    void isLensEligibleForIndicator_supportsFullSourceOwnershipForNonPrefixedIndicators() {
        HintGeneratorService service = newService();

        HintLens genericLens = lens("generic", LensType.GENERIC, Set.of());
        HintLens worldBankLens = fullSourceLens("world-bank", LensType.SPECIALIZED_STATIC, DataSource.WORLD_BANK);

        assertFalse(service.isLensEligibleForIndicator(genericLens, DataSource.WORLD_BANK, "SP.POP.TOTL", false, true));
        assertTrue(service.isLensEligibleForIndicator(worldBankLens, DataSource.WORLD_BANK, "SP.POP.TOTL", false, true));
        assertFalse(service.isLensEligibleForIndicator(worldBankLens, DataSource.MANUAL, "SP.POP.TOTL", false, true));
    }

    @Test
    void isSourceOwnedCandidate_recognizesCountryLevelClaimedPrefixes() {
        CountryHintLens countryLens = mock(CountryHintLens.class);
        when(countryLens.claimedPrefixes()).thenReturn(Set.of("MANUAL_FLAG_IMAGE_"));

        HintGeneratorService service = new HintGeneratorService(
                mock(HintRepository.class),
                mock(StatisticRepository.class),
                mock(DeduplicationService.class),
                mock(LensRegistry.class),
                mock(StatisticContextBuilder.class),
                mock(HintGenerationLogRepository.class),
                mock(HintSanitizer.class),
                mock(HintLifecycleService.class),
                new HintFactory(mock(HintSanitizer.class), mock(DeduplicationService.class)),
                List.of(countryLens),
                Clock.systemUTC()
        );

        CandidateHint candidate = CandidateHint.builder()
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_FLAG_IMAGE_1")
                .build();

        assertTrue(service.isSourceOwnedCandidate(candidate));
    }

    @Test
    void applySourceOwnedLifecycle_archivesStaleHintsByExactKey() {
        HintRepository hintRepository = mock(HintRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        LensRegistry lensRegistry = mock(LensRegistry.class);
        HintSanitizer hintSanitizer = mock(HintSanitizer.class);
        HintLifecycleService hintLifecycleService = new HintLifecycleService(hintRepository, new HintFactory(hintSanitizer, deduplicationService));

        HintGeneratorService service = new HintGeneratorService(
                hintRepository,
                mock(StatisticRepository.class),
                deduplicationService,
                lensRegistry,
                mock(StatisticContextBuilder.class),
                mock(HintGenerationLogRepository.class),
                hintSanitizer,
                hintLifecycleService,
                new HintFactory(hintSanitizer, deduplicationService),
                List.of(),
                Clock.systemUTC()
        );

        Country country = Country.builder()
                .id(4001L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        CandidateHint fresh = CandidateHint.builder()
                .text("Fresh wiki hint")
                .type(HintType.CULTURAL)
                .category(Category.CULTURE)
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_NOBEL_LAUREATE_T_1")
                .sourceYear(2026)
                .estimatedDifficulty(20)
                .estimatedQuality(new BigDecimal("0.90"))
                .ambiguityCount(1)
                .lensName("manual")
                .build();

        when(lensRegistry.isClaimed(DataSource.MANUAL, "MANUAL_NOBEL_LAUREATE_T_1")).thenReturn(true);
        when(hintSanitizer.sanitize("Fresh wiki hint", country)).thenReturn("Fresh wiki hint");
        when(deduplicationService.generateContentHash("Fresh wiki hint")).thenReturn("hash-fresh");
        when(hintRepository.saveWithArchive(any(Hint.class))).thenReturn(HintUpsertAction.INSERTED);
        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.MANUAL)).thenReturn(List.of(
                hint(91L, Category.CULTURE, HintType.CULTURAL, "0.70", DataSource.MANUAL, "MANUAL_OLD_KEY"),
                hint(92L, Category.CULTURE, HintType.CULTURAL, "0.90", DataSource.MANUAL, "MANUAL_NOBEL_LAUREATE_T_1")
        ));

        SourceOwnedLifecycleResult result = service.applySourceOwnedLifecycle(
                country,
                List.of(fresh),
                Set.of(DataSource.MANUAL)
        );

        assertThat(result.newCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isZero();
        assertThat(result.accepted()).containsExactly(fresh);
        verify(hintRepository).archiveAndDeleteByIds(List.of(91L), "stale_source_regen");
    }

    @Test
    void generateHintsForCountryAndSource_supportsCountryLevelSourceOwnedHintsWithoutStatistics() {
        HintRepository hintRepository = mock(HintRepository.class);
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        LensRegistry lensRegistry = mock(LensRegistry.class);
        StatisticContextBuilder contextBuilder = mock(StatisticContextBuilder.class);
        HintGenerationLogRepository generationLogRepository = mock(HintGenerationLogRepository.class);
        HintSanitizer hintSanitizer = mock(HintSanitizer.class);
        CountryHintLens countryLens = mock(CountryHintLens.class);
        HintLifecycleService hintLifecycleService = new HintLifecycleService(hintRepository, new HintFactory(hintSanitizer, deduplicationService));

        HintGeneratorService service = new HintGeneratorService(
                hintRepository,
                statisticRepository,
                deduplicationService,
                lensRegistry,
                contextBuilder,
                generationLogRepository,
                hintSanitizer,
                hintLifecycleService,
                new HintFactory(hintSanitizer, deduplicationService),
                List.of(countryLens),
                Clock.systemUTC()
        );

        Country country = Country.builder()
                .id(4001L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        CandidateHint candidate = CandidateHint.builder()
                .text("Flag check")
                .type(HintType.IMAGE_FACT)
                .category(Category.CULTURE)
                .source(DataSource.MANUAL)
                .sourceIndicator("MANUAL_FLAG_IMAGE_1")
                .estimatedDifficulty(18)
                .estimatedQuality(new BigDecimal("0.86"))
                .ambiguityCount(1)
                .lensName("manual-country")
                .imageUrl("/api/assets/flags/us.svg")
                .originalImageUrl("/api/assets/flags/us.svg")
                .build();

        when(statisticRepository.findCurrentByCountryIdAndSource(country.getId(), DataSource.MANUAL)).thenReturn(List.of());
        when(countryLens.source()).thenReturn(DataSource.MANUAL);
        when(countryLens.canApply(country)).thenReturn(true);
        when(countryLens.shouldCleanup(country)).thenReturn(true);
        when(countryLens.generate(country)).thenReturn(List.of(candidate));
        when(countryLens.claimedPrefixes()).thenReturn(Set.of("MANUAL_FLAG_IMAGE_"));
        when(countryLens.cleanupSources()).thenReturn(Set.of(DataSource.MANUAL));
        when(hintSanitizer.sanitize(candidate.text(), country)).thenReturn(candidate.text());
        when(deduplicationService.generateContentHash(candidate.text())).thenReturn("hash-manual-flag");
        when(hintRepository.saveWithArchive(any(Hint.class))).thenReturn(HintUpsertAction.INSERTED);
        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.MANUAL)).thenReturn(List.of());

        int generated = service.generateHintsForCountryAndSource(country, DataSource.MANUAL);

        assertThat(generated).isEqualTo(1);
        verify(hintRepository).saveWithArchive(any(Hint.class));
        verify(hintRepository).findActiveByCountryIdAndSource(country.getId(), DataSource.MANUAL);
    }

    @Test
    void generateAllHintsForCountry_generatesCountryLevelHintsWithoutStatistics() {
        HintRepository hintRepository = mock(HintRepository.class);
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        LensRegistry lensRegistry = mock(LensRegistry.class);
        HintGenerationLogRepository generationLogRepository = mock(HintGenerationLogRepository.class);
        HintSanitizer hintSanitizer = mock(HintSanitizer.class);
        CountryHintLens countryLens = mock(CountryHintLens.class);
        HintLifecycleService hintLifecycleService = new HintLifecycleService(hintRepository, new HintFactory(hintSanitizer, deduplicationService));

        HintGeneratorService service = new HintGeneratorService(
                hintRepository,
                statisticRepository,
                deduplicationService,
                lensRegistry,
                mock(StatisticContextBuilder.class),
                generationLogRepository,
                hintSanitizer,
                hintLifecycleService,
                new HintFactory(hintSanitizer, deduplicationService),
                List.of(countryLens),
                Clock.systemUTC()
        );

        Country country = Country.builder()
                .id(4001L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();
        CandidateHint candidate = CandidateHint.builder()
                .text("Recognize this country outline")
                .type(HintType.IMAGE_FACT)
                .category(Category.GEOGRAPHY)
                .source(DataSource.NATURAL_EARTH)
                .sourceIndicator("NATURAL_EARTH_COUNTRY_OUTLINE_1")
                .estimatedDifficulty(16)
                .estimatedQuality(new BigDecimal("0.94"))
                .ambiguityCount(1)
                .lensName("natural-earth-country-outline")
                .imageUrl("natural-earth/us.svg")
                .build();

        when(statisticRepository.findCurrentByCountryId(country.getId())).thenReturn(List.of());
        when(countryLens.canApply(country)).thenReturn(true);
        when(countryLens.shouldCleanup(country)).thenReturn(true);
        when(countryLens.generate(country)).thenReturn(List.of(candidate));
        when(countryLens.claimedPrefixes()).thenReturn(Set.of("NATURAL_EARTH_COUNTRY_OUTLINE_"));
        when(countryLens.cleanupSources()).thenReturn(Set.of(DataSource.NATURAL_EARTH));
        when(deduplicationService.deduplicate(country.getId(), List.of()))
                .thenReturn(DeduplicationResult.of(List.of(), List.of()));
        when(hintSanitizer.sanitize(candidate.text(), country)).thenReturn(candidate.text());
        when(deduplicationService.generateContentHash(candidate.text())).thenReturn("hash-outline");
        when(deduplicationService.generateRevisionHash(candidate.text(), candidate.imageUrl()))
                .thenReturn("revision-outline");
        when(hintRepository.saveWithArchive(any(Hint.class))).thenReturn(HintUpsertAction.INSERTED);
        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.NATURAL_EARTH))
                .thenReturn(List.of());
        when(hintRepository.findActiveCoverageByCountryId(country.getId())).thenReturn(new HintCoverageStats(null, null, 0, 0));

        HintGenerationResult result = service.generateAllHintsForCountry(
                country,
                HintGenerationTriggerType.MANUAL,
                null
        );

        assertThat(result.hintsGenerated()).isEqualTo(1);
        assertThat(result.hintsNew()).isEqualTo(1);
        assertThat(result.statisticsProcessed()).isZero();
        verify(hintRepository).saveWithArchive(any(Hint.class));
    }

    @Test
    void generateHintsForCountryAndSource_archivesHintWhenCountryAssetIsRejected() {
        HintRepository hintRepository = mock(HintRepository.class);
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        CountryHintLens countryLens = mock(CountryHintLens.class);
        HintLifecycleService lifecycleService = new HintLifecycleService(hintRepository, new HintFactory(mock(HintSanitizer.class), mock(DeduplicationService.class)));
        HintGeneratorService service = new HintGeneratorService(
                hintRepository,
                statisticRepository,
                mock(DeduplicationService.class),
                mock(LensRegistry.class),
                mock(StatisticContextBuilder.class),
                mock(HintGenerationLogRepository.class),
                mock(HintSanitizer.class),
                lifecycleService,
                new HintFactory(mock(HintSanitizer.class), mock(DeduplicationService.class)),
                List.of(countryLens),
                Clock.systemUTC()
        );
        Country country = Country.builder().id(4002L).isoAlpha2("SJ").nameEn("Svalbard and Jan Mayen").build();
        Hint staleHint = Hint.builder()
                .id(91L)
                .source(DataSource.HYDRO_RIVERS)
                .sourceIndicator("HYDRORIVERS_RIVER_SKELETON_3")
                .category(Category.GEOGRAPHY)
                .hintType(HintType.IMAGE_FACT)
                .build();

        when(statisticRepository.findCurrentByCountryIdAndSource(country.getId(), DataSource.HYDRO_RIVERS))
                .thenReturn(List.of());
        when(countryLens.source()).thenReturn(DataSource.HYDRO_RIVERS);
        when(countryLens.canApply(country)).thenReturn(false);
        when(countryLens.shouldCleanup(country)).thenReturn(true);
        when(countryLens.claimedPrefixes()).thenReturn(Set.of("HYDRORIVERS_RIVER_SKELETON_"));
        when(countryLens.cleanupSources()).thenReturn(Set.of(DataSource.HYDRO_RIVERS));
        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.HYDRO_RIVERS))
                .thenReturn(List.of(staleHint));

        assertThat(service.generateHintsForCountryAndSource(country, DataSource.HYDRO_RIVERS)).isZero();
        verify(hintRepository).archiveAndDeleteByIds(List.of(91L), "stale_source_regen");
    }

    @Test
    void generateAllHintsForCountry_archivesRejectedCountryAssetHint() {
        HintRepository hintRepository = mock(HintRepository.class);
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        CountryHintLens countryLens = mock(CountryHintLens.class);
        HintGeneratorService service = new HintGeneratorService(
                hintRepository,
                statisticRepository,
                deduplicationService,
                mock(LensRegistry.class),
                mock(StatisticContextBuilder.class),
                mock(HintGenerationLogRepository.class),
                mock(HintSanitizer.class),
                new HintLifecycleService(hintRepository, new HintFactory(mock(HintSanitizer.class), deduplicationService)),
                new HintFactory(mock(HintSanitizer.class), deduplicationService),
                List.of(countryLens),
                Clock.systemUTC()
        );
        Country country = Country.builder().id(4003L).isoAlpha2("PR").nameEn("Puerto Rico").build();
        Hint staleHint = Hint.builder()
                .id(92L)
                .source(DataSource.HYDRO_RIVERS)
                .sourceIndicator("HYDRORIVERS_RIVER_SKELETON_3")
                .category(Category.GEOGRAPHY)
                .hintType(HintType.IMAGE_FACT)
                .build();

        when(statisticRepository.findCurrentByCountryId(country.getId())).thenReturn(List.of());
        when(countryLens.shouldCleanup(country)).thenReturn(true);
        when(countryLens.claimedPrefixes()).thenReturn(Set.of("HYDRORIVERS_RIVER_SKELETON_"));
        when(countryLens.cleanupSources()).thenReturn(Set.of(DataSource.HYDRO_RIVERS));
        when(deduplicationService.deduplicate(country.getId(), List.of()))
                .thenReturn(DeduplicationResult.of(List.of(), List.of()));
        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.HYDRO_RIVERS))
                .thenReturn(List.of(staleHint));
        when(hintRepository.findActiveCoverageByCountryId(country.getId())).thenReturn(new HintCoverageStats(null, null, 0, 0));

        service.generateAllHintsForCountry(country, HintGenerationTriggerType.MANUAL, null);

        verify(hintRepository).archiveAndDeleteByIds(List.of(92L), "stale_source_regen");
    }

    @Test
    void generateAllHintsForCountry_countsSourceOwnedHintsAsGenerated() {
        HintRepository hintRepository = mock(HintRepository.class);
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        DeduplicationService deduplicationService = mock(DeduplicationService.class);
        LensRegistry lensRegistry = mock(LensRegistry.class);
        StatisticContextBuilder contextBuilder = mock(StatisticContextBuilder.class);
        HintGenerationLogRepository generationLogRepository = mock(HintGenerationLogRepository.class);
        HintSanitizer hintSanitizer = mock(HintSanitizer.class);
        HintLens ownerLens = mock(HintLens.class);
        HintLifecycleService hintLifecycleService = new HintLifecycleService(hintRepository, new HintFactory(hintSanitizer, deduplicationService));

        HintGeneratorService service = new HintGeneratorService(
                hintRepository,
                statisticRepository,
                deduplicationService,
                lensRegistry,
                contextBuilder,
                generationLogRepository,
                hintSanitizer,
                hintLifecycleService,
                new HintFactory(hintSanitizer, deduplicationService),
                List.of(),
                Clock.systemUTC()
        );

        Country country = Country.builder()
                .id(4001L)
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();

        CountryStatistic statistic = CountryStatistic.builder()
                .countryId(4001L)
                .source(DataSource.WORLD_BANK)
                .indicatorCode("SP.POP.TOTL")
                .indicatorName("Population")
                .category(Category.DEMOGRAPHICS)
                .value(new BigDecimal("340000000"))
                .year(2026)
                .build();

        StatisticContext context = StatisticContext.builder()
                .country(country)
                .statistic(statistic)
                .totalCountriesWithData(150)
                .totalActiveCountries(180)
                .indicatorDisplayName("population")
                .build();

        CandidateHint candidate = CandidateHint.builder()
                .text("Population flex")
                .type(HintType.ABSOLUTE_FACT)
                .category(Category.DEMOGRAPHICS)
                .source(DataSource.WORLD_BANK)
                .sourceIndicator("WB_SUPERLATIVE_SP_POP_TOTL_1")
                .sourceYear(2026)
                .estimatedDifficulty(10)
                .estimatedQuality(new BigDecimal("0.95"))
                .ambiguityCount(1)
                .lensName("world-bank")
                .build();

        when(statisticRepository.findCurrentByCountryId(country.getId())).thenReturn(List.of(statistic));
        when(contextBuilder.buildMinimal(country, statistic)).thenReturn(context);
        when(lensRegistry.isDynamicIndicator("SP.POP.TOTL")).thenReturn(false);
        when(lensRegistry.isClaimed(DataSource.WORLD_BANK, "SP.POP.TOTL")).thenReturn(true);
        when(lensRegistry.isClaimed(DataSource.WORLD_BANK, "WB_SUPERLATIVE_SP_POP_TOTL_1")).thenReturn(true);
        when(lensRegistry.getAllLenses()).thenReturn(List.of(ownerLens));
        when(ownerLens.lensType()).thenReturn(LensType.SPECIALIZED_STATIC);
        when(ownerLens.ownsIndicator(DataSource.WORLD_BANK, "SP.POP.TOTL")).thenReturn(true);
        when(ownerLens.canApply(context)).thenReturn(true);
        when(ownerLens.generate(context)).thenReturn(List.of(candidate));
        when(deduplicationService.deduplicate(country.getId(), List.of())).thenReturn(DeduplicationResult.of(List.of(), List.of()));
        when(hintSanitizer.sanitize(candidate.text(), country)).thenReturn(candidate.text());
        when(deduplicationService.generateContentHash(candidate.text())).thenReturn("hash-population-flex");
        when(hintRepository.saveWithArchive(any(Hint.class))).thenReturn(HintUpsertAction.INSERTED);
        when(hintRepository.findActiveByCountryIdAndSource(country.getId(), DataSource.WORLD_BANK)).thenReturn(List.of());
        when(hintRepository.findActiveCoverageByCountryId(country.getId())).thenReturn(new HintCoverageStats(null, null, 0, 0));

        HintGenerationResult result = service.generateAllHintsForCountry(
                country,
                HintGenerationTriggerType.MANUAL,
                null
        );

        assertThat(result.hintsGenerated()).isEqualTo(1);
        assertThat(result.hintsNew()).isEqualTo(1);
        verify(hintRepository, times(1)).saveWithArchive(any(Hint.class));
    }


    private Hint hint(Long id, Category category, HintType type, String quality) {
        return hint(id, category, type, quality, DataSource.MANUAL, "TEST_" + id);
    }

    private Hint hint(Long id, Category category, HintType type, String quality, DataSource source, String sourceIndicator) {
        return Hint.builder()
                .id(id)
                .countryId(4001L)
                .category(category)
                .hintType(type)
                .hintText("hint-" + id)
                .difficulty(20)
                .qualityScore(new BigDecimal(quality))
                .source(source)
                .sourceIndicator(sourceIndicator)
                .contentHash("hash-" + id)
                .isActive(true)
                .build();
    }

    private HintGeneratorService newService() {
        return newService(mock(HintRepository.class));
    }

    private HintGeneratorService newService(HintRepository hintRepository) {
        return new HintGeneratorService(
                hintRepository,
                mock(StatisticRepository.class),
                mock(DeduplicationService.class),
                mock(LensRegistry.class),
                mock(StatisticContextBuilder.class),
                mock(HintGenerationLogRepository.class),
                mock(HintSanitizer.class),
                mock(HintLifecycleService.class),
                new HintFactory(mock(HintSanitizer.class), mock(DeduplicationService.class)),
                List.of(),
                Clock.systemUTC()
        );
    }

    @Test
    void generateHintsForCountryAndSource_failingLensDoesNotArchiveExistingHints() {
        HintRepository hintRepository = mock(HintRepository.class);
        StatisticRepository statisticRepository = mock(StatisticRepository.class);
        LensRegistry lensRegistry = mock(LensRegistry.class);
        StatisticContextBuilder contextBuilder = mock(StatisticContextBuilder.class);
        HintLifecycleService lifecycleService = mock(HintLifecycleService.class);
        HintLens failingLens = mock(HintLens.class);
        Country country = Country.builder().id(5L).isoAlpha2("DE").nameEn("Germany").build();
        CountryStatistic statistic = CountryStatistic.builder()
                .countryId(5L).source(DataSource.WORLD_BANK).indicatorCode("SP.POP.TOTL").build();

        when(statisticRepository.findCurrentByCountryIdAndSource(5L, DataSource.WORLD_BANK)).thenReturn(List.of(statistic));
        when(lensRegistry.isClaimed(DataSource.WORLD_BANK, "SP.POP.TOTL")).thenReturn(true);
        when(lensRegistry.getAllLenses()).thenReturn(List.of(failingLens));
        when(failingLens.lensType()).thenReturn(LensType.SPECIALIZED_STATIC);
        when(failingLens.ownsIndicator(DataSource.WORLD_BANK, "SP.POP.TOTL")).thenReturn(true);
        when(failingLens.canApply(any())).thenReturn(true);
        when(failingLens.generate(any())).thenThrow(new IllegalStateException("cache load failed"));

        HintGeneratorService service = new HintGeneratorService(
                hintRepository,
                statisticRepository,
                mock(DeduplicationService.class),
                lensRegistry,
                contextBuilder,
                mock(HintGenerationLogRepository.class),
                mock(HintSanitizer.class),
                lifecycleService,
                mock(HintFactory.class),
                List.of(),
                Clock.systemUTC());

        service.generateHintsForCountryAndSource(country, DataSource.WORLD_BANK);

        verify(lifecycleService).applySourceOwnedLifecycle(country, List.of(), Set.of());
    }

    private HintLens lens(String name, LensType type, Set<String> claimedPrefixes) {
        return new HintLens() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean canApply(StatisticContext context) {
                return true;
            }

            @Override
            public List<CandidateHint> generate(StatisticContext context) {
                return List.of();
            }

            @Override
            public LensType lensType() {
                return type;
            }

            @Override
            public Set<String> claimedPrefixes() {
                return claimedPrefixes;
            }
        };
    }

    private HintLens exactLens(String name, LensType type, Set<String> claimedIndicators, Set<DataSource> claimedSources) {
        return new HintLens() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean canApply(StatisticContext context) {
                return true;
            }

            @Override
            public List<CandidateHint> generate(StatisticContext context) {
                return List.of();
            }

            @Override
            public LensType lensType() {
                return type;
            }

            @Override
            public Set<String> claimedIndicators() {
                return claimedIndicators;
            }

            @Override
            public Set<DataSource> claimedSources() {
                return claimedSources;
            }
        };
    }

    private HintLens fullSourceLens(String name, LensType type, DataSource source) {
        return new HintLens() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean canApply(StatisticContext context) {
                return true;
            }

            @Override
            public List<CandidateHint> generate(StatisticContext context) {
                return List.of();
            }

            @Override
            public LensType lensType() {
                return type;
            }

            @Override
            public Set<DataSource> claimedSources() {
                return Set.of(source);
            }

            @Override
            public boolean ownsIndicator(DataSource indicatorSource, String indicatorCode) {
                return indicatorSource == source && indicatorCode != null && !indicatorCode.isBlank();
            }

            @Override
            public boolean hasOwnershipRules() {
                return true;
            }
        };
    }
}
