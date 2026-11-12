package io.clueterra.questions.clients.curatedfacts;

import io.clueterra.questions.clients.common.AbstractCachedLens;
import io.clueterra.questions.clients.common.TemplatedHintBuilder;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class CuratedCountryFactsLens extends AbstractCachedLens {

    private static final String CULTURE_TRAIL = "CURATED_CULTURE_TRAIL";
    private static final String SYMBOL_TRAIL = "CURATED_SYMBOL_TRAIL";
    private static final String IDENTITY_PAIR = "CURATED_IDENTITY_PAIR";
    private static final List<CuratedCountryFactIndicator> CULTURE_INDICATORS = List.of(
            CuratedCountryFactIndicator.SIGNATURE_DISH,
            CuratedCountryFactIndicator.SIGNATURE_DRINK,
            CuratedCountryFactIndicator.TRADITIONAL_DANCE,
            CuratedCountryFactIndicator.TRADITIONAL_INSTRUMENT,
            CuratedCountryFactIndicator.PROMINENT_SPORT);
    private static final List<CuratedCountryFactIndicator> SYMBOL_INDICATORS = List.of(
            CuratedCountryFactIndicator.EMBLEMATIC_ANIMAL,
            CuratedCountryFactIndicator.EMBLEMATIC_BIRD,
            CuratedCountryFactIndicator.EMBLEMATIC_FLOWER,
            CuratedCountryFactIndicator.EMBLEMATIC_TREE);
    private static final List<CuratedCountryFactIndicator> IDENTITY_INDICATORS = List.of(
            CuratedCountryFactIndicator.STATEHOOD_DATE,
            CuratedCountryFactIndicator.ASSOCIATED_MOTTO);

    private final StatisticRepository statisticRepository;
    private final DynamicDifficultyCalculator difficultyCalculator;
    private final CuratedCountryFactsAmbiguityCalculator ambiguityCalculator;
    private final CountryDifficultyAdjuster countryDifficultyAdjuster;
    private final AtomicReference<Map<Long, List<CountryStatistic>>> cachedByCountry =
            new AtomicReference<>(Map.of());

    public CuratedCountryFactsLens(StatisticRepository statisticRepository,
                                   DynamicDifficultyCalculator difficultyCalculator,
                                   CuratedCountryFactsAmbiguityCalculator ambiguityCalculator,
                                   CountryDifficultyAdjuster countryDifficultyAdjuster) {
        this.statisticRepository = statisticRepository;
        this.difficultyCalculator = difficultyCalculator;
        this.ambiguityCalculator = ambiguityCalculator;
        this.countryDifficultyAdjuster = countryDifficultyAdjuster;
    }

    @Override
    public String name() {
        return "curated-country-facts";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_STATIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of("CURATED_");
    }

    @Override
    public Set<DataSource> claimedSources() {
        return Set.of(DataSource.CURATED_COUNTRY_FACTS);
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return context.statistic().getSource() == DataSource.CURATED_COUNTRY_FACTS
                && CuratedCountryFactIndicator.byCode(context.statistic().getIndicatorCode()).isPresent();
    }

    @Override
    protected void loadCache() {
        Map<Long, List<CountryStatistic>> byCountry = new HashMap<>();
        statisticRepository.findBySource(DataSource.CURATED_COUNTRY_FACTS)
                .forEach(statistic -> byCountry
                        .computeIfAbsent(statistic.getCountryId(), ignored -> new ArrayList<>())
                        .add(statistic));
        cachedByCountry.set(Map.copyOf(byCountry));
        log.info("[CuratedCountryFacts] Cache refreshed: {} countries", byCountry.size());
    }

    @Override
    protected List<CandidateHint> doGenerate(StatisticContext context) {
        String value = context.statistic().getValueText();
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return CuratedCountryFactIndicator.byCode(context.statistic().getIndicatorCode())
                .map(indicator -> generate(context, indicator, value))
                .orElseGet(List::of);
    }

    private List<CandidateHint> generate(StatisticContext context,
                                         CuratedCountryFactIndicator indicator,
                                         String rawValue) {
        Optional<String> preparedValue = CuratedFactTextSupport.prepareValue(
                context.country(), indicator, rawValue);
        if (preparedValue.isEmpty()) {
            return List.of();
        }

        String value = preparedValue.get();
        return switch (indicator) {
            case SIGNATURE_DISH, SIGNATURE_DRINK, TRADITIONAL_DANCE,
                 TRADITIONAL_INSTRUMENT, PROMINENT_SPORT -> buildCultureTrail(context, indicator, value);
            case EMBLEMATIC_ANIMAL, EMBLEMATIC_BIRD, EMBLEMATIC_FLOWER,
                 EMBLEMATIC_TREE -> buildSymbolTrail(context, indicator, value);
            case STATEHOOD_DATE, ASSOCIATED_MOTTO -> buildIdentityHint(context, indicator, value);
        };
    }

    private List<CandidateHint> buildCultureTrail(StatisticContext context,
                                                  CuratedCountryFactIndicator indicator,
                                                  String value) {
        Map<CuratedCountryFactIndicator, String> facts = collectPreparedFacts(
                context, CULTURE_INDICATORS, 1, 3);
        if (facts.size() < 2) {
            return optionalList(buildSingle(context, indicator, value));
        }
        if (facts.keySet().iterator().next() != indicator) {
            return List.of();
        }

        List<String> clues = facts.entrySet().stream()
                .map(entry -> cultureClue(entry.getKey(), entry.getValue()))
                .toList();
        Category category = facts.keySet().stream().allMatch(this::isFoodIndicator)
                ? Category.FOOD
                : Category.CULTURE;
        return optionalList(buildGroupedCandidate(
                context,
                CULTURE_TRAIL,
                "culture-trail",
                Map.of("clues", CuratedFactTextSupport.joinNaturalLanguage(clues)),
                facts,
                category,
                HintType.CULTURAL,
                20,
                BigDecimal.valueOf(0.92)));
    }

    private String cultureClue(CuratedCountryFactIndicator indicator, String value) {
        String formatted = CuratedFactTextSupport.formatValue(indicator, value);
        return switch (indicator) {
            case SIGNATURE_DISH -> "try " + formatted;
            case SIGNATURE_DRINK -> "sip " + formatted;
            case TRADITIONAL_DANCE -> "watch dancers perform " + formatted;
            case TRADITIONAL_INSTRUMENT -> "listen for musicians playing "
                    + CuratedFactTextSupport.formatWithDefiniteArticles(
                    CuratedFactTextSupport.splitList(value));
            case PROMINENT_SPORT -> "watch " + formatted;
            default -> formatted;
        };
    }

    private boolean isFoodIndicator(CuratedCountryFactIndicator indicator) {
        return indicator == CuratedCountryFactIndicator.SIGNATURE_DISH
                || indicator == CuratedCountryFactIndicator.SIGNATURE_DRINK;
    }

    private List<CandidateHint> buildSymbolTrail(StatisticContext context,
                                                 CuratedCountryFactIndicator indicator,
                                                 String value) {
        Map<CuratedCountryFactIndicator, String> facts = collectDistinctSymbols(context);
        if (facts.isEmpty() || facts.keySet().iterator().next() != indicator) {
            return List.of();
        }
        if (facts.size() < 2) {
            return optionalList(buildSingle(context, indicator, value));
        }

        Category category = facts.keySet().stream().allMatch(this::isPlantSymbol)
                ? Category.NATURE
                : Category.CULTURE;
        return optionalList(buildGroupedCandidate(
                context,
                SYMBOL_TRAIL,
                "symbol-trail",
                Map.of("symbols", CuratedFactTextSupport.formatWithDefiniteArticles(
                        new ArrayList<>(facts.values()))),
                facts,
                category,
                HintType.CULTURAL,
                25,
                BigDecimal.valueOf(0.90)));
    }

    private boolean isPlantSymbol(CuratedCountryFactIndicator indicator) {
        return indicator == CuratedCountryFactIndicator.EMBLEMATIC_FLOWER
                || indicator == CuratedCountryFactIndicator.EMBLEMATIC_TREE;
    }

    private Map<CuratedCountryFactIndicator, String> collectDistinctSymbols(StatisticContext context) {
        Map<CuratedCountryFactIndicator, String> symbols = new LinkedHashMap<>();
        SYMBOL_INDICATORS.forEach(indicator -> findPreparedText(context, indicator)
                .filter(value -> symbols.values().stream().noneMatch(existing ->
                        CuratedFactTextSupport.materiallyEquivalent(existing, value, context.country())))
                .ifPresent(value -> symbols.put(indicator, value)));
        return limitFacts(symbols, 3);
    }

    private List<CandidateHint> buildIdentityHint(StatisticContext context,
                                                  CuratedCountryFactIndicator indicator,
                                                  String value) {
        Map<CuratedCountryFactIndicator, String> facts = collectPreparedFacts(
                context, IDENTITY_INDICATORS, Integer.MAX_VALUE, 2);
        if (facts.size() < 2) {
            return optionalList(buildSingle(context, indicator, value));
        }
        if (facts.keySet().iterator().next() != indicator) {
            return List.of();
        }

        return optionalList(buildGroupedCandidate(
                context,
                IDENTITY_PAIR,
                "identity-pair",
                Map.of(
                        "date", facts.get(CuratedCountryFactIndicator.STATEHOOD_DATE),
                        "motto", facts.get(CuratedCountryFactIndicator.ASSOCIATED_MOTTO)),
                facts,
                Category.HISTORY,
                HintType.HISTORICAL,
                26,
                BigDecimal.valueOf(0.90)));
    }

    private Map<CuratedCountryFactIndicator, String> collectPreparedFacts(
            StatisticContext context,
            List<CuratedCountryFactIndicator> indicators,
            int maxItems,
            int maxFacts) {
        Map<CuratedCountryFactIndicator, String> facts = new LinkedHashMap<>();
        indicators.forEach(indicator -> findPreparedText(context, indicator)
                .map(value -> CuratedFactTextSupport.limitItems(indicator, value, maxItems))
                .ifPresent(value -> facts.put(indicator, value)));
        return limitFacts(facts, maxFacts);
    }

    private Map<CuratedCountryFactIndicator, String> limitFacts(
            Map<CuratedCountryFactIndicator, String> facts,
            int maxFacts) {
        Map<CuratedCountryFactIndicator, String> limited = new LinkedHashMap<>();
        facts.entrySet().stream()
                .limit(maxFacts)
                .forEach(entry -> limited.put(entry.getKey(), entry.getValue()));
        return limited;
    }

    private Optional<CandidateHint> buildSingle(StatisticContext context,
                                                CuratedCountryFactIndicator indicator,
                                                String value) {
        int ambiguity = ambiguityCalculator.compute(
                indicator.getCode(), value, cachedByCountry.get(), context.statistic().getCountryId());
        String templateCode = indicator.getCode() + "_" + pickVariant(context, indicator.getCode());
        return buildCandidate(
                context,
                templateCode,
                indicator.getCode(),
                Map.of(indicator.getPlaceholder(), formatSingleValue(indicator, value)),
                indicator.getCategory(),
                indicator.getHintType(),
                ambiguity,
                indicator.getBaseDifficulty(),
                indicator.getBaseQuality());
    }

    private String formatSingleValue(CuratedCountryFactIndicator indicator, String value) {
        if (indicator == CuratedCountryFactIndicator.TRADITIONAL_INSTRUMENT) {
            return CuratedFactTextSupport.formatWithDefiniteArticles(
                    CuratedFactTextSupport.splitList(value));
        }
        return CuratedFactTextSupport.formatValue(indicator, value);
    }

    private Optional<CandidateHint> buildGroupedCandidate(
            StatisticContext context,
            String templatePrefix,
            String salt,
            Map<String, ?> placeholders,
            Map<CuratedCountryFactIndicator, String> facts,
            Category category,
            HintType hintType,
            int baseDifficulty,
            BigDecimal baseQuality) {
        Map<String, String> valuesByIndicator = new LinkedHashMap<>();
        facts.forEach((indicator, value) -> valuesByIndicator.put(indicator.getCode(), value));
        int ambiguity = ambiguityCalculator.computeCombination(
                valuesByIndicator, cachedByCountry.get(), context.statistic().getCountryId());
        String templateCode = templatePrefix + "_" + pickVariant(context, salt);
        return buildCandidate(
                context,
                templateCode,
                templateCode,
                placeholders,
                category,
                hintType,
                ambiguity,
                baseDifficulty,
                baseQuality);
    }

    private Optional<String> findPreparedText(StatisticContext context,
                                              CuratedCountryFactIndicator indicator) {
        return findText(context.statistic().getCountryId(), indicator)
                .flatMap(value -> CuratedFactTextSupport.prepareValue(context.country(), indicator, value));
    }

    private List<CandidateHint> optionalList(Optional<CandidateHint> candidate) {
        return candidate.map(List::of).orElseGet(List::of);
    }

    private Optional<String> findText(Long countryId, CuratedCountryFactIndicator indicator) {
        return cachedByCountry.get().getOrDefault(countryId, List.of()).stream()
                .filter(statistic -> indicator.getCode().equals(statistic.getIndicatorCode()))
                .map(CountryStatistic::getValueText)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private Optional<CandidateHint> buildCandidate(StatisticContext context,
                                                   String templateCode,
                                                   String sourceIndicator,
                                                   Map<String, ?> placeholders,
                                                   Category category,
                                                   HintType hintType,
                                                   int ambiguity,
                                                   int baseDifficulty,
                                                   BigDecimal baseQuality) {
        return TemplatedHintBuilder.buildTemplatedCandidate(
                templateCode,
                sourceIndicator,
                placeholders,
                hintType,
                category,
                DataSource.CURATED_COUNTRY_FACTS,
                context.statistic().getYear(),
                countryDifficultyAdjuster.adjust(
                        context.countryCode(),
                        difficultyCalculator.computeDifficulty(sourceIndicator, ambiguity, baseDifficulty)),
                difficultyCalculator.computeQuality(sourceIndicator, ambiguity, baseQuality, context.confidence()),
                ambiguity,
                name(),
                null);
    }

    private int pickVariant(StatisticContext context, String salt) {
        String seed = (context.countryCode() != null ? context.countryCode() : "XX") + "|" + salt;
        return Math.floorMod(seed.hashCode(), 3) + 1;
    }
}
