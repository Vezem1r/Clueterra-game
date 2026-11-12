package io.clueterra.questions.service.enrichment;

import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.service.enrichment.dto.IndicatorKey;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.clueterra.questions.service.operation.AdminOperationRunner;
import io.clueterra.questions.service.operation.AdminOperationSpec;
import io.clueterra.questions.service.operation.OperationSummary;
import io.clueterra.questions.service.hint.lens.LensRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StatisticEnrichmentService {

    private final StatisticRepository statisticRepository;
    private final CountryJdbcRepository countryRepository;
    private final ConsoleLogWriter console;
    private final AdminOperationRunner operationRunner;
    private final LensRegistry lensRegistry;

    public StatisticEnrichmentService(
            StatisticRepository statisticRepository,
            CountryJdbcRepository countryRepository,
            ConsoleLogWriter console,
            AdminOperationRunner operationRunner,
            LensRegistry lensRegistry) {
        this.statisticRepository = statisticRepository;
        this.countryRepository = countryRepository;
        this.console = console;
        this.operationRunner = operationRunner;
        this.lensRegistry = lensRegistry;
    }

    private static final Set<String> LOWER_IS_BETTER = Set.of(
            "SL.UEM.TOTL.ZS", "SH.DYN.MORT", "FP.CPI.TOTL.ZG", "SI.POV.GINI",
            "EN.ATM.CO2E.PC", "EN.ATM.CO2E.KT", "SP.DYN.CDRT.IN", "UNEMPLOYMENT",
            "INFANT_MORTALITY", "GINI", "CO2_EMISSIONS", "INFLATION_2024",
            "INFLATION_2023", "PUBLIC_DEBT", "GC.DOD.TOTL.GD.ZS", "YOUTH_UNEMPLOYMENT",
            "INFANT_MORTALITY_RATE", "MATERNAL_MORTALITY", "DEATH_RATE", "OBESITY_RATE",
            "TOBACCO_USE_TOTAL", "SP.DYN.AMRT.FE", "SP.DYN.AMRT.MA", "ILLICIT_DRUGS",
            "POVERTY_RATE", "INTERNALLY_DISPLACED", "EN.ATM.PM25.MC.M3"
    );

    public int enrichIndicators(Set<IndicatorKey> indicatorKeys) {
        if (indicatorKeys == null || indicatorKeys.isEmpty()) {
            return 0;
        }

        int statisticsEnriched = 0;
        int errors = 0;

        for (IndicatorKey key : indicatorKeys) {
            try {
                statisticsEnriched += enrichIndicator(key.source(), key.indicatorCode(), key.year());
            } catch (Exception e) {
                errors++;
                log.warn("Error enriching {}/{}", key.source(), key.indicatorCode(), e);
            }
        }

        if (errors > 0) {
            log.warn("Targeted enrichment completed with {} errors across {} keys", errors, indicatorKeys.size());
        }
        return statisticsEnriched;
    }

    public Set<IndicatorKey> collectComparableIndicatorKeys(Collection<CountryStatistic> statistics) {
        if (statistics == null || statistics.isEmpty()) {
            return Set.of();
        }

        return statistics.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getIsCurrent() == null || s.getIsCurrent())
                .filter(s -> s.getSource() != null)
                .filter(s -> StringUtils.hasText(s.getIndicatorCode()))
                .filter(s -> !Boolean.TRUE.equals(s.getIsDynamic()))
                .filter(s -> !lensRegistry.isDynamicIndicator(s.getIndicatorCode()))
                .map(s -> new IndicatorKey(s.getSource(), s.getIndicatorCode(), s.getYear()))
                .collect(Collectors.toSet());
    }

    public int enrichIndicator(DataSource source, String indicatorCode, Integer year) {
        Map<Long, String> countryRegions = countryRepository.findAllCountryRegions();
        List<CountryStatistic> statistics = statisticRepository.findBySourceAndIndicatorCode(source, indicatorCode);

        if (statistics.isEmpty()) {
            return 0;
        }

        if (year != null) {
            statistics = statistics.stream().filter(s -> year.equals(s.getYear())).toList();
        }

        List<CountryStatistic> validStatistics = statistics.stream()
                .filter(s -> s.getIsCurrent() != null && s.getIsCurrent())
                .filter(s -> s.getValue() != null)
                .collect(Collectors.toList());

        if (validStatistics.size() < 2) {
            return 0;
        }

        boolean lowerIsBetter = isLowerBetter(indicatorCode);

        double mean = validStatistics.stream()
                .mapToDouble(s -> s.getValue().doubleValue())
                .average().orElse(0.0);

        double variance = validStatistics.stream()
                .mapToDouble(s -> Math.pow(s.getValue().doubleValue() - mean, 2))
                .average().orElse(0.0);

        double stdDev = Math.sqrt(variance);

        Comparator<CountryStatistic> comparator = Comparator.comparing(CountryStatistic::getValue);
        if (!lowerIsBetter) {
            comparator = comparator.reversed();
        }

        validStatistics.sort(comparator);

        Map<String, List<CountryStatistic>> byRegion = validStatistics.stream()
                .filter(s -> countryRegions.containsKey(s.getCountryId()))
                .collect(Collectors.groupingBy(s -> countryRegions.get(s.getCountryId())));

        Map<Long, Integer> regionalRanks = new HashMap<>();
        for (List<CountryStatistic> regionStats : byRegion.values()) {
            regionStats.sort(comparator);
            int currentRegRank = 1;
            for (int i = 0; i < regionStats.size(); i++) {
                if (i > 0 && regionStats.get(i).getValue().compareTo(regionStats.get(i - 1).getValue()) != 0) {
                    currentRegRank = i + 1;
                }
                regionalRanks.put(regionStats.get(i).getId(), currentRegRank);
            }
        }

        int totalCount = validStatistics.size();
        int currentGlobalRank = 1;

        for (int i = 0; i < totalCount; i++) {
            CountryStatistic stat = validStatistics.get(i);

            if (i > 0 && stat.getValue().compareTo(validStatistics.get(i - 1).getValue()) != 0) {
                currentGlobalRank = i + 1;
            }

            stat.setGlobalRank(currentGlobalRank);

            double percentile = ((double) (totalCount - currentGlobalRank) / totalCount) * 100.0;
            stat.setPercentile(BigDecimal.valueOf(percentile).setScale(2, RoundingMode.HALF_UP));

            Integer regRank = regionalRanks.get(stat.getId());
            stat.setRegionalRank(regRank);

            if (stdDev > 0) {
                double rawZScore = (stat.getValue().doubleValue() - mean) / stdDev;
                stat.setDeviationScore(BigDecimal.valueOf(rawZScore).round(new MathContext(4, RoundingMode.HALF_UP)));
            } else {
                stat.setDeviationScore(BigDecimal.ZERO);
            }
        }

        statisticRepository.batchUpdateEnrichment(validStatistics);

        return validStatistics.size();
    }

    private Set<IndicatorKey> getUniqueIndicators() {
        List<CountryStatistic> allStats = statisticRepository.findAll();
        return collectComparableIndicatorKeys(allStats);
    }

    private boolean isLowerBetter(String indicatorCode) {
        if (!StringUtils.hasText(indicatorCode)) {
            return false;
        }

        if (LOWER_IS_BETTER.contains(indicatorCode)) {
            return true;
        }

        String upper = indicatorCode.toUpperCase();
        return upper.contains("MORTALITY") ||
                upper.contains("UNEMPLOYMENT") ||
                upper.contains("DEATH") ||
                upper.contains("POVERTY") ||
                upper.contains("GINI") ||
                upper.contains("INFLATION") ||
                upper.contains("DEBT") ||
                upper.contains("DEFICIT");
    }

    public String startEnrichment() {
        return operationRunner.launch(
                new AdminOperationSpec("enrich", "Enrichment", OperationType.DATA_SYNC, "ENRICHMENT"),
                context -> runEnrichment());
    }

    private OperationSummary runEnrichment() {
        console.system("Starting statistics enrichment...");
        Set<IndicatorKey> indicatorKeys = getUniqueIndicators();
        log.info("Enriching {} unique indicators", indicatorKeys.size());
        console.info("Found " + indicatorKeys.size() + " unique indicators to enrich");

        int indicatorsProcessed = 0;
        int statisticsEnriched = 0;
        List<String> errorMessages = new ArrayList<>();

        for (IndicatorKey key : indicatorKeys) {
            try {
                statisticsEnriched += enrichIndicator(key.source(), key.indicatorCode(), key.year());
                indicatorsProcessed++;
            } catch (Exception e) {
                String error = String.format("%s/%s: %s", key.source(), key.indicatorCode(), e.getMessage());
                log.warn("Failed to enrich indicator {}", error, e);
                errorMessages.add(error);
            }
        }

        log.info("Enrichment completed: {} indicators, {} statistics, {} errors",
                indicatorsProcessed, statisticsEnriched, errorMessages.size());
        console.success("Enrichment completed: " + indicatorsProcessed + " indicators, " + statisticsEnriched
                + " statistics enriched" + (errorMessages.isEmpty() ? "" : ", " + errorMessages.size() + " errors"));
        return OperationSummary.of("indicatorsProcessed", indicatorsProcessed)
                .and("statisticsEnriched", statisticsEnriched)
                .and("errors", errorMessages);
    }
}
