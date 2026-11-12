package io.clueterra.questions.clients.wikidata.hintgeneration;

import io.clueterra.questions.entity.CountryStatistic;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WikidataAmbiguityCalculator {

    public int compute(String indicatorCode,
                       String value,
                       Map<Long, List<CountryStatistic>> statisticsByCountry,
                       Long countryId) {
        String normalized = normalize(value);
        return (int) statisticsByCountry.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(countryId))
                .filter(entry -> contains(entry.getValue(), indicatorCode, normalized))
                .count();
    }

    public int computePair(String firstIndicator,
                           String firstValue,
                           String secondIndicator,
                           String secondValue,
                           Map<Long, List<CountryStatistic>> statisticsByCountry,
                           Long countryId) {
        String normalizedFirst = normalize(firstValue);
        String normalizedSecond = normalize(secondValue);
        return (int) statisticsByCountry.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(countryId))
                .filter(entry -> contains(entry.getValue(), firstIndicator, normalizedFirst)
                        && contains(entry.getValue(), secondIndicator, normalizedSecond))
                .count();
    }

    public int computeItems(String indicatorCode,
                            List<String> displayedItems,
                            Map<Long, List<CountryStatistic>> statisticsByCountry,
                            Long countryId) {
        Set<String> normalizedItems = normalizeItems(displayedItems);
        return (int) statisticsByCountry.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(countryId))
                .filter(entry -> containsItems(entry.getValue(), indicatorCode, normalizedItems))
                .count();
    }

    public int computePairWithItems(String firstIndicator,
                                    String firstValue,
                                    String secondIndicator,
                                    List<String> displayedItems,
                                    Map<Long, List<CountryStatistic>> statisticsByCountry,
                                    Long countryId) {
        String normalizedFirst = normalize(firstValue);
        Set<String> normalizedItems = normalizeItems(displayedItems);
        return (int) statisticsByCountry.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(countryId))
                .filter(entry -> contains(entry.getValue(), firstIndicator, normalizedFirst)
                        && containsItems(entry.getValue(), secondIndicator, normalizedItems))
                .count();
    }

    private boolean contains(List<CountryStatistic> statistics, String indicatorCode, String normalizedValue) {
        return statistics.stream().anyMatch(statistic -> indicatorCode.equals(statistic.getIndicatorCode())
                && normalizedValue.equals(normalize(statistic.getValueText())));
    }

    private boolean containsItems(List<CountryStatistic> statistics,
                                  String indicatorCode,
                                  Set<String> normalizedItems) {
        if (normalizedItems.isEmpty()) {
            return false;
        }
        return statistics.stream()
                .filter(statistic -> indicatorCode.equals(statistic.getIndicatorCode()))
                .map(CountryStatistic::getValueText)
                .map(WikidataHintValueSupport::preparePlugTypes)
                .map(this::normalizeItems)
                .anyMatch(items -> items.containsAll(normalizedItems));
    }

    private Set<String> normalizeItems(List<String> items) {
        return items.stream()
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}+.]+", " ")
                .trim();
    }
}
