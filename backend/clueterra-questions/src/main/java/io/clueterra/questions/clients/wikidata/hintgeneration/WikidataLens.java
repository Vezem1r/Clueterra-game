package io.clueterra.questions.clients.wikidata.hintgeneration;

import io.clueterra.questions.clients.common.AbstractCachedLens;
import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.clients.wikidata.dto.WikidataMetric;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.CountryDifficultyAdjuster;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Slf4j
@Component
public class WikidataLens extends AbstractCachedLens {

    private static final String CONTACT_PAIR = "WIKIDATA_CONTACT_PAIR";
    private static final String EVERYDAY_INFRASTRUCTURE = "WIKIDATA_EVERYDAY_INFRASTRUCTURE";
    private static final int MAX_ALLOWED_AMBIGUITY = 10;
    private static final int MAX_ANTHEM_AMBIGUITY = 4;

    private final StatisticRepository statisticRepository;
    private final DynamicDifficultyCalculator difficultyCalculator;
    private final WikidataAmbiguityCalculator ambiguityCalculator;
    private final CountryDifficultyAdjuster countryDifficultyAdjuster;
    private final AtomicReference<Map<Long, List<CountryStatistic>>> cachedByCountry =
            new AtomicReference<>(Map.of());

    public WikidataLens(StatisticRepository statisticRepository,
                        DynamicDifficultyCalculator difficultyCalculator,
                        WikidataAmbiguityCalculator ambiguityCalculator,
                        CountryDifficultyAdjuster countryDifficultyAdjuster) {
        this.statisticRepository = statisticRepository;
        this.difficultyCalculator = difficultyCalculator;
        this.ambiguityCalculator = ambiguityCalculator;
        this.countryDifficultyAdjuster = countryDifficultyAdjuster;
    }

    @Override
    public String name() {
        return "wikidata";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("WIKIDATA_");
    }

    @Override
    public Set<DataSource> claimedSources() {
        return Set.of(DataSource.WIKIDATA);
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return context.statistic().getSource() == DataSource.WIKIDATA
                && metricFor(context.statistic().getIndicatorCode()).isPresent();
    }

    @Override
    protected void loadCache() {
        Map<Long, List<CountryStatistic>> byCountry = new HashMap<>();
        statisticRepository.findBySource(DataSource.WIKIDATA).forEach(statistic -> byCountry
                .computeIfAbsent(statistic.getCountryId(), ignored -> new ArrayList<>())
                .add(statistic));
        cachedByCountry.set(Map.copyOf(byCountry));
        log.info("[Wikidata] Hint cache refreshed: {} countries", byCountry.size());
    }

    @Override
    protected List<CandidateHint> doGenerate(StatisticContext context) {
        String value = context.statistic().getValueText();
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return metricFor(context.statistic().getIndicatorCode())
                .map(metric -> generate(context, metric, value))
                .orElseGet(List::of);
    }

    private List<CandidateHint> generate(StatisticContext context, WikidataMetric metric, String rawValue) {
        return switch (metric) {
            case NATIONAL_ANTHEM -> optionalList(buildAnthem(context, rawValue));
            case CALLING_CODE -> optionalList(buildContactOrCallingCode(context, rawValue));
            case TOP_LEVEL_DOMAIN -> optionalList(buildDomainFallback(context, rawValue));
            case DRIVING_SIDE -> optionalList(buildEverydayInfrastructure(context, rawValue));
            case ELECTRICAL_PLUG -> optionalList(buildPlugFallback(context, rawValue));
        };
    }

