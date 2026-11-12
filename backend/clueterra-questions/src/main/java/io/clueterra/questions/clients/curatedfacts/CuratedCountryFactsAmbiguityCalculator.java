package io.clueterra.questions.clients.curatedfacts;

import io.clueterra.questions.entity.CountryStatistic;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CuratedCountryFactsAmbiguityCalculator {

    public int compute(String indicatorCode,
                       String value,
                       Map<Long, List<CountryStatistic>> statisticsByCountry,
                       Long countryId) {
        return (int) statisticsByCountry.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(countryId))
                .filter(entry -> entry.getValue().stream().anyMatch(statistic ->
                        indicatorCode.equals(statistic.getIndicatorCode())
                                && matches(indicatorCode, statistic.getValueText(), value)))
                .count();
    }

    public int computeCombination(Map<String, String> facts,
                                  Map<Long, List<CountryStatistic>> statisticsByCountry,
                                  Long countryId) {
        return (int) statisticsByCountry.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(countryId))
                .filter(entry -> facts.entrySet().stream().allMatch(fact ->
                        contains(entry.getValue(), fact.getKey(), fact.getValue())))
                .count();
    }

    private boolean contains(List<CountryStatistic> statistics, String indicatorCode, String expectedValue) {
        return statistics.stream().anyMatch(statistic -> indicatorCode.equals(statistic.getIndicatorCode())
                && matches(indicatorCode, statistic.getValueText(), expectedValue));
    }

    private boolean matches(String indicatorCode, String actualValue, String expectedValue) {
        return CuratedCountryFactIndicator.byCode(indicatorCode)
                .filter(this::isListIndicator)
                .map(ignored -> containsAllItems(actualValue, expectedValue))
                .orElseGet(() -> normalize(expectedValue).equals(normalize(actualValue)));
    }

    private boolean isListIndicator(CuratedCountryFactIndicator indicator) {
        return switch (indicator) {
            case SIGNATURE_DISH, SIGNATURE_DRINK, TRADITIONAL_DANCE,
                 TRADITIONAL_INSTRUMENT, PROMINENT_SPORT -> true;
            default -> false;
        };
    }

    private boolean containsAllItems(String actualValue, String expectedValue) {
        List<String> actualItems = CuratedFactTextSupport.splitList(actualValue).stream()
                .map(this::normalize)
                .toList();
        return CuratedFactTextSupport.splitList(expectedValue).stream()
                .map(this::normalize)
                .allMatch(actualItems::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
