package io.clueterra.questions.clients.wikidata.cinema.hintgeneration;

import io.clueterra.questions.clients.common.AbstractCachedLens;
import io.clueterra.questions.clients.common.TemplatedHintBuilder;
import io.clueterra.questions.clients.wikidata.cinema.WikidataCinemaFact;
import io.clueterra.questions.clients.wikidata.cinema.WikidataCinemaPersonCode;
import io.clueterra.questions.clients.wikidata.cinema.WikidataCinemaStatisticCode;
import io.clueterra.questions.clients.wikidata.cinema.WikidataCinemaWorkCode;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class WikidataCinemaLens extends AbstractCachedLens {

    private static final String INDICATOR_PREFIX = WikidataCinemaStatisticCode.PREFIX;
    private static final int MAX_ALLOWED_AMBIGUITY = 2;
    private static final int TEMPLATE_VARIANTS = 10;

    private final WikidataCinemaAmbiguityCalculator ambiguityCalculator;
    private final DynamicDifficultyCalculator difficultyCalculator;
    private final CountryDifficultyAdjuster countryDifficultyAdjuster;
    private final StatisticRepository statisticRepository;
    private final AtomicReference<Map<String, Set<Long>>> cachedCountriesByItem =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, List<WikidataCinemaWorkStatistic>>> cachedWorksByPerson =
            new AtomicReference<>(Map.of());

    @Override
    public String name() {
        return "wikidata-cinema";
    }

    @Override
    public LensType lensType() {
        return LensType.SPECIALIZED_DYNAMIC;
    }

    @Override
    public Set<String> claimedPrefixes() {
        return Set.of(INDICATOR_PREFIX);
    }

    @Override
    public Set<DataSource> claimedSources() {
        return Set.of(DataSource.WIKIDATA);
    }

    @Override
    public boolean canApply(StatisticContext context) {
        return context.statistic().getSource() == DataSource.WIKIDATA
                && WikidataCinemaStatisticCode.parsePerson(
                        context.statistic().getIndicatorCode()).isPresent();
    }

    @Override
    protected void loadCache() {
        Map<String, Set<Long>> countriesByItem = new HashMap<>();
        Map<String, List<WikidataCinemaWorkStatistic>> worksByPerson = new HashMap<>();
        statisticRepository.findCurrentBySource(DataSource.WIKIDATA).stream()
                .filter(statistic -> statistic.getIndicatorCode().startsWith(INDICATOR_PREFIX))
                .forEach(statistic -> addStatisticToCache(
                        statistic, countriesByItem, worksByPerson));
        Map<String, Set<Long>> immutableCountries = countriesByItem.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Set.copyOf(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, List<WikidataCinemaWorkStatistic>> immutableWorks = worksByPerson.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .sorted(Comparator
                                        .comparingInt(WikidataCinemaWorkStatistic::sitelinks)
                                        .reversed()
                                        .thenComparing(WikidataCinemaWorkStatistic::workItemId))
                                .toList(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        cachedCountriesByItem.set(Map.copyOf(immutableCountries));
        cachedWorksByPerson.set(Map.copyOf(immutableWorks));
        log.info("[WikidataCinema] Cache refreshed: {} exact people, {} person-role work groups",
                immutableCountries.size(), immutableWorks.size());
    }

    @Override
    protected List<CandidateHint> doGenerate(StatisticContext context) {
        if (!canApply(context)) {
            return List.of();
        }
        return WikidataCinemaStatisticCode.parsePerson(context.statistic().getIndicatorCode()).stream()
                .map(personCode -> buildFact(context, personCode))
                .flatMap(Optional::stream)
                .map(fact -> buildCandidate(context, fact))
                .flatMap(Optional::stream)
                .toList();
    }

    private void addStatisticToCache(CountryStatistic statistic,
                                     Map<String, Set<Long>> countriesByItem,
                                     Map<String, List<WikidataCinemaWorkStatistic>> worksByPerson) {
        WikidataCinemaStatisticCode.parsePerson(statistic.getIndicatorCode())
                .ifPresent(code -> countriesByItem
                        .computeIfAbsent(code.personItemId(), ignored -> new HashSet<>())
                        .add(statistic.getCountryId()));
        WikidataCinemaStatisticCode.parseWork(statistic.getIndicatorCode())
                .filter(ignored -> StringUtils.hasText(statistic.getValueText()))
                .ifPresent(code -> worksByPerson
                        .computeIfAbsent(cacheKey(statistic.getCountryId(), code), ignored -> new ArrayList<>())
                        .add(new WikidataCinemaWorkStatistic(
                                code.workItemId(),
                                statistic.getValueText(),
                                statistic.getValue() != null ? statistic.getValue().intValue() : 0)));
    }

    private Optional<WikidataCinemaFact> buildFact(StatisticContext context,
                                                    WikidataCinemaPersonCode personCode) {
        CountryStatistic statistic = context.statistic();
        if (!StringUtils.hasText(statistic.getValueText())) {
            return Optional.empty();
        }
        List<WikidataCinemaWorkStatistic> works = cachedWorksByPerson.get().getOrDefault(
                cacheKey(statistic.getCountryId(), personCode), List.of());
        if (works.size() < 2) {
            return Optional.empty();
        }
        WikidataCinemaWorkStatistic first = works.get(0);
        WikidataCinemaWorkStatistic second = works.get(1);
        int recognition = statistic.getValue() != null ? statistic.getValue().intValue() : 50;
        double confidence = statistic.getConfidence() != null
                ? statistic.getConfidence().doubleValue()
                : 0.95;
        return Optional.of(new WikidataCinemaFact(
                personCode.factType().name() + "_" + personCode.personItemId(),
                context.countryCode(),
                personCode.factType(),
                personCode.personItemId(),
                statistic.getValueText(),
                first.workItemId(),
                first.title(),
                first.sitelinks(),
                second.workItemId(),
                second.title(),
                second.sitelinks(),
                0,
                recognition,
                confidence));
    }

    private Optional<CandidateHint> buildCandidate(StatisticContext context, WikidataCinemaFact fact) {
        int ambiguity = ambiguityCalculator.compute(
                fact, cachedCountriesByItem.get(), context.statistic().getCountryId());
        if (ambiguity > MAX_ALLOWED_AMBIGUITY) {
            return Optional.empty();
        }

        String sourceIndicator = INDICATOR_PREFIX + fact.id();
        int difficulty = difficultyCalculator.computeDifficulty(
                sourceIndicator, ambiguity, baseDifficulty(fact.recognitionScore()));
        BigDecimal quality = difficultyCalculator.computeQuality(
                sourceIndicator,
                ambiguity,
                baseQuality(fact.recognitionScore()),
                fact.confidence());

        return TemplatedHintBuilder.buildTemplatedCandidate(
                templateCode(fact),
                sourceIndicator,
                placeholders(fact),
                HintType.CULTURAL,
                Category.CULTURE,
                DataSource.WIKIDATA,
                context.statistic().getYear(),
                countryDifficultyAdjuster.adjust(context.countryCode(), difficulty),
                quality,
                ambiguity,
                name(),
                null);
    }

    private int baseDifficulty(int recognitionScore) {
        return Math.clamp(88 - (int) Math.round(recognitionScore * 0.68), 16, 72);
    }

    private BigDecimal baseQuality(int recognitionScore) {
        double quality = 0.72 + recognitionScore * 0.0018;
        return BigDecimal.valueOf(Math.min(0.90, quality)).setScale(2, RoundingMode.HALF_UP);
    }

    private String templateCode(WikidataCinemaFact fact) {
        String family = switch (fact.factType()) {
            case ACTOR_WORKS -> "ACTOR_WORKS";
            case DIRECTOR_WORKS -> "DIRECTOR_WORKS";
        };
        int variant = Math.floorMod(
                (fact.countryCode() + "|" + fact.id()).hashCode(), TEMPLATE_VARIANTS) + 1;
        return INDICATOR_PREFIX + family + "_" + variant;
    }

    private Map<String, String> placeholders(WikidataCinemaFact fact) {
        return Map.of(
                "subject", fact.subject(),
                "work_one", fact.workOne(),
                "work_two", fact.workTwo());
    }

    private String cacheKey(Long countryId, WikidataCinemaPersonCode code) {
        return countryId + "|" + code.factType().name() + "|" + code.personItemId();
    }

    private String cacheKey(Long countryId, WikidataCinemaWorkCode code) {
        return countryId + "|" + code.factType().name() + "|" + code.personItemId();
    }
}