    private Optional<CandidateHint> buildAnthem(StatisticContext context, String rawValue) {
        return WikidataHintValueSupport.prepareAnthem(context.country(), rawValue)
                .flatMap(anthem -> {
                    int ambiguity = ambiguityCalculator.compute(
                            WikidataMetric.NATIONAL_ANTHEM.getCode(), anthem,
                            cachedByCountry.get(), context.statistic().getCountryId());
                    if (ambiguity > MAX_ANTHEM_AMBIGUITY) {
                        return Optional.empty();
                    }
                    return buildCandidate(
                            context,
                            WikidataMetric.NATIONAL_ANTHEM.getCode() + "_"
                                    + pickVariant(context, WikidataMetric.NATIONAL_ANTHEM.getCode()),
                            WikidataMetric.NATIONAL_ANTHEM.getCode(),
                            Map.of("anthem", anthem),
                            HintType.CULTURAL,
                            Category.CULTURE,
                            ambiguity,
                            32,
                            BigDecimal.valueOf(0.90));
                });
    }

    private Optional<CandidateHint> buildContactOrCallingCode(StatisticContext context, String rawValue) {
        Optional<String> callingCode = WikidataHintValueSupport.prepareSimpleValue(rawValue);
        if (callingCode.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> domain = findText(context.statistic().getCountryId(), WikidataMetric.TOP_LEVEL_DOMAIN)
                .flatMap(value -> WikidataHintValueSupport.prepareDomain(context.country(), value));
        if (domain.isEmpty()) {
            return buildExactSingle(
                    context,
                    WikidataMetric.CALLING_CODE,
                    callingCode.get(),
                    "calling_code",
                    27,
                    BigDecimal.valueOf(0.90));
        }

        int ambiguity = ambiguityCalculator.computePair(
                WikidataMetric.CALLING_CODE.getCode(), callingCode.get(),
                WikidataMetric.TOP_LEVEL_DOMAIN.getCode(), domain.get(),
                cachedByCountry.get(), context.statistic().getCountryId());
        return buildCandidate(
                context,
                CONTACT_PAIR + "_" + pickVariant(context, "contact-pair"),
                CONTACT_PAIR,
                Map.of("calling_code", callingCode.get(), "domain", domain.get()),
                HintType.ABSOLUTE_FACT,
                Category.INFRASTRUCTURE,
                ambiguity,
                21,
                BigDecimal.valueOf(0.90));
    }

    private Optional<CandidateHint> buildDomainFallback(StatisticContext context, String rawValue) {
        if (findText(context.statistic().getCountryId(), WikidataMetric.CALLING_CODE)
                .flatMap(WikidataHintValueSupport::prepareSimpleValue)
                .isPresent()) {
            return Optional.empty();
        }
        return WikidataHintValueSupport.prepareDomain(context.country(), rawValue)
                .flatMap(domain -> buildExactSingle(
                        context,
                        WikidataMetric.TOP_LEVEL_DOMAIN,
                        domain,
                        "domain",
                        24,
                        BigDecimal.valueOf(0.90)));
    }

    private Optional<CandidateHint> buildEverydayInfrastructure(StatisticContext context, String rawValue) {
        Optional<String> drivingSide = WikidataHintValueSupport.prepareSimpleValue(rawValue);
        List<String> plugTypes = findText(context.statistic().getCountryId(), WikidataMetric.ELECTRICAL_PLUG)
                .map(WikidataHintValueSupport::preparePlugTypes)
                .orElseGet(List::of);
        if (drivingSide.isEmpty() || plugTypes.isEmpty()) {
            return Optional.empty();
        }

        int ambiguity = ambiguityCalculator.computePairWithItems(
                WikidataMetric.DRIVING_SIDE.getCode(), drivingSide.get(),
                WikidataMetric.ELECTRICAL_PLUG.getCode(), plugTypes,
                cachedByCountry.get(), context.statistic().getCountryId());
        return buildCandidate(
                context,
                EVERYDAY_INFRASTRUCTURE + "_" + pickVariant(context, "infrastructure-pair"),
                EVERYDAY_INFRASTRUCTURE,
                Map.of(
                        "driving_side", drivingSide.get(),
                        "plug", WikidataHintValueSupport.formatPlugTypes(plugTypes)),
                HintType.ABSOLUTE_FACT,
                Category.INFRASTRUCTURE,
                ambiguity,
                31,
                BigDecimal.valueOf(0.90));
    }

    private Optional<CandidateHint> buildPlugFallback(StatisticContext context, String rawValue) {
        if (findText(context.statistic().getCountryId(), WikidataMetric.DRIVING_SIDE)
                .flatMap(WikidataHintValueSupport::prepareSimpleValue)
                .isPresent()) {
            return Optional.empty();
        }

        List<String> plugTypes = WikidataHintValueSupport.preparePlugTypes(rawValue);
        if (plugTypes.isEmpty()) {
            return Optional.empty();
        }
        int ambiguity = ambiguityCalculator.computeItems(
                WikidataMetric.ELECTRICAL_PLUG.getCode(), plugTypes,
                cachedByCountry.get(), context.statistic().getCountryId());
        return buildCandidate(
                context,
                WikidataMetric.ELECTRICAL_PLUG.getCode() + "_"
                        + pickVariant(context, WikidataMetric.ELECTRICAL_PLUG.getCode()),
                WikidataMetric.ELECTRICAL_PLUG.getCode(),
                Map.of("plug", WikidataHintValueSupport.formatPlugTypes(plugTypes)),
                HintType.ABSOLUTE_FACT,
                Category.INFRASTRUCTURE,
                ambiguity,
                35,
                BigDecimal.valueOf(0.88));
    }

    private Optional<CandidateHint> buildExactSingle(StatisticContext context,
                                                     WikidataMetric metric,
                                                     String value,
                                                     String placeholder,
                                                     int baseDifficulty,
                                                     BigDecimal baseQuality) {
        int ambiguity = ambiguityCalculator.compute(
                metric.getCode(), value, cachedByCountry.get(), context.statistic().getCountryId());
        return buildCandidate(
                context,
                metric.getCode() + "_" + pickVariant(context, metric.getCode()),
                metric.getCode(),
                Map.of(placeholder, value),
                HintType.ABSOLUTE_FACT,
                Category.INFRASTRUCTURE,
                ambiguity,
                baseDifficulty,
                baseQuality);
    }

    private Optional<CandidateHint> buildCandidate(StatisticContext context,
                                                   String templateCode,
                                                   String sourceIndicator,
                                                   Map<String, ?> placeholders,
                                                   HintType hintType,
                                                   Category category,
                                                   int ambiguity,
                                                   int baseDifficulty,
                                                   BigDecimal baseQuality) {
        if (ambiguity > MAX_ALLOWED_AMBIGUITY) {
            return Optional.empty();
        }
        int clueDifficulty = difficultyCalculator.computeDifficulty(
                sourceIndicator, ambiguity, baseDifficulty);
        return TemplatedHintBuilder.buildTemplatedCandidate(
                templateCode,
                sourceIndicator,
                placeholders,
                hintType,
                category,
                DataSource.WIKIDATA,
                context.statistic().getYear(),
                countryDifficultyAdjuster.adjust(context.countryCode(), clueDifficulty),
                difficultyCalculator.computeQuality(sourceIndicator, ambiguity, baseQuality, context.confidence()),
                ambiguity,
                name(),
                null);
    }

    private Optional<String> findText(Long countryId, WikidataMetric metric) {
        return cachedByCountry.get().getOrDefault(countryId, List.of()).stream()
                .filter(statistic -> metric.getCode().equals(statistic.getIndicatorCode()))
                .map(CountryStatistic::getValueText)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private Optional<WikidataMetric> metricFor(String indicatorCode) {
        return Stream.of(WikidataMetric.values())
                .filter(metric -> metric.getCode().equals(indicatorCode))
                .findFirst();
    }

    private List<CandidateHint> optionalList(Optional<CandidateHint> candidate) {
        return candidate.map(List::of).orElseGet(List::of);
    }

    private int pickVariant(StatisticContext context, String salt) {
        String seed = (context.countryCode() != null ? context.countryCode() : "XX") + "|" + salt;
        return Math.floorMod(seed.hashCode(), 3) + 1;
    }
}
