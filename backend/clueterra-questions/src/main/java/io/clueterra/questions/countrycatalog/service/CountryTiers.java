package io.clueterra.questions.countrycatalog.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import io.clueterra.questions.countrycatalog.model.ValidatedCountry;

@Component
public class CountryTiers {

    private static final int TIER_COUNT = 5;
    private static final double POPULATION_WEIGHT = 0.6;

    public Map<String, Integer> tiers(List<CountryCatalogEntry> entries, Map<String, ValidatedCountry> validated) {
        Map<String, Double> population = percentiles(entries, validated, ValidatedCountry::rankPopulation);
        Map<String, Double> area = percentiles(entries, validated, ValidatedCountry::rankArea);
        List<String> prominence = entries.stream().map(CountryCatalogEntry::iso)
                .sorted(Comparator.comparingDouble((String iso) ->
                        POPULATION_WEIGHT * population.get(iso) + (1 - POPULATION_WEIGHT) * area.get(iso)).reversed())
                .toList();
        Map<String, Integer> result = new LinkedHashMap<>();
        IntStream.range(0, prominence.size()).forEach(index ->
                result.put(prominence.get(index), Math.min(TIER_COUNT, index * TIER_COUNT / prominence.size() + 1)));
        return result;
    }

    private Map<String, Double> percentiles(List<CountryCatalogEntry> entries, Map<String, ValidatedCountry> validated,
            ToDoubleFunction<ValidatedCountry> value) {
        List<String> ordered = entries.stream().map(CountryCatalogEntry::iso)
                .sorted(Comparator.comparingDouble(iso -> value.applyAsDouble(validated.get(iso))))
                .toList();
        double last = Math.max(1, ordered.size() - 1);
        Map<String, Double> result = new LinkedHashMap<>();
        IntStream.range(0, ordered.size()).forEach(index -> result.put(ordered.get(index), index / last));
        return result;
    }
}
