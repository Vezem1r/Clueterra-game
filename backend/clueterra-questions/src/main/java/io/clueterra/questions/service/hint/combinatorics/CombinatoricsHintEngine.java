package io.clueterra.questions.service.hint.combinatorics;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.CombinatoricsConfigRepository;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.HintLifecycleService;
import io.clueterra.questions.service.hint.SourceOwnedLifecycleResult;
import io.clueterra.questions.service.hint.IndicatorTemplateRenderer;
import io.clueterra.questions.service.hint.combinatorics.dto.*;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CombinatoricsHintEngine {

    private static final String ENGINE_NAME = "combinatorics-engine";

    private final StatisticRepository statisticRepository;
    private final CombinatoricsConfigRepository configRepository;
    private final HintLifecycleService hintLifecycleService;
    private final IndicatorTemplateRenderer templateRenderer;
    private final CombinatoricsHintDifficultyCalculator difficultyCalculator;
    private final CombinatoricsAmbiguityCalculator ambiguityCalculator;

    private final AtomicReference<CombinatoricsSnapshot> cache = new AtomicReference<>();

    public void invalidateCache() {
        cache.set(null);
    }

    public int generateForCountry(Country country) {
        if (country == null || country.getId() == null) {
            return 0;
        }

        CombinatoricsSnapshot snapshot = refreshSnapshotIfNeeded();
        Map<String, CountryStatistic> statsByKey = snapshot.statsByCountry().getOrDefault(country.getId(), Map.of());
        List<CandidateHint> candidates = new ArrayList<>();

        for (ComboHintDefinition definition : snapshot.definitions()) {
            Set<Long> matchingCountryIds = snapshot.matchingCountriesByIndicator()
                    .getOrDefault(definition.indicatorCode(), Set.of());
            if (!matchingCountryIds.contains(country.getId())) {
                continue;
            }

            buildCandidate(country, definition, statsByKey, snapshot.statsByCountry(), matchingCountryIds)
                    .ifPresent(candidates::add);
        }

        SourceOwnedLifecycleResult result = hintLifecycleService.applySourceOwnedLifecycle(
                country,
                candidates,
                Set.of(DataSource.COMBINATORICS)
        );
        log.debug("[COMBINATORICS] {} => {} candidates, {} new, {} updated",
                country.getIsoAlpha2(), candidates.size(), result.newCount(), result.updatedCount());
        return result.newCount() + result.updatedCount();
    }

    private CombinatoricsSnapshot refreshSnapshotIfNeeded() {
        CombinatoricsSnapshot existing = cache.get();
        if (existing != null) {
            return existing;
        }

        CombinatoricsSnapshot loaded = loadSnapshot();
        cache.compareAndSet(null, loaded);
        return cache.get();
    }

    private CombinatoricsSnapshot loadSnapshot() {
        List<ComboHintDefinition> definitions = configRepository.findActiveDefinitions();
        List<CountryStatistic> currentStatistics = statisticRepository.findAll().stream()
                .filter(stat -> Boolean.TRUE.equals(stat.getIsCurrent()))
                .filter(stat -> stat.getCountryId() != null)
                .filter(stat -> stat.getSource() != null)
                .filter(stat -> stat.getIndicatorCode() != null && !stat.getIndicatorCode().isBlank())
                .toList();

        Map<Long, Map<String, CountryStatistic>> statsByCountry = buildStatsIndex(currentStatistics);
        Map<String, Set<Long>> matchingCountriesByIndicator = buildMatchingCountries(definitions, statsByCountry);

        log.info("[COMBINATORICS] Snapshot loaded: {} definitions, {} countries with stats",
                definitions.size(), statsByCountry.size());

        return new CombinatoricsSnapshot(
                List.copyOf(definitions),
                Map.copyOf(statsByCountry),
                Map.copyOf(matchingCountriesByIndicator)
        );
    }

    private Map<Long, Map<String, CountryStatistic>> buildStatsIndex(List<CountryStatistic> statistics) {
        Map<Long, Map<String, CountryStatistic>> statsByCountry = new LinkedHashMap<>();

        for (CountryStatistic statistic : statistics) {
            Map<String, CountryStatistic> countryStats = statsByCountry.computeIfAbsent(
                    statistic.getCountryId(),
                    ignored -> new LinkedHashMap<>()
            );
            String key = statKey(statistic.getSource(), statistic.getIndicatorCode());
            countryStats.merge(key, statistic, this::preferMoreRecent);
        }

        Map<Long, Map<String, CountryStatistic>> immutable = new LinkedHashMap<>();
        statsByCountry.forEach((countryId, stats) -> immutable.put(countryId, Map.copyOf(stats)));
        return immutable;
    }

    private Map<String, Set<Long>> buildMatchingCountries(
            List<ComboHintDefinition> definitions,
            Map<Long, Map<String, CountryStatistic>> statsByCountry) {
        Map<String, Set<Long>> matching = new LinkedHashMap<>();

        for (ComboHintDefinition definition : definitions) {
            Set<Long> matchedCountryIds = statsByCountry.entrySet().stream()
                    .filter(entry -> matchesDefinition(definition, entry.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            if (definition.maxAmbiguityCount() != null
                    && matchedCountryIds.size() > definition.maxAmbiguityCount()) {
                matching.put(definition.indicatorCode(), Set.of());
                continue;
            }

            matching.put(definition.indicatorCode(), Set.copyOf(matchedCountryIds));
        }

        return matching;
    }

    private boolean matchesDefinition(ComboHintDefinition definition, Map<String, CountryStatistic> statsByKey) {
        for (ComboHintComponent component : definition.components()) {
            if (component.required() && !hasNumericValue(component, statsByKey)) {
                return false;
            }
        }

        for (ComboHintPredicate predicate : definition.predicates()) {
            BigDecimal left = componentValue(predicate.leftComponentKey(), definition.components(), statsByKey);
            BigDecimal right = rightValue(predicate, definition.components(), statsByKey);
            if (!predicate.operator().test(left, right)) {
                return false;
            }
        }

        return true;
    }

    private Optional<CandidateHint> buildCandidate(
            Country country,
            ComboHintDefinition definition,
            Map<String, CountryStatistic> statsByKey,
            Map<Long, Map<String, CountryStatistic>> statsByCountry,
            Set<Long> matchingCountryIds) {
        Map<String, String> placeholders = buildPlaceholders(definition.components(), statsByKey);
        if (placeholders.isEmpty()) {
            return Optional.empty();
        }

        ComboTextVariant variant = pickVariant(country, definition);
        String text = templateRenderer.renderTemplate(variant.template(), placeholders).trim();
        if (text.isBlank()) {
            return Optional.empty();
        }

        Integer sourceYear = latestSourceYear(definition.components(), statsByKey);
        int ambiguityCount = ambiguityCalculator.computeAmbiguity(
                definition,
                country.getId(),
                statsByCountry,
                matchingCountryIds
        );
        int difficulty = difficultyCalculator.computeDifficulty(
                definition.baseDifficulty(),
                ambiguityCount,
                definition.components().size(),
                definition.familyType()
        );
        BigDecimal quality = difficultyCalculator.computeQuality(
                definition.baseQuality(),
                ambiguityCount,
                definition.components().size()
        );

        return Optional.of(CandidateHint.builder()
                .text(text)
                .type(definition.hintType())
                .category(definition.category())
                .source(DataSource.COMBINATORICS)
                .sourceIndicator(definition.indicatorCode())
                .sourceYear(sourceYear)
                .estimatedDifficulty(difficulty)
                .estimatedQuality(quality)
                .ambiguityCount(ambiguityCount)
                .lensName(ENGINE_NAME)
                .build());
    }

    private Map<String, String> buildPlaceholders(
            List<ComboHintComponent> components,
            Map<String, CountryStatistic> statsByKey) {
        Map<String, String> placeholders = new HashMap<>();

        for (ComboHintComponent component : components) {
            CountryStatistic statistic = statsByKey.get(component.statKey());
            if (statistic == null) {
                if (component.required()) {
                    return Map.of();
                }
                continue;
            }

            if (statistic.getValue() != null) {
                placeholders.put(component.componentKey(), component.formatter().format(statistic.getValue()));
            } else if (statistic.getValueText() != null && !statistic.getValueText().isBlank()) {
                placeholders.put(component.componentKey(), statistic.getValueText().trim());
            } else if (component.required()) {
                return Map.of();
            }
        }

        return placeholders;
    }

    private ComboTextVariant pickVariant(Country country, ComboHintDefinition definition) {
        int variantIndex = Math.floorMod(
                Objects.hash(country.getId(), definition.indicatorCode()),
                definition.variants().size()
        );
        return definition.variants().stream()
                .sorted(Comparator.comparingInt(ComboTextVariant::variantNo))
                .toList()
                .get(variantIndex);
    }

    private Integer latestSourceYear(List<ComboHintComponent> components, Map<String, CountryStatistic> statsByKey) {
        return components.stream()
                .map(ComboHintComponent::statKey)
                .map(statsByKey::get)
                .filter(Objects::nonNull)
                .map(CountryStatistic::getYear)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private boolean hasNumericValue(ComboHintComponent component, Map<String, CountryStatistic> statsByKey) {
        CountryStatistic statistic = statsByKey.get(component.statKey());
        return statistic != null && statistic.getValue() != null;
    }

    private BigDecimal componentValue(
            String componentKey,
            List<ComboHintComponent> components,
            Map<String, CountryStatistic> statsByKey) {
        return components.stream()
                .filter(component -> component.componentKey().equals(componentKey))
                .findFirst()
                .map(ComboHintComponent::statKey)
                .map(statsByKey::get)
                .map(CountryStatistic::getValue)
                .orElse(null);
    }

    private BigDecimal rightValue(
            ComboHintPredicate predicate,
            List<ComboHintComponent> components,
            Map<String, CountryStatistic> statsByKey) {
        if (predicate.comparisonMode() == ComboPredicateMode.COMPONENT) {
            return componentValue(predicate.rightComponentKey(), components, statsByKey);
        }
        return predicate.rightValueNumeric();
    }

    private String statKey(DataSource source, String indicatorCode) {
        return source.name() + "|" + indicatorCode;
    }

    private CountryStatistic preferMoreRecent(CountryStatistic existing, CountryStatistic challenger) {
        LocalDate existingDate = existing.getEffectiveDate();
        LocalDate challengerDate = challenger.getEffectiveDate();
        if (existingDate != null || challengerDate != null) {
            if (existingDate == null) {
                return challenger;
            }
            if (challengerDate == null) {
                return existing;
            }
            if (challengerDate.isAfter(existingDate)) {
                return challenger;
            }
            if (existingDate.isAfter(challengerDate)) {
                return existing;
            }
        }

        Integer existingYear = existing.getYear();
        Integer challengerYear = challenger.getYear();
        if (existingYear != null || challengerYear != null) {
            if (existingYear == null) {
                return challenger;
            }
            if (challengerYear == null) {
                return existing;
            }
            if (challengerYear > existingYear) {
                return challenger;
            }
            if (existingYear > challengerYear) {
                return existing;
            }
        }

        Instant existingFetchedAt = existing.getFetchedAt();
        Instant challengerFetchedAt = challenger.getFetchedAt();
        if (existingFetchedAt != null || challengerFetchedAt != null) {
            if (existingFetchedAt == null) {
                return challenger;
            }
            if (challengerFetchedAt == null) {
                return existing;
            }
            if (challengerFetchedAt.isAfter(existingFetchedAt)) {
                return challenger;
            }
        }

        String existingSource = existing.getSource() != null ? existing.getSource().name() : "";
        String challengerSource = challenger.getSource() != null ? challenger.getSource().name() : "";
        return challengerSource.toLowerCase(Locale.ENGLISH).compareTo(existingSource.toLowerCase(Locale.ENGLISH)) > 0
                ? challenger
                : existing;
    }
}
